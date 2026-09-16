package io.github.lvdaxianer.dinghai.projectiq.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 对结构化模型输出执行候选集合、数量和文档边界校验。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class StructuredReranker {
    /** 回答器允许的章节上限。 */
    public static final int MAX_SECTIONS = 6;
    /** 回答器允许的文档上限。 */
    public static final int MAX_DOCUMENTS = 3;

    private final RerankerGateway gateway;

    /** 创建结构化重排器。 */
    public StructuredReranker(RerankerGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    /** 将候选摘要交给模型，并只接受原候选集合中的 ID。 */
    public RerankResult rerank(String question, List<EvidenceCandidate> candidates) {
        List<EvidenceCandidate> safeCandidates = List.copyOf(candidates);
        List<String> modelIds = gateway.rank(question, safeCandidates.stream()
                .map(EvidenceCandidate::toRerankCandidate).toList());
        return new RerankResult(limitValidIds(modelIds, safeCandidates));
    }

    /** 过滤未知、重复、超过章节和文档上限的 ID。 */
    private List<String> limitValidIds(List<String> modelIds, List<EvidenceCandidate> candidates) {
        Map<String, EvidenceCandidate> byId = indexCandidates(candidates);
        Set<String> selectedIds = new HashSet<>(MAX_SECTIONS);
        Set<String> documentIds = new HashSet<>(MAX_DOCUMENTS);
        List<String> result = new ArrayList<>(MAX_SECTIONS);
        if (modelIds == null) {
            return result;
        }
        for (String modelId : modelIds) {
            if (accept(modelId, byId, selectedIds, documentIds)) {
                result.add(modelId);
            }
            if (result.size() == MAX_SECTIONS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    /** 建立候选索引，保留首次出现的 section。 */
    private Map<String, EvidenceCandidate> indexCandidates(List<EvidenceCandidate> candidates) {
        Map<String, EvidenceCandidate> byId = new HashMap<>(Math.max(16, candidates.size() * 2));
        candidates.forEach(candidate -> byId.putIfAbsent(candidate.sectionId(), candidate));
        return byId;
    }

    /** 判断模型 ID 是否能加入当前受限结果。 */
    private boolean accept(String modelId, Map<String, EvidenceCandidate> byId,
                           Set<String> selectedIds, Set<String> documentIds) {
        EvidenceCandidate candidate = byId.get(modelId);
        if (candidate == null || selectedIds.contains(modelId)) {
            return false;
        }
        boolean knownDocument = documentIds.contains(candidate.documentId());
        if (!knownDocument && documentIds.size() >= MAX_DOCUMENTS) {
            return false;
        }
        selectedIds.add(modelId);
        documentIds.add(candidate.documentId());
        return true;
    }
}
