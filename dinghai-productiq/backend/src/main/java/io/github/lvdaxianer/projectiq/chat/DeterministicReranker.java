package io.github.lvdaxianer.projectiq.chat;

import java.util.Comparator;
import java.util.List;

/**
 * 按向量分数和跳数稳定排序的离线 Reranker fake。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class DeterministicReranker implements RerankerGateway {
    /** 返回分数高、跳数近且 ID 稳定的候选顺序。 */
    @Override
    public List<String> rank(String question, List<RerankCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(RerankCandidate::score).reversed()
                        .thenComparing(RerankCandidate::hop)
                        .thenComparing(RerankCandidate::sectionId))
                .map(RerankCandidate::sectionId)
                .toList();
    }
}
