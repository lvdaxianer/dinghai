package io.github.lvdaxianer.projectiq.chat;

/**
 * 下游 Answerer 的成功结果。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record AnswerResult(String answer, String modelId) {
    /** 统一空值，避免调用方收到 null。 */
    public AnswerResult {
        answer = answer == null ? "" : answer;
        modelId = modelId == null ? "unknown" : modelId;
    }
}
