package io.github.lvdaxianer.projectiq.retrieval;

import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;

/**
 * 通过 SQLite 当前性校验后的语义候选。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record HydratedCandidate(SectionRecord section, VectorMatch match) {
    /** 返回章节 ID。 */
    public String sectionId() {
        return section.sectionId();
    }
}
