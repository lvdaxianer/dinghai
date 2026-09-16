## ADDED Requirements

### Requirement: Reranker and Answerer SHALL be evidence constrained
Reranker SHALL receive only candidate IDs, metadata, summaries, concept IDs, and hop information, and Java SHALL accept only IDs from the original candidate set. Answerer SHALL receive at most six sections, three documents, and approximately 8,000 Chinese characters of complete evidence, and SHALL be instructed to refuse unsupported facts.

#### Scenario: Ignore an unknown Reranker ID
- **WHEN** the model returns a section ID not present in the candidate set
- **THEN** Java ignores that ID and answers only with validated candidate sections

### Requirement: Chat stream SHALL expose stable SSE events and refusal states
`POST /api/chat/stream` SHALL accept a JSON `question` body and emit `delta`, `sources`, and `done` events on success. It SHALL emit `error` with one of `no_knowledge`, `knowledge_updating`, `no_relevant_knowledge`, or `answer_failed` when applicable, and SHALL not call Answerer for the first three states.

#### Scenario: Answer a covered question
- **WHEN** valid evidence is reranked and the Answerer streams text
- **THEN** the client receives ordered delta events, simplified document/section sources, and a final done event

#### Scenario: Refuse an uncovered question
- **WHEN** no current section supports the question
- **THEN** the endpoint emits `no_knowledge` or `no_relevant_knowledge` without invoking the chat model

### Requirement: Backend observability SHALL avoid sensitive content
Every request SHALL have requestId/traceId context. Logs and metrics SHALL include status, timings, model IDs, candidate counts, hop counts, final IDs, and retry state, but SHALL NOT include full questions, evidence正文, complete answers, model keys, or cookies. Blocking downstream calls SHALL run on bounded dedicated executors and client disconnect SHALL cancel Answerer work.

#### Scenario: Trace an asynchronous index job
- **WHEN** a Qdrant sync job is executed on a worker thread
- **THEN** MDC trace context is restored for the job and cleared before the worker is reused
