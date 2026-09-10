package io.github.lvdaxianer.dinghai.sharedstate;

import java.util.function.Supplier;

/**
 * 共享状态读穿回源加载的通用校验。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
final class CacheLoaderSupport {

    /**
     * 禁止实例化工具类。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private CacheLoaderSupport() {
    }

    /**
     * 执行回源加载并拒绝空结果。
     *
     * @param loader 回源加载器
     * @param <T> 缓存值类型
     * @return 非空回源值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    static <T> T loadNonNull(Supplier<? extends T> loader) {
        T loaded = loader.get();
        // 本库契约禁止缓存 null，回源空值按非法参数拒绝且不写入缓存。
        if (loaded == null) {
            throw new IllegalArgumentException("cache loader must not return null");
        }
        return loaded;
    }
}
