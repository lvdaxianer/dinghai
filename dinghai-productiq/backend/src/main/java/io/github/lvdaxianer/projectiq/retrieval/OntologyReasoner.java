package io.github.lvdaxianer.projectiq.retrieval;

import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.projectiq.knowledge.repository.RelationRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按固定边白名单执行有界、确定性的本体扩展。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class OntologyReasoner {
    private static final Set<String> STRONG_RELATIONS = Set.of("creates", "requires", "governed_by",
            "exception_of", "overrides");
    private static final int MAX_HOPS = 4;
    private static final int MAX_NEW_CONCEPTS = 8;
    private static final int MAX_DOCUMENTS = 3;

    /**
     * 从当前候选章节开始扩展强关系。
     *
     * @param seeds 已通过 SQLite 当前性校验的候选
     * @param store 事实仓储
     * @return 带跳数的扩展候选
     * @throws SQLException SQLite 查询失败
     */
    public List<OntologyCandidate> expand(List<HydratedCandidate> seeds, KnowledgeStore store) throws SQLException {
        Map<String, Integer> discovered = new HashMap<>();
        Set<String> documents = new HashSet<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        List<OntologyCandidate> result = new ArrayList<>();
        for (HydratedCandidate seed : seeds) {
            addSeed(seed, store, discovered, documents, queue, result);
        }
        Set<String> superseded = new HashSet<>();
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (node.hop() >= MAX_HOPS) continue;
            for (RelationRecord relation : store.findRelationsFromConcept(node.conceptId())) {
                if (!STRONG_RELATIONS.contains(relation.relationType())) continue;
                if ("overrides".equals(relation.relationType())) superseded.add(relation.toConceptId());
                if (discovered.containsKey(relation.toConceptId()) || discovered.size() >= MAX_NEW_CONCEPTS) continue;
                int hop = node.hop() + 1;
                discovered.put(relation.toConceptId(), hop);
                queue.addLast(new Node(relation.toConceptId(), hop));
                addConceptSections(relation.toConceptId(), hop, store, documents, result);
            }
        }
        return result.stream().filter(candidate -> !superseded.contains(candidate.conceptId())).toList();
    }

    /** 加入种子概念和其初始章节。 */
    private void addSeed(HydratedCandidate seed, KnowledgeStore store, Map<String, Integer> discovered,
                         Set<String> documents, ArrayDeque<Node> queue, List<OntologyCandidate> result)
            throws SQLException {
        for (String conceptId : store.findConceptIdsBySectionId(seed.sectionId())) {
            if (discovered.putIfAbsent(conceptId, 0) == null) {
                queue.addLast(new Node(conceptId, 0));
                result.add(new OntologyCandidate(seed.section(), conceptId, 0));
                documents.add(seed.section().documentId());
            }
        }
    }

    /** 加入关系目标概念对应的章节，限制关联文档数量。 */
    private void addConceptSections(String conceptId, int hop, KnowledgeStore store, Set<String> documents,
                                    List<OntologyCandidate> result) throws SQLException {
        for (SectionRecord section : store.findSectionsByConceptId(conceptId)) {
            if (!documents.contains(section.documentId()) && documents.size() >= MAX_DOCUMENTS) continue;
            documents.add(section.documentId());
            result.add(new OntologyCandidate(section, conceptId, hop));
        }
    }

    /** 广度优先遍历节点。 */
    private record Node(String conceptId, int hop) {
    }
}
