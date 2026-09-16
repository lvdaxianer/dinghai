package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * SSE 层调用的受限回答用例端口，检索编排可在此端口后独立演进。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@FunctionalInterface
public interface ChatAnswerGateway {
    /**
     * 根据已校验问题生成回答或稳定拒答状态。
     *
     * @param question 已校验问题
     * @return 回答结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    AnswerResponse answer(ChatQuestion question);
}
