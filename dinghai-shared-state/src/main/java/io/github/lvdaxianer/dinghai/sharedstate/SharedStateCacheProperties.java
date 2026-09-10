package io.github.lvdaxianer.dinghai.sharedstate;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 共享状态缓存配置属性。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
@ConfigurationProperties(prefix = "dinghai.cache")
public class SharedStateCacheProperties {

    /** Caffeine 后端配置。 */
    private final CaffeineProperties caffeine = new CaffeineProperties();

    /** Redis 后端配置。 */
    private final RedisProperties redis = new RedisProperties();

    /** Redis 容错配置。 */
    private final CircuitProperties circuit = new CircuitProperties();

    /**
     * 获取 Caffeine 后端配置。
     *
     * @return Caffeine 后端配置
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CaffeineProperties getCaffeine() {
        return caffeine;
    }

    /**
     * 获取 Redis 后端配置。
     *
     * @return Redis 后端配置
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public RedisProperties getRedis() {
        return redis;
    }

    /**
     * 获取 Redis 容错配置。
     *
     * @return Redis 容错配置
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public CircuitProperties getCircuit() {
        return circuit;
    }

    /**
     * Caffeine 后端配置属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static class CaffeineProperties {

        /** 默认最大缓存条目数。 */
        private static final long DEFAULT_MAXIMUM_SIZE = 10_000L;

        /** 最大缓存条目数。 */
        private long maximumSize = DEFAULT_MAXIMUM_SIZE;

        /**
         * 获取最大缓存条目数。
         *
         * @return 最大缓存条目数
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public long getMaximumSize() {
            return maximumSize;
        }

        /**
         * 设置最大缓存条目数。
         *
         * @param maximumSize 最大缓存条目数
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public void setMaximumSize(long maximumSize) {
            // 非正数容量会使后端无法正常工作，因此绑定时直接拒绝。
            if (maximumSize <= 0L) {
                throw new IllegalArgumentException("dinghai.cache.caffeine.maximum-size must be positive");
            }
            this.maximumSize = maximumSize;
        }
    }

    /**
     * Redis 后端连接配置属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static class RedisProperties extends RedisConnectionProperties {
    }

    /**
     * Redis 熔断、预热和恢复配置属性。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public static class CircuitProperties {

        /** 默认 Redis 连续失败阈值。 */
        private static final int DEFAULT_FAILURE_THRESHOLD = 3;

        /** 默认熔断打开时间。 */
        private static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(30L);

        /** 默认本地预热条目有效时间。 */
        private static final Duration DEFAULT_WARM_TTL = Duration.ofMinutes(5L);

        /** Redis 连续失败阈值。 */
        private int failureThreshold = DEFAULT_FAILURE_THRESHOLD;

        /** 熔断打开时间。 */
        private Duration openDuration = DEFAULT_OPEN_DURATION;

        /** 本地预热条目有效时间。 */
        private Duration warmTtl = DEFAULT_WARM_TTL;

        /**
         * 获取连续失败阈值。
         *
         * @return 连续失败阈值
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public int getFailureThreshold() {
            return failureThreshold;
        }

        /**
         * 设置连续失败阈值。
         *
         * @param failureThreshold 连续失败阈值
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public void setFailureThreshold(int failureThreshold) {
            // 非正数阈值无法形成有效熔断策略。
            if (failureThreshold <= 0) {
                throw new IllegalArgumentException("dinghai.cache.circuit.failure-threshold must be positive");
            }
            this.failureThreshold = failureThreshold;
        }

        /**
         * 获取熔断打开时间。
         *
         * @return 熔断打开时间
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public Duration getOpenDuration() {
            return openDuration;
        }

        /**
         * 设置熔断打开时间。
         *
         * @param openDuration 熔断打开时间
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public void setOpenDuration(Duration openDuration) {
            this.openDuration = requirePositive(openDuration, "open-duration");
        }

        /**
         * 获取本地预热条目有效时间。
         *
         * @return 本地预热条目有效时间
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public Duration getWarmTtl() {
            return warmTtl;
        }

        /**
         * 设置本地预热条目有效时间。
         *
         * @param warmTtl 本地预热条目有效时间
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        public void setWarmTtl(Duration warmTtl) {
            this.warmTtl = requirePositive(warmTtl, "warm-ttl");
        }

        /**
         * 校验正数时长。
         *
         * @param value 待校验时长
         * @param name 配置名称
         * @return 已校验时长
         * @author lvdaxianer@yeah.net
         * @date 2026-07-17
         */
        private Duration requirePositive(Duration value, String name) {
            Duration validValue = Objects.requireNonNull(value, name);
            // 零或负时长会使熔断和预热条目立即失效。
            if (validValue.isZero() || validValue.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return validValue;
        }
    }
}
