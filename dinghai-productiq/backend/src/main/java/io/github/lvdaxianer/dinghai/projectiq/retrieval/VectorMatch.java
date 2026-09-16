package io.github.lvdaxianer.dinghai.projectiq.retrieval;

/**
 * 描述向量检索命中的章节和相似度。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record VectorMatch(String sectionId, String documentId, String sourceKey, String sourceHash,
                          float score) {
}
