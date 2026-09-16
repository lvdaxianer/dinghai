package io.github.lvdaxianer.projectiq.retrieval;

import io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.VectorIndexJobRecord;

import java.sql.SQLException;
import java.util.List;

/**
 * 执行 SQLite 中的向量索引任务，并支持从当前事实重建 point。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class IndexJobRunner {
    private static final String UPSERT = "upsert";
    private static final String DELETE = "delete";
    private static final String PROCESSING = "processing";
    private static final String SUCCEEDED = "succeeded";
    private static final String FAILED = "failed";
    private final KnowledgeStore store;
    private final EmbeddingClient embeddingClient;
    private final VectorIndex vectorIndex;

    /** 创建索引任务执行器。 */
    public IndexJobRunner(KnowledgeStore store, EmbeddingClient embeddingClient, VectorIndex vectorIndex) {
        this.store = store;
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
    }

    /** 执行一批待处理任务，并返回成功和失败数量。 */
    public ExecutionResult executePending(int limit) throws SQLException {
        int succeeded = 0;
        int failed = 0;
        for (VectorIndexJobRecord job : store.findPendingVectorJobs(limit)) {
            if (executeOne(job)) {
                succeeded++;
            } else {
                failed++;
            }
        }
        return new ExecutionResult(succeeded, failed);
    }

    /** 执行一个任务并把失败记录回 SQLite。 */
    private boolean executeOne(VectorIndexJobRecord job) throws SQLException {
        store.updateVectorJobStatus(job.jobId(), PROCESSING, null);
        try {
            if (UPSERT.equals(job.operation())) {
                upsertCurrentSource(job.sourceKey());
            } else if (DELETE.equals(job.operation())) {
                vectorIndex.deleteBySourceKey(job.sourceKey());
            } else {
                throw new IllegalArgumentException("不支持的索引任务操作: " + job.operation());
            }
            store.updateVectorJobStatus(job.jobId(), SUCCEEDED, null);
            return true;
        } catch (java.io.IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            store.updateVectorJobStatus(job.jobId(), FAILED, safeMessage(exception));
            return false;
        }
    }

    /** 从 SQLite 当前文档和章节重建向量点。 */
    private void upsertCurrentSource(String sourceKey) throws java.io.IOException, InterruptedException, SQLException {
        DocumentRecord document = store.findDocumentBySourceKey(sourceKey)
                .orElseThrow(() -> new IllegalStateException("索引来源文档不存在"));
        List<VectorPoint> points = store.findSectionsBySourceKey(sourceKey).stream()
                .map(section -> point(section, sourceKey, document.contentHash()))
                .toList();
        vectorIndex.upsert(points);
    }

    /** 生成单个章节向量点。 */
    private VectorPoint point(SectionRecord section, String sourceKey, String sourceHash) {
        VectorPoint base = VectorPoint.from(section, sourceKey, sourceHash);
        return base.withVector(embeddingClient.embed(base.text()));
    }

    /** 将异常转换为有限安全消息。 */
    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.substring(0,
                Math.min(message.length(), 500));
    }

    /** 批量执行结果。 */
    public record ExecutionResult(int succeeded, int failed) {
    }
}
