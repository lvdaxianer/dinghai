package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定期回收已过期的 multipart 临时会话和 Starter 内存归属状态。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class MultipartUploadSessionCleaner {

    /** 会话清理任务日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(MultipartUploadSessionCleaner.class);
    /** 清理任务在日志上下文中使用的稳定名称。 */
    private static final String JOB_NAME = "multipartUploadSessionCleanup";
    private final MultipartUploadService service;
    private final Clock clock;

    /**
     * 创建 multipart 会话清理任务。
     *
     * @param service multipart 会话服务
     * @param clock 当前时间源
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    public MultipartUploadSessionCleaner(MultipartUploadService service, Clock clock) {
        this.service = Objects.requireNonNull(service, "service");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 每五分钟回收过期会话，间隔可由宿主配置覆盖。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Scheduled(fixedDelayString = "${dinghai.file-storage.multipart.cleanup-interval:PT5M}")
    public void cleanupExpiredSessions() {
        MDC.put("traceId", "job-" + UUID.randomUUID().toString().replace("-", ""));
        MDC.put("jobName", JOB_NAME);
        long startedAt = System.nanoTime();
        try {
            LOGGER.info("[multipart session cleanup] job start, name={}", JOB_NAME);
            int removed = service.cleanupExpiredSessions(clock.instant());
            LOGGER.info("[multipart session cleanup] job done, name={}, removed={}, cost={}ms", JOB_NAME,
                    removed, elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            LOGGER.error("[multipart session cleanup] job failed, name={}, cost={}ms", JOB_NAME,
                    elapsedMillis(startedAt), exception);
            throw exception;
        } finally {
            MDC.clear();
        }
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
