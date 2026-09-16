package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * 受限证据回答端口，具体模型网关由适配器实现。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@FunctionalInterface
public interface Answerer {
    /**
     * 根据受限提示生成答案。
     *
     * @param prompt 包含问题和有界证据的提示
     * @return 模型答案
     */
    AnswerResult answer(AnswerPrompt prompt);
}
