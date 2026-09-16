package io.github.lvdaxianer.projectiq.retrieval;

import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;

/**
 * 带本体路径跳数的章节候选。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record OntologyCandidate(SectionRecord section, String conceptId, int hop) {
}
