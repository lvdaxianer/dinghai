package io.github.lvdaxianer.projectiq.chat;

import java.util.List;

/**
 * 当前性校验后、可供重排的章节候选；正文只在本地证据构建阶段使用。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record EvidenceCandidate(
        String sectionId,
        String documentId,
        String documentTitle,
        String summary,
        String content,
        List<String> conceptIds,
        int hop,
        float score,
        String headingPath) {
    /** 兼容不带章节路径的候选构造。 */
    public EvidenceCandidate(String sectionId, String documentId, String documentTitle, String summary,
                             String content, List<String> conceptIds, int hop, float score) {
        this(sectionId, documentId, documentTitle, summary, content, conceptIds, hop, score, "");
    }

    /** 冻结概念列表，避免模型边界被调用方之后修改。 */
    public EvidenceCandidate {
        conceptIds = conceptIds == null ? List.of() : List.copyOf(conceptIds);
    }

    /** 转换为不包含正文的重排输入。 */
    public RerankCandidate toRerankCandidate() {
        return new RerankCandidate(sectionId, documentId, documentTitle, summary, conceptIds, hop, score, headingPath);
    }
}
