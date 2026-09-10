package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 可计入 Redis 熔断的连接或命令执行异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
public final class RedisBackendException extends IllegalStateException {

    /**
     * 创建 Redis 后端异常。
     *
     * @param cause Redis 原始异常
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public RedisBackendException(RuntimeException cause) {
        super("redis backend operation failed", cause);
    }
}
