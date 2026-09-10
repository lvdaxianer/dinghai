package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * Caffeine 后端内部存储条目。
 *
 * @param serializedEnvelope JSON 缓存值信封
 * @param expiryNanos 条目有效纳秒数
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
record CaffeineStoredEntry(String serializedEnvelope, long expiryNanos) {
}
