package io.github.lvdaxianer.projectiq.chat;

import java.util.List;

/**
 * 结构化重排模型的端口；实现只能返回候选 section ID。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@FunctionalInterface
public interface RerankerGateway {
    /**
     * 请求模型对受限摘要排序。
     *
     * @param question 用户问题
     * @param candidates 不含正文的候选摘要
     * @return 模型按优先级返回的 section ID
     */
    List<String> rank(String question, List<RerankCandidate> candidates);
}
