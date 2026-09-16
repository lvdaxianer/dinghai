## ADDED Requirements

### Requirement: Runtime SHALL use an explicit dependency mode
The backend SHALL provide `deterministic` and `remote` runtime modes. Deterministic mode SHALL use offline,
repeatable adapters. Remote mode SHALL require a nonblank gateway API key, valid HTTP gateway URL, model IDs,
embedding dimension, and Qdrant collection configuration, and SHALL fail startup if any required value is absent.

#### Scenario: Reject incomplete remote configuration
- **WHEN** `productiq.runtime.mode` is `remote` and a required gateway configuration value is missing
- **THEN** application context initialization fails instead of silently using deterministic adapters

### Requirement: Remote model adapters SHALL validate bounded responses
The remote Embedding adapter SHALL call an OpenAI-compatible embeddings endpoint and reject missing, empty, or
dimension-mismatched vectors. The Reranker adapter SHALL accept only JSON-ranked IDs and SHALL never expand the
original Java candidate set.

#### Scenario: Reject malformed remote reranker output
- **WHEN** the gateway response does not contain a valid ranked section ID array
- **THEN** the Reranker produces no selected IDs and no untrusted ID reaches evidence construction

### Requirement: Vector index jobs SHALL run recoverably in the runtime
The backend SHALL process persisted vector jobs on a bounded dedicated worker. It SHALL persist processing,
succeeded, and failed outcomes and retry failed jobs on later scheduled executions without changing SQLite facts.

#### Scenario: Recover a failed index job
- **WHEN** a previous vector job is failed and its dependency later becomes available
- **THEN** a later worker execution reprocesses it and persists a succeeded state
