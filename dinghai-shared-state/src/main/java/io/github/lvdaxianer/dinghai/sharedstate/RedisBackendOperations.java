package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;

/**
 * 将 Spring Data Redis 访问异常转换为可熔断后端异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class RedisBackendOperations {

    /**
     * 禁止实例化 Redis 后端操作工具类。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private RedisBackendOperations() {
    }

    /**
     * 执行带返回值的 Redis 访问。
     *
     * @param operation Redis 访问操作
     * @param <T> 返回值类型
     * @return Redis 返回值
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    static <T> T call(Supplier<T> operation) {
        try {
            return Objects.requireNonNull(operation, "operation").get();
        } catch (DataAccessException exception) {
            throw new RedisBackendException(exception);
        }
    }

    /**
     * 执行无返回值的 Redis 访问。
     *
     * @param operation Redis 访问操作
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    static void run(Runnable operation) {
        try {
            Objects.requireNonNull(operation, "operation").run();
        } catch (DataAccessException exception) {
            throw new RedisBackendException(exception);
        }
    }
}
