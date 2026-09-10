package io.github.lvdaxianer.dinghai.sharedstate;

/**
 * Caffeine 后端内部存储条目。
 *
 * @param serializedEnvelope JSON 缓存值信封
 * @param expiryNanos 条目有效纳秒数
 * @param createdAtNanos 条目写入时的单调时钟值
 * @param isEternal 是否永久有效
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
record CaffeineStoredEntry(String serializedEnvelope, long expiryNanos,
                           long createdAtNanos, boolean isEternal) {
}
