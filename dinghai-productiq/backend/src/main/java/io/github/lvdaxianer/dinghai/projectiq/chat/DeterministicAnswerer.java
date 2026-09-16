package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * 不依赖外部模型的确定性 Answerer，用于离线测试和本地验收。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class DeterministicAnswerer implements Answerer {
    /** 根据第一节证据生成可重复的最小答案。 */
    @Override
    public AnswerResult answer(AnswerPrompt prompt) {
        EvidenceCandidate first = prompt.evidence().sections().getFirst();
        String answer = "依据「" + first.documentTitle() + "」的「" + first.sectionId() + "」："
                + first.content();
        return new AnswerResult(answer, "deterministic-fake");
    }
}
