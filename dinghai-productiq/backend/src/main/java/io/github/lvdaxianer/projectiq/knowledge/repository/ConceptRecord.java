package io.github.lvdaxianer.projectiq.knowledge.repository;

/** SQLite 概念记录。 */
public record ConceptRecord(String conceptId, String name, String conceptType, String definition) {
}
