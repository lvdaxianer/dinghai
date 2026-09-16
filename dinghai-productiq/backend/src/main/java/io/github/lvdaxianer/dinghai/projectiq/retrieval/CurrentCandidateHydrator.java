package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.SectionRecord;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 将向量候选回查 SQLite，并过滤缺失或 hash 过期的章节。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class CurrentCandidateHydrator {
    private final KnowledgeStore store;

    /** 创建当前性水合器。 */
    public CurrentCandidateHydrator(KnowledgeStore store) {
        this.store = store;
    }

    /**
     * 只保留仍属于当前文档 hash 的候选。
     *
     * @param matches Qdrant 或内存索引候选
     * @return 当前有效候选
     * @throws SQLException SQLite 查询失败
     */
    public List<HydratedCandidate> hydrate(List<VectorMatch> matches) throws SQLException {
        Map<String, SectionRecord> sections = store.findSectionsByIds(sectionIds(matches));
        Map<String, DocumentRecord> documents = store.findDocumentsByIds(documentIds(sections));
        return matches.stream().filter(match -> current(match, sections, documents)).map(match ->
                new HydratedCandidate(sections.get(match.sectionId()), match)).toList();
    }

    /**
     * 提取去重后的候选章节 ID。
     *
     * @param matches 向量召回候选
     * @return 稳定章节 ID
     */
    private List<String> sectionIds(List<VectorMatch> matches) {
        return matches.stream().map(VectorMatch::sectionId).distinct().toList();
    }

    /**
     * 提取章节关联的去重文档 ID。
     *
     * @param sections 当前章节映射
     * @return 稳定文档 ID
     */
    private List<String> documentIds(Map<String, SectionRecord> sections) {
        return sections.values().stream().map(SectionRecord::documentId).distinct().toList();
    }

    /**
     * 验证向量载荷仍指向 SQLite 当前章节和当前文档哈希。
     *
     * @param match 向量命中
     * @param sections 当前章节映射
     * @param documents 当前文档映射
     * @return 当前候选时为 true
     */
    private boolean current(VectorMatch match, Map<String, SectionRecord> sections,
                            Map<String, DocumentRecord> documents) {
        SectionRecord section = sections.get(match.sectionId());
        return section != null && currentHash(documents.get(section.documentId()), match);
    }

    /**
     * 比较向量 payload 和 SQLite 当前 hash。
     *
     * @param document 当前文档，可为空
     * @param match 向量命中
     * @return 文档和哈希均当前时为 true
     */
    private boolean currentHash(DocumentRecord document, VectorMatch match) {
        return document != null && document.documentId().equals(match.documentId())
                && document.sourceKey().equals(match.sourceKey())
                && document.contentHash().equals(match.sourceHash());
    }
}
