package io.github.lvdaxianer.projectiq.retrieval;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于离线测试和无 Qdrant 环境的内存向量索引。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class InMemoryVectorIndex implements VectorIndex {
    private final Map<String, VectorPoint> points = new HashMap<>();

    /** 批量写入章节向量点。 */
    @Override
    public synchronized void upsert(List<VectorPoint> values) {
        values.forEach(value -> points.put(value.sectionId(), value));
    }

    /** 删除来源拥有的全部章节向量。 */
    @Override
    public synchronized void deleteBySourceKey(String sourceKey) {
        points.values().removeIf(point -> point.sourceKey().equals(sourceKey));
    }

    /** 按余弦相似度返回有限数量的候选章节。 */
    @Override
    public synchronized List<VectorMatch> search(List<Float> query, int limit) {
        return points.values().stream()
                .map(point -> new VectorMatch(point.sectionId(), point.documentId(), point.sourceKey(),
                        point.sourceHash(), cosine(query, point.vector())))
                .sorted(Comparator.comparing(VectorMatch::score).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    /** 重建内存索引时清空已有点，维度由后续 upsert 的向量决定。 */
    @Override
    public synchronized void recreate(int dimensions) {
        points.clear();
    }

    /** 计算两个向量的余弦相似度。 */
    private static float cosine(List<Float> left, List<Float> right) {
        if (left.size() != right.size() || left.isEmpty()) {
            return 0.0f;
        }
        float dot = 0.0f;
        float leftNorm = 0.0f;
        float rightNorm = 0.0f;
        for (int index = 0; index < left.size(); index++) {
            float leftValue = left.get(index);
            float rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        return leftNorm == 0.0f || rightNorm == 0.0f
                ? 0.0f : (float) (dot / Math.sqrt(leftNorm * rightNorm));
    }
}
