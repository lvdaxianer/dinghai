package io.github.lvdaxianer.dinghai.projectiq.knowledge.repository;

import java.time.Instant;

/**
 * SQLite 中的当前文档摘要记录。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record DocumentRecord(String documentId, String sourceKey, String title, String contentHash, Instant updatedAt) {
}
