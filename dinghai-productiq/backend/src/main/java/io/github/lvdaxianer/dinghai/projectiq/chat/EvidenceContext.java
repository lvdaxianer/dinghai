package io.github.lvdaxianer.dinghai.projectiq.chat;

import java.util.List;
import java.util.Set;

/**
 * 传给 Answerer 的完整但有界证据上下文。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record EvidenceContext(
        List<EvidenceCandidate> sections,
        Set<String> documentIds,
        int characterCount) {
    /** 固化并校验 Answerer 的证据边界。 */
    public EvidenceContext {
        sections = sections == null ? List.of() : List.copyOf(sections);
        documentIds = documentIds == null ? Set.of() : Set.copyOf(documentIds);
        if (sections.size() > StructuredReranker.MAX_SECTIONS
                || documentIds.size() > StructuredReranker.MAX_DOCUMENTS
                || characterCount < 0 || characterCount > EvidenceContextBuilder.MAX_CHARACTERS
                || sections.stream().anyMatch(section -> section.hop() >= 3)) {
            throw new IllegalArgumentException("证据上下文超出回答边界");
        }
    }

    /** 返回用于模型提示词的固定拒答约束。 */
    public String instruction() {
        return "仅根据证据回答；证据未明确支持时必须拒答，不得补写或猜测事实。";
    }
}
