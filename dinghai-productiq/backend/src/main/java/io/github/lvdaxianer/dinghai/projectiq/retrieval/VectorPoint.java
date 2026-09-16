package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.SectionRecord;

import java.util.List;
import java.util.Objects;

/**
 * 描述一个可重建的章节向量点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record VectorPoint(String sectionId, String documentId, String sourceKey, String sourceHash,
                          List<Float> vector, String text) {
    public VectorPoint {
        Objects.requireNonNull(sectionId, "sectionId 不能为空");
        Objects.requireNonNull(documentId, "documentId 不能为空");
        Objects.requireNonNull(sourceKey, "sourceKey 不能为空");
        Objects.requireNonNull(sourceHash, "sourceHash 不能为空");
        Objects.requireNonNull(vector, "vector 不能为空");
        Objects.requireNonNull(text, "text 不能为空");
        vector = List.copyOf(vector);
    }

    /** 从 SQLite 章节记录构造带当前文档哈希的向量点。 */
    public static VectorPoint from(SectionRecord section, String sourceKey, String sourceHash) {
        return new VectorPoint(section.sectionId(), section.documentId(), sourceKey, sourceHash,
                List.of(), section.content());
    }

    /** 返回使用指定向量的同一章节点。 */
    public VectorPoint withVector(List<Float> values) {
        return new VectorPoint(sectionId, documentId, sourceKey, sourceHash, values, text);
    }
}
