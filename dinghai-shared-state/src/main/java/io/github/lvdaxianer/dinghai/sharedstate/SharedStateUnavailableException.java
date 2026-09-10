package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 共享状态后端不可用且命名空间禁止本地降级时抛出的异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-18
 */
public final class SharedStateUnavailableException extends IllegalStateException {

    /**
     * 创建无底层异常的共享状态不可用异常。
     *
     * @param namespace 受影响的命名空间
     * @author lvdaxianer@yeah.net
     * @date 2026-07-18
     */
    public SharedStateUnavailableException(String namespace) {
        super("shared state unavailable for namespace: " + namespace);
    }

    /**
     * 创建共享状态不可用异常。
     *
     * @param namespace 受影响的命名空间
     * @param cause Redis 原始异常
     * @author lvdaxianer@yeah.net
     * @date 2026-07-18
     */
    public SharedStateUnavailableException(String namespace, Throwable cause) {
        super("shared state unavailable for namespace: " + namespace, cause);
    }
}
