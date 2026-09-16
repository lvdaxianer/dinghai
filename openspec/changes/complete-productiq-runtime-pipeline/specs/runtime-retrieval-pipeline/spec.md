## ADDED Requirements

### Requirement: Chat stream SHALL execute the current retrieval pipeline
For every valid chat question with current indexed knowledge, the runtime SHALL embed the question, obtain at most
20 vector candidates, hydrate and hash-validate them from SQLite, expand only allowed ontology relations, rerank
the resulting evidence candidates, and answer only from the bounded final evidence.

#### Scenario: Answer a current indexed question
- **WHEN** a question matches a current indexed section with sufficient evidence
- **THEN** `/api/chat/stream` emits `delta`, simplified `sources`, and `done` events using the selected SQLite sections

#### Scenario: Discard stale vector candidates
- **WHEN** a vector hit has a source hash different from the current SQLite document
- **THEN** that hit is excluded before ontology expansion and cannot reach the Answerer

### Requirement: Retrieval SHALL preserve stable refusal states
The runtime SHALL return `no_knowledge` when no current documents exist, `knowledge_updating` when index jobs are
not complete, and `no_relevant_knowledge` when current indexed candidates cannot support an answer. These states
SHALL not invoke the Answerer.

#### Scenario: Protect a document during index update
- **WHEN** a source has a pending, processing, or retryable failed vector index job
- **THEN** the chat stream emits `knowledge_updating` and does not use an old vector result
