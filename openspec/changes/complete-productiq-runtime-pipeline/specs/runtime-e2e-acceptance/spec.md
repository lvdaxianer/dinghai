## ADDED Requirements

### Requirement: Acceptance SHALL exercise real backend composition
The project SHALL provide automated acceptance tests that compose the runtime chat gateway with a real SQLite
knowledge store and an in-memory vector index. The tests SHALL import or persist representative current facts,
execute indexing, and assert SSE answer, refusal, source, and index-updating outcomes.

#### Scenario: Verify an answer through the complete local pipeline
- **WHEN** representative product knowledge is persisted and its vector job is executed
- **THEN** a chat question produces selected sources through embedding, vector retrieval, hydration, reasoning,
reranking, and answering rather than a test-only canned chat gateway

#### Scenario: Verify a no-knowledge refusal
- **WHEN** the runtime has no current documents
- **THEN** a valid chat question produces the stable `no_knowledge` event without invoking the Answerer
