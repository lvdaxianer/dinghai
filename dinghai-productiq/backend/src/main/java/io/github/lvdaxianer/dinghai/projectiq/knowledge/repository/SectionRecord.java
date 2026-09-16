package io.github.lvdaxianer.dinghai.projectiq.knowledge.repository;

/**
 * SQLite 中可独立检索的章节记录。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record SectionRecord(String sectionId, String documentId, String headingPathJson, String summary,
                            String content, int sourceStartLine, int sourceEndLine) {
}
