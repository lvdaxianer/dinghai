package io.github.lvdaxianer.dinghai.sharedstate;

import com.github.benmanes.caffeine.cache.Expiry;

/**
 * Caffeine 条目独立到期策略。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class CaffeineStoredEntryExpiry implements Expiry<String, CaffeineStoredEntry> {

    /**
     * 返回新条目的独立有效期。
     *
     * @param key 缓存键
     * @param value 缓存条目
     * @param currentTime 当前纳秒时间
     * @return 新条目有效纳秒数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public long expireAfterCreate(String key, CaffeineStoredEntry value, long currentTime) {
        return value.expiryNanos();
    }

    /**
     * 返回更新后条目的独立有效期。
     *
     * @param key 缓存键
     * @param value 更新后的缓存条目
     * @param currentTime 当前纳秒时间
     * @param currentDuration 更新前剩余纳秒数
     * @return 更新后条目有效纳秒数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public long expireAfterUpdate(String key, CaffeineStoredEntry value,
                                  long currentTime, long currentDuration) {
        return value.expiryNanos();
    }

    /**
     * 读取时保持条目剩余有效期不变。
     *
     * @param key 缓存键
     * @param value 缓存条目
     * @param currentTime 当前纳秒时间
     * @param currentDuration 当前剩余纳秒数
     * @return 当前剩余纳秒数
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    @Override
    public long expireAfterRead(String key, CaffeineStoredEntry value,
                                long currentTime, long currentDuration) {
        return currentDuration;
    }
}
