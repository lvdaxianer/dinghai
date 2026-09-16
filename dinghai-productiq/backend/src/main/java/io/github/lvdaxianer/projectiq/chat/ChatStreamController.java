package io.github.lvdaxianer.projectiq.chat;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 面向浏览器插件的聊天 SSE API，只接受 JSON 请求体中的 question 字段。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@RestController
@RequestMapping("/api/chat")
public final class ChatStreamController {
    private final ChatStreamService streamService;

    /**
     * 创建聊天 SSE 控制器。
     *
     * @param streamService 受限 SSE 编排服务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public ChatStreamController(ChatStreamService streamService) {
        this.streamService = Objects.requireNonNull(streamService, "streamService");
    }

    /**
     * 以稳定事件名启动一个聊天流，客户端断开时取消下游调用。
     *
     * @param request JSON 请求体
     * @return 成功时为 SSE emitter，校验失败时为 JSON 错误码
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object stream(@RequestBody(required = false) ChatStreamRequest request) {
        try {
            return openStream(new ChatQuestion(request == null ? null : request.question()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":\"invalid_question\"}");
        }
    }

    /**
     * 创建 emitter 并将容器终态回调连接至下游 Future 取消。
     *
     * @param question 已校验问题
     * @return 已启动的 SSE emitter
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private SseEmitter openStream(ChatQuestion question) {
        SseEmitter emitter = new SseEmitter();
        AtomicReference<ChatStreamHandle> handle = new AtomicReference<>();
        ChatStreamHandle cancellation = () -> cancel(handle);
        SseEmitterSink sink = new SseEmitterSink(emitter, handle);
        emitter.onCompletion(cancellation::cancel);
        emitter.onTimeout(cancellation::cancel);
        emitter.onError(throwable -> cancellation.cancel());
        handle.set(streamService.start(question, sink));
        return emitter;
    }

    /**
     * 取消已建立的会话；容器过早回调时允许句柄尚未就绪。
     *
     * @param handle 会话句柄引用
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void cancel(AtomicReference<ChatStreamHandle> handle) {
        ChatStreamHandle current = handle.get();
        // 容器在句柄设置前结束时没有需要取消的下游调用。
        if (current != null) {
            current.cancel();
        }
    }

    /** 仅包含客户端可控问题的请求 JSON。 */
    public record ChatStreamRequest(String question) {
    }

    /** Spring SSE emitter 到稳定传输端口的适配器。 */
    private static final class SseEmitterSink implements ChatStreamSink {
        private final SseEmitter emitter;
        private final AtomicReference<ChatStreamHandle> handle;

        /** 创建发送事件的 emitter 适配器。 */
        private SseEmitterSink(SseEmitter emitter, AtomicReference<ChatStreamHandle> handle) {
            this.emitter = emitter;
            this.handle = handle;
        }

        /** 将事件数据以 JSON 形式发送给客户端。 */
        @Override
        public void emit(ChatStreamEvent event) {
            try {
                emitter.send(SseEmitter.event().name(event.name()).data(event.data(), MediaType.APPLICATION_JSON));
            } catch (IOException exception) {
                ChatStreamHandle current = handle.get();
                if (current != null) {
                    current.cancel();
                }
                throw new ChatClientDisconnectedException(exception);
            }
        }

        /** 正常关闭 SSE emitter。 */
        @Override
        public void complete() {
            emitter.complete();
        }
    }

    /** 客户端断开产生的内部控制流异常，不暴露为 SSE 失败事件。 */
    private static final class ChatClientDisconnectedException extends RuntimeException {
        /** 创建保留底层 I/O 原因的断开异常。 */
        private ChatClientDisconnectedException(IOException cause) {
            super(cause);
        }
    }
}
