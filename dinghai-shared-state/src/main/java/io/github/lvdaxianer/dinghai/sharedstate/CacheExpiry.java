package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.Objects;

/**
 * 缓存条目的显式到期策略。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class CacheExpiry {

    /** Caffeine 可接受的永久有效时长。 */
    private static final Duration ETERNAL_DURATION = Duration.ofNanos(Long.MAX_VALUE);

    /** 永久有效策略单例。 */
    private static final CacheExpiry ETERNAL = new CacheExpiry(ETERNAL_DURATION, true);

    /** 条目有效时长。 */
    private final Duration ttl;

    /** 是否永久有效。 */
    private final boolean isEternal;

    /**
     * 创建到期策略。
     *
     * @param ttl       条目有效时长
     * @param isEternal 是否永久有效
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private CacheExpiry(Duration ttl, boolean isEternal) {
        this.ttl = ttl;
        this.isEternal = isEternal;
    }

    /**
     * 获取永久有效策略。
     *
     * @return 永久有效策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static CacheExpiry eternal() {
        return ETERNAL;
    }

    /**
     * 创建正数 TTL 到期策略。
     *
     * @param ttl 条目有效时长
     * @return TTL 到期策略
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static CacheExpiry after(Duration ttl) {
        Duration validTtl = Objects.requireNonNull(ttl, "ttl");
        // 非正数 TTL 会产生立即失效或永不一致的后端行为，因此拒绝。
        if (validTtl.isZero() || validTtl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return new CacheExpiry(validTtl, false);
    }

    /**
     * 获取条目有效时长。
     *
     * @return 条目有效时长
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public Duration ttl() {
        return ttl;
    }

    /**
     * 获取供后端使用的纳秒时长。
     *
     * @return 纳秒时长，溢出时返回最大长整型
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public long toNanos() {
        try {
            return ttl.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 判断条目是否永久有效。
     *
     * @return true 表示永久有效
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public boolean isEternal() {
        return isEternal;
    }
}
