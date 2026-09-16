package io.github.lvdaxianer.projectiq.chat;

/**
 * 回答用例对上层暴露的稳定响应。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record AnswerResponse(AnswerStatus status, String answer, String modelId,
                             EvidenceContext evidence) {
    /** 创建稳定拒答响应。 */
    public static AnswerResponse refusal(AnswerStatus status) {
        return new AnswerResponse(status, "", "none", new EvidenceContext(
                java.util.List.of(), java.util.Set.of(), 0));
    }
}
