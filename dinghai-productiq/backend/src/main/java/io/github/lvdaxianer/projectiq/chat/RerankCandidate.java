package io.github.lvdaxianer.projectiq.chat;

import java.util.List;

/**
 * 发送给 Reranker 的安全摘要，不包含证据正文。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record RerankCandidate(
        String sectionId,
        String documentId,
        String documentTitle,
        String summary,
        List<String> conceptIds,
        int hop,
        float score,
        String headingPath) {
    /** 兼容不带章节路径的摘要构造。 */
    public RerankCandidate(String sectionId, String documentId, String documentTitle, String summary,
                           List<String> conceptIds, int hop, float score) {
        this(sectionId, documentId, documentTitle, summary, conceptIds, hop, score, "");
    }

    /** 冻结概念列表，保持结构化请求不可变。 */
    public RerankCandidate {
        conceptIds = conceptIds == null ? List.of() : List.copyOf(conceptIds);
    }
}
