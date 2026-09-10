package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * 用于验证 JSON 快照隔离的可变缓存值。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class MutableCacheValue {

    /** 可变名称。 */
    private String name;

    /**
     * 创建空缓存值，供 JSON 反序列化使用。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public MutableCacheValue() {
    }

    /**
     * 创建指定名称的缓存值。
     *
     * @param name 名称
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    MutableCacheValue(String name) {
        this.name = name;
    }

    /**
     * 获取名称。
     *
     * @return 名称
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public String getName() {
        return name;
    }

    /**
     * 设置名称。
     *
     * @param name 名称
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    public void setName(String name) {
        this.name = name;
    }
}
