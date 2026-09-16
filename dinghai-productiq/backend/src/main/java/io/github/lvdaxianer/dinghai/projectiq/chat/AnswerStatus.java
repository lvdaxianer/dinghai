package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * 回答链路对调用方稳定暴露的状态。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public enum AnswerStatus {
    /** 已由回答器生成答案。 */
    ANSWERED,
    /** 没有任何已发布知识。 */
    NO_KNOWLEDGE,
    /** 知识索引正在更新。 */
    KNOWLEDGE_UPDATING,
    /** 有知识但当前问题没有有效候选。 */
    NO_RELEVANT_KNOWLEDGE,
    /** 回答器调用失败。 */
    ANSWER_FAILED
}
