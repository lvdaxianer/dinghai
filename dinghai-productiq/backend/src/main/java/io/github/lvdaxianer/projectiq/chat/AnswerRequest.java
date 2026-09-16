package io.github.lvdaxianer.projectiq.chat;

import java.util.List;

/**
 * 回答用例输入，候选必须已完成 SQLite 当前性校验。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record AnswerRequest(String question, KnowledgeState knowledgeState,
                            List<EvidenceCandidate> candidates) {
    /** 校验并冻结调用方输入。 */
    public AnswerRequest {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        if (knowledgeState == null) {
            throw new IllegalArgumentException("knowledgeState 不能为空");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
