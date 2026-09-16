## ADDED Requirements

### Requirement: SQLite SHALL be the authoritative current knowledge store
The backend SHALL persist documents, sections, concepts, aliases, section-concept mappings, relations, relation evidence, and vector index jobs in SQLite with the documented uniqueness constraints. Qdrant payloads SHALL never be the source of authoritative正文、关系或发布状态。

#### Scenario: Read current facts after an index outage
- **WHEN** Qdrant is unavailable but SQLite contains a committed document
- **THEN** the document, sections, concepts, and evidence remain queryable and the index job is marked pending or failed for retry

### Requirement: Knowledge import SHALL be transactional and idempotent
The import endpoint SHALL validate a restricted `sourceKey`, use `source_key + content_hash` as an idempotency key, replace only the source-owned sections/mappings/evidence inside one transaction, retain relations still evidenced by other documents, and enqueue Qdrant delete/upsert work only after commit.

#### Scenario: Repeat an already completed import
- **WHEN** the same source key and content hash are imported twice
- **THEN** the second request returns the existing completed result without duplicating facts or index jobs

#### Scenario: Replace one document with a removed relation
- **WHEN** a new package omits a relation previously evidenced only by that document
- **THEN** the old evidence and relation are removed transactionally and other documents' unrelated facts remain intact

### Requirement: Explicit source deletion SHALL not be inferred from scans
The backend SHALL expose an explicit source deletion operation that removes the source-owned facts and schedules vector deletion. A document absent from a directory scan SHALL NOT be deleted implicitly.

#### Scenario: Delete a trusted source explicitly
- **WHEN** an authorized operator requests deletion for `team.md`
- **THEN** SQLite removes only that source's sections and evidence, and Qdrant deletion is queued
