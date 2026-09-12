package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 每天午夜清理非本地 provider 的空闲磁盘缓存副本。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public final class ProviderFileReadCacheCleaner {
    /** 被清理的读取缓存。 */
    private final ProviderFileReadCache cache;
    /** 当前时间源。 */
    private final Clock clock;
    /** 空闲保留时间。 */
    private final Duration idleTtl;

    /** 创建缓存清理任务。 */
    public ProviderFileReadCacheCleaner(ProviderFileReadCache cache, Clock clock, Duration idleTtl) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idleTtl = Objects.requireNonNull(idleTtl, "idleTtl");
    }

    /** 在服务端本地时间每日 00:00 清理过期缓存。 */
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanAtMidnight() {
        cache.cleanExpired(clock.instant(), idleTtl);
    }
}
