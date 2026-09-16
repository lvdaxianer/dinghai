package io.github.lvdaxianer.projectiq.knowledge.repository;

/** SQLite 本体关系记录。 */
public record RelationRecord(long relationId, String fromConceptId, String relationType, String toConceptId) {
}
