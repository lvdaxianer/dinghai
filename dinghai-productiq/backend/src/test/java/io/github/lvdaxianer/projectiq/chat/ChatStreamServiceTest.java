package io.github.lvdaxianer.projectiq.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 验证 SSE 聊天编排的稳定事件、拒答和请求上下文传播。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class ChatStreamServiceTest {
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(1);

    /** 验证回答结果按既定顺序转换为 SSE 事件。 */
    @Test
    void emitsDeltaSourcesAndDoneInOrder() throws Exception {
        CapturingSink sink = new CapturingSink();
        ChatStreamService service = new ChatStreamService(question -> answered(), TEST_TIMEOUT);

        service.start(new ChatQuestion("如何配置"), sink);

        assertThat(sink.await()).isTrue();
        assertThat(sink.events()).extracting(ChatStreamEvent::name)
                .containsExactly("delta", "sources", "done");
        assertThat(sink.events().getFirst().data()).containsEntry("text", "可用配置");
        assertThat(sink.events().get(1).data()).containsEntry("sources", List.of(Map.of(
                "documentTitle", "配置手册", "sectionTitle", "安装/配置")));
    }

    /** 验证覆盖不足时只输出稳定错误码且不尝试输出成功事件。 */
    @Test
    void emitsStableErrorForRefusal() throws Exception {
        CapturingSink sink = new CapturingSink();
        ChatStreamService service = new ChatStreamService(
                question -> AnswerResponse.refusal(AnswerStatus.NO_RELEVANT_KNOWLEDGE), TEST_TIMEOUT);

        service.start(new ChatQuestion("未知问题"), sink);

        assertThat(sink.await()).isTrue();
        assertThat(sink.events()).extracting(ChatStreamEvent::name).containsExactly("error");
        assertThat(sink.events().getFirst().data()).containsEntry("code", "no_relevant_knowledge");
    }

    /** 验证异步模型调用能读取提交请求的 trace 上下文。 */
    @Test
    void propagatesMdcToAnswerWorker() throws Exception {
        CapturingSink sink = new CapturingSink();
        ChatStreamService service = new ChatStreamService(question -> new AnswerResponse(
                AnswerStatus.ANSWERED, MDC.get("traceId"), "test-model", emptyEvidence()), TEST_TIMEOUT);
        MDC.put("traceId", "trace-123");

        try {
            service.start(new ChatQuestion("追踪"), sink);
            assertThat(sink.await()).isTrue();
        } finally {
            MDC.clear();
        }

        assertThat(sink.events().getFirst().data()).containsEntry("text", "trace-123");
    }

    /** 验证客户端取消会中断尚在运行的阻塞回答任务。 */
    @Test
    void cancelsBlockingAnswerWhenClientDisconnects() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        ChatStreamService service = new ChatStreamService(question -> {
            started.countDown();
            try {
                Thread.sleep(TEST_TIMEOUT.toMillis() * 10);
            } catch (InterruptedException exception) {
                interrupted.set(true);
                interruptionObserved.countDown();
                Thread.currentThread().interrupt();
            }
            return AnswerResponse.refusal(AnswerStatus.ANSWER_FAILED);
        }, TEST_TIMEOUT);

        ChatStreamHandle handle = service.start(new ChatQuestion("取消"), new CapturingSink());
        assertThat(started.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        handle.cancel();

        assertThat(interruptionObserved.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(interrupted).isTrue();
    }

    /** 验证看门狗无法调度时会立即取消已提交的阻塞回答任务。 */
    @Test
    void cancelsAnswerWhenWatchdogRejectsScheduling() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);
        ThreadPoolExecutor answers = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));
        ScheduledThreadPoolExecutor watchdog = new ScheduledThreadPoolExecutor(1);
        watchdog.shutdownNow();
        ChatStreamService service = new ChatStreamService(question -> {
            started.countDown();
            try {
                Thread.sleep(TEST_TIMEOUT.toMillis() * 10);
            } catch (InterruptedException exception) {
                interruptionObserved.countDown();
                Thread.currentThread().interrupt();
            }
            return AnswerResponse.refusal(AnswerStatus.ANSWER_FAILED);
        }, TEST_TIMEOUT, answers, watchdog);

        try {
            service.start(new ChatQuestion("看门狗"), new CapturingSink());

            assertThat(started.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(interruptionObserved.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            service.close();
        }
    }

    /** 创建带可公开来源的已回答结果。 */
    private static AnswerResponse answered() {
        EvidenceCandidate section = new EvidenceCandidate("section-1", "document-1", "配置手册",
                "摘要", "正文", List.of("config"), 0, 0.9f, "安装/配置");
        return new AnswerResponse(AnswerStatus.ANSWERED, "可用配置", "test-model",
                new EvidenceContext(List.of(section), java.util.Set.of("document-1"), 5));
    }

    /** 创建没有来源的最小证据上下文。 */
    private static EvidenceContext emptyEvidence() {
        return new EvidenceContext(List.of(), java.util.Set.of(), 0);
    }

    /** 收集异步输出并在终态通知测试。 */
    private static final class CapturingSink implements ChatStreamSink {
        private final List<ChatStreamEvent> events = new ArrayList<>(3);
        private final CountDownLatch completed = new CountDownLatch(1);

        /** 收集一个 SSE 事件。 */
        @Override
        public synchronized void emit(ChatStreamEvent event) {
            events.add(event);
        }

        /** 标记 SSE 会话已结束。 */
        @Override
        public void complete() {
            completed.countDown();
        }

        /** 等待 SSE 会话结束。 */
        private boolean await() throws InterruptedException {
            return completed.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        /** 返回不可变事件快照。 */
        private synchronized List<ChatStreamEvent> events() {
            return List.copyOf(events);
        }
    }
}
