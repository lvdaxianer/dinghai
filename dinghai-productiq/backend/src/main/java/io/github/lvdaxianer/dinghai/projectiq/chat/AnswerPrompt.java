package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * Answerer 的受限提示输入。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record AnswerPrompt(String question, EvidenceContext evidence, String instruction) {
    /** 校验提示只携带受限证据。 */
    public AnswerPrompt {
        if (question == null || question.isBlank() || evidence == null || instruction == null) {
            throw new IllegalArgumentException("AnswerPrompt 参数无效");
        }
    }
}
