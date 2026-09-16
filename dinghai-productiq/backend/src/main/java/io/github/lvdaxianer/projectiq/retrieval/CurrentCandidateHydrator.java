package io.github.lvdaxianer.projectiq.retrieval;

import io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;

import java.sql.SQLException;
import java.util.List;

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
        return matches.stream().map(this::hydrateOne).flatMap(java.util.Optional::stream).toList();
    }

    /** 回查一个候选的章节和文档。 */
    private java.util.Optional<HydratedCandidate> hydrateOne(VectorMatch match) {
        try {
            return store.findSectionById(match.sectionId()).flatMap(section -> current(section, match));
        } catch (SQLException exception) {
            throw new IllegalStateException("SQLite 当前性查询失败", exception);
        }
    }

    /** 验证章节所属文档和 hash。 */
    private java.util.Optional<HydratedCandidate> current(
            io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord section, VectorMatch match) {
        try {
            return store.findDocumentById(section.documentId())
                    .filter(document -> currentHash(document, match))
                    .map(document -> new HydratedCandidate(section, match));
        } catch (SQLException exception) {
            throw new IllegalStateException("SQLite 文档当前性查询失败", exception);
        }
    }

    /** 比较向量 payload 和 SQLite 当前 hash。 */
    private boolean currentHash(DocumentRecord document, VectorMatch match) {
        return document.documentId().equals(match.documentId())
                && document.sourceKey().equals(match.sourceKey())
                && document.contentHash().equals(match.sourceHash());
    }
}
