## ADDED Requirements

### Requirement: Retrieval SHALL hydrate and validate semantic candidates
The retriever SHALL embed the user question, query Qdrant for 20 to 30 section candidates, hydrate each section from SQLite, and discard missing, stale, or hash-mismatched candidates before ontology expansion.

#### Scenario: Discard a stale vector hit
- **WHEN** Qdrant returns a section whose stored source hash no longer matches SQLite
- **THEN** the section is excluded from candidates and cannot reach the Answerer

### Requirement: Ontology expansion SHALL be deterministic and bounded
The reasoner SHALL traverse only `creates`, `requires`, `governed_by`, `exception_of`, and `overrides` as strong relations, for at most four hops, eight newly discovered concepts, and three associated documents. `overrides` SHALL exclude superseded evidence; distant third/fourth-hop sections SHALL be available only to Reranker.

#### Scenario: Expand a cross-document question
- **WHEN** a direct section concept creates a state governed by a billing rule
- **THEN** the state and billing sections become candidates with hop metadata, while unrelated weak `related_to` sections do not enter final evidence automatically

#### Scenario: Stop expansion at safety limits
- **WHEN** a graph contains more than eight new concepts or a path longer than four hops
- **THEN** expansion stops deterministically and never returns more than three associated documents
