package io.github.lvdaxianer.projectiq.knowledge.repository;

/**
 * Qdrant 同步任务的可恢复状态记录。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record VectorIndexJobRecord(long jobId, String sourceKey, String operation, String status,
                                   int retryCount, String errorMessage) {
}
