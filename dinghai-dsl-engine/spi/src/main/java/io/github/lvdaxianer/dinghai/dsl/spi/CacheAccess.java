package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 缓存安全访问工具。
 *
 * <p>缓存故障只会降级为 miss 或跳过写入，不改变解析、权限校验和后端失败语义。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class CacheAccess {

    private CacheAccess() {
    }

    /**
     * 安全读取缓存。
     *
     * @param reader 缓存读取操作
     * @param <T> 缓存值类型
     * @return 命中值或 miss
     */
    public static <T> Optional<T> readOrMiss(Supplier<Optional<T>> reader) {
        try {
            Optional<T> value = reader.get();
            return value == null ? Optional.empty() : value;
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * 安全写入缓存。
     *
     * @param writer 缓存写入操作
     */
    public static void writeOrIgnore(Runnable writer) {
        try {
            writer.run();
        } catch (RuntimeException ignored) {
            // 缓存不可用时继续走本次有界解析或编译流程。
        }
    }
}
