package io.github.lvdaxianer.dinghai.projectiq.chat;

/**
 * 当前知识索引对回答链路的可用状态。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public enum KnowledgeState {
    /** 知识库没有已发布事实。 */
    NO_KNOWLEDGE,
    /** 索引正在同步，旧候选不可用于回答。 */
    INDEX_UPDATING,
    /** 知识库可查询。 */
    READY
}
