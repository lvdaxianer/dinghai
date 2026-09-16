## ADDED Requirements

### Requirement: ProductIQ SHALL provide reproducible local operations
The project SHALL include Docker Compose for the backend dependency topology and documentation for persistent SQLite/Qdrant/knowledge volumes, gateway environment variables, collection model/dimension rules, retries, timeout budgets, index rebuild, and rollback. Different embedding models SHALL use different Qdrant collections.

#### Scenario: Rebuild a cleared vector index
- **WHEN** an operator creates a new collection and runs the rebuild operation
- **THEN** current SQLite sections are re-embedded into the new model-specific collection before configuration cutover

### Requirement: Acceptance tooling SHALL cover retrieval, reasoning, refusal, and updates
The project SHALL include an executable acceptance fixture or test set covering direct Chinese questions, colloquial aliases, cross-document rules, overrides, no-coverage refusal, source replacement, and vector recovery. Results SHALL retain model identifiers, knowledge hashes, and selected section IDs for regression comparison.

#### Scenario: Run the baseline acceptance set
- **WHEN** the acceptance command runs against deterministic test adapters
- **THEN** it reports Top-20 recall, Top-3 rerank, ontology expansion, answer-source consistency, refusal, and rebuild outcomes with zero unexplained failures
