package io.github.lvdaxianer.dinghai.projectiq.chat;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在专用有界线程池中执行阻塞回答，并将同步回答适配为稳定的 SSE 事件序列。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class ChatStreamService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamService.class);
    private static final int ANSWER_CORE_THREADS = 2;
    private static final int ANSWER_MAX_THREADS = 4;
    private static final int ANSWER_QUEUE_CAPACITY = 32;
    private static final int WATCHDOG_THREADS = 1;
    private static final long KEEP_ALIVE_SECONDS = 30L;
    private static final String ANSWER_THREAD_PREFIX = "productiq-answer-blocking-";
    private static final String WATCHDOG_THREAD_PREFIX = "productiq-chat-watchdog-";
    private final ChatAnswerGateway gateway;
    private final Duration timeout;
    private final ThreadPoolExecutor answerExecutor;
    private final ScheduledExecutorService watchdogExecutor;

    /**
     * 创建使用专用有界执行器的 SSE 编排器。
     *
     * @param gateway 回答用例端口
     * @param timeout 单次下游回答的整体时限
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public ChatStreamService(ChatAnswerGateway gateway, Duration timeout) {
        this(gateway, timeout, answerExecutor(), watchdogExecutor());
    }

    /**
     * 创建可注入执行器的 SSE 编排器，供配置和确定性测试使用。
     *
     * @param gateway 回答用例端口
     * @param timeout 单次下游回答的整体时限
     * @param answerExecutor 阻塞回答专用执行器
     * @param watchdogExecutor 超时取消执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    ChatStreamService(ChatAnswerGateway gateway, Duration timeout, ThreadPoolExecutor answerExecutor,
                      ScheduledExecutorService watchdogExecutor) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.timeout = requirePositive(timeout);
        this.answerExecutor = Objects.requireNonNull(answerExecutor, "answerExecutor");
        this.watchdogExecutor = Objects.requireNonNull(watchdogExecutor, "watchdogExecutor");
    }

    /**
     * 异步开始一条 SSE 会话，并返回可由 HTTP 断开回调调用的取消句柄。
     *
     * @param question 已校验问题
     * @param sink SSE 传输端口
     * @return 可取消的会话句柄
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public ChatStreamHandle start(ChatQuestion question, ChatStreamSink sink) {
        StreamControl control = new StreamControl(sink);
        Future<?> future = null;
        try {
            future = answerExecutor.submit(MdcTaskContext.wrap(() -> answer(question, control)));
            control.setFuture(future);
            watchdogExecutor.schedule(MdcTaskContext.wrap(() -> timeout(control)), timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            // 看门狗不可用时不得遗留已提交的阻塞下游调用。
            if (future != null) {
                future.cancel(true);
            }
            reject(control, exception);
        }
        return control;
    }

    /**
     * 调用回答用例并映射成成功或拒答事件。
     *
     * @param question 已校验问题
     * @param control 当前会话控制器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void answer(ChatQuestion question, StreamControl control) {
        long startedAt = System.nanoTime();
        try {
            AnswerResponse response = gateway.answer(question);
            emitResponse(response, control);
            logCompletion(response, startedAt);
        } catch (RuntimeException exception) {
            fail(control, startedAt, exception);
        }
    }

    /**
     * 为回答或拒答选择唯一的稳定事件分支。
     *
     * @param response 回答用例输出
     * @param control 当前会话控制器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void emitResponse(AnswerResponse response, StreamControl control) {
        AnswerResponse safeResponse = Objects.requireNonNull(response, "answer response");
        // 回答成功时严格依次输出文本、来源和完成事件。
        if (safeResponse.status() == AnswerStatus.ANSWERED) {
            control.emit(ChatStreamEvent.delta(safeResponse.answer()));
            control.emit(ChatStreamEvent.sources(sources(safeResponse.evidence())));
            control.emit(ChatStreamEvent.done());
        } else {
            control.emit(ChatStreamEvent.error(errorCode(safeResponse.status())));
        }
        control.complete();
    }

    /**
     * 将受限证据转换为不含正文、摘要或概念细节的公开来源。
     *
     * @param evidence 有界证据上下文
     * @return 客户端可展示的来源列表
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private List<Map<String, String>> sources(EvidenceContext evidence) {
        List<Map<String, String>> sources = new ArrayList<>(evidence.sections().size());
        evidence.sections().forEach(section -> sources.add(Map.of(
                "documentTitle", safeText(section.documentTitle()),
                "sectionTitle", safeText(section.headingPath()))));
        return List.copyOf(sources);
    }

    /**
     * 将可能为空的展示文本转换为安全空串，避免来源事件构造失败。
     *
     * @param value 原始展示文本
     * @return 非空展示文本
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * 将拒答状态收敛为契约规定的错误码。
     *
     * @param status 回答状态
     * @return SSE error code
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String errorCode(AnswerStatus status) {
        return switch (status) {
            case NO_KNOWLEDGE -> "no_knowledge";
            case KNOWLEDGE_UPDATING -> "knowledge_updating";
            case NO_RELEVANT_KNOWLEDGE -> "no_relevant_knowledge";
            case ANSWER_FAILED -> "answer_failed";
            case ANSWERED -> throw new IllegalArgumentException("已回答状态不应映射错误码");
        };
    }

    /**
     * 在下游异常时输出安全错误码并记录不包含文本内容的异常现场。
     *
     * @param control 当前会话控制器
     * @param startedAt 调用起始纳秒时间
     * @param exception 下游异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void fail(StreamControl control, long startedAt, RuntimeException exception) {
        // 客户端已取消时不再写错误事件或记录模型异常细节。
        if (control.isTerminated()) {
            return;
        }
        LOGGER.error("[知识问答] status={} exceptionType={} elapsedMs={}", "answer_failed",
                exception.getClass().getSimpleName(), elapsedMillis(startedAt));
        control.emit(ChatStreamEvent.error("answer_failed"));
        control.complete();
    }

    /**
     * 当线程池饱和时返回可恢复的稳定错误码。
     *
     * @param control 当前会话控制器
     * @param exception 拒绝执行异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void reject(StreamControl control, RuntimeException exception) {
        LOGGER.warn("[知识问答] status={} reason={}", "answer_failed", exception.getClass().getSimpleName());
        control.emit(ChatStreamEvent.error("answer_failed"));
        control.complete();
    }

    /**
     * 到达整体时限时中断下游任务并向仍连接的客户端输出失败事件。
     *
     * @param control 当前会话控制器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void timeout(StreamControl control) {
        // 已终态的会话不再重复发送错误事件。
        if (control.beginTermination()) {
            control.cancelFuture();
            LOGGER.warn("[知识问答] status={}", "answer_failed");
            try {
                control.emitUnchecked(ChatStreamEvent.error("answer_failed"));
            } finally {
                control.completeUnchecked();
            }
        }
    }

    /**
     * 记录受控的完成摘要，严格排除问题、证据正文和完整答案。
     *
     * @param response 回答结果
     * @param startedAt 调用起始纳秒时间
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void logCompletion(AnswerResponse response, long startedAt) {
        LOGGER.info("[知识问答] status={} modelId={} sourceCount={} elapsedMs={}", response.status(),
                response.modelId(), response.evidence().sections().size(), elapsedMillis(startedAt));
    }

    /**
     * 将纳秒差转换为日志使用的毫秒。
     *
     * @param startedAt 调用起始纳秒时间
     * @return 已耗时毫秒
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    /**
     * 校验调用整体时限有效。
     *
     * @param timeout 下游调用时限
     * @return 原时限
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private Duration requirePositive(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout 必须大于零");
        }
        return timeout;
    }

    /**
     * 创建隔离阻塞模型调用的有界专用线程池。
     *
     * @return 专用回答执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static ThreadPoolExecutor answerExecutor() {
        return new ThreadPoolExecutor(ANSWER_CORE_THREADS, ANSWER_MAX_THREADS, KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(ANSWER_QUEUE_CAPACITY), threadFactory(ANSWER_THREAD_PREFIX),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建与回答执行器隔离的超时看门狗。
     *
     * @return 专用超时执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static ScheduledExecutorService watchdogExecutor() {
        return new ScheduledThreadPoolExecutor(WATCHDOG_THREADS, threadFactory(WATCHDOG_THREAD_PREFIX));
    }

    /**
     * 创建具备稳定业务前缀且不会阻塞应用退出的线程工厂。
     *
     * @param prefix 线程业务前缀
     * @return 命名线程工厂
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 停止专用执行器，供 Spring 关闭阶段回收线程池资源。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @PreDestroy
    public void close() {
        answerExecutor.shutdownNow();
        watchdogExecutor.shutdownNow();
    }

    /** SSE 单会话的原子终态和 Future 取消控制。 */
    private static final class StreamControl implements ChatStreamHandle {
        private final ChatStreamSink sink;
        private final AtomicBoolean terminated = new AtomicBoolean();
        private volatile Future<?> future;

        /** 创建持有传输端口的会话控制器。 */
        private StreamControl(ChatStreamSink sink) {
            this.sink = Objects.requireNonNull(sink, "sink");
        }

        /** 绑定可取消的回答 Future。 */
        private void setFuture(Future<?> future) {
            this.future = future;
        }

        /** 仅为第一个终态发送者打开写事件的窗口。 */
        private boolean beginTermination() {
            return terminated.compareAndSet(false, true);
        }

        /** 返回当前会话是否已被完成、超时或客户端断开终止。 */
        private boolean isTerminated() {
            return terminated.get();
        }

        /** 写入普通事件，客户端断开时将该会话标记为结束。 */
        private void emit(ChatStreamEvent event) {
            if (!terminated.get()) {
                sink.emit(event);
            }
        }

        /** 在已拥有终态权限的路径写入事件。 */
        private void emitUnchecked(ChatStreamEvent event) {
            sink.emit(event);
        }

        /** 完成正常或拒答响应。 */
        private void complete() {
            if (beginTermination()) {
                sink.complete();
            }
        }

        /** 在已拥有终态权限的路径结束传输。 */
        private void completeUnchecked() {
            sink.complete();
        }

        /** 取消 Future，向支持中断的下游调用传播客户端断开。 */
        private void cancelFuture() {
            if (future != null) {
                future.cancel(true);
            }
        }

        /** 取消未结束会话。 */
        @Override
        public void cancel() {
            if (beginTermination()) {
                cancelFuture();
            }
        }
    }
}
