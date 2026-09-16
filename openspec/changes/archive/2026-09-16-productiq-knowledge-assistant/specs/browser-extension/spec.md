## ADDED Requirements

### Requirement: Chrome extension SHALL use only backend HTTP/SSE contracts
The Manifest V3 extension SHALL provide popup question input, streaming answer rendering, sources, cancellation, error/empty/loading states, and an options page for backend base URL and a lightweight client key. It SHALL never contain a model gateway key or call SQLite/Qdrant directly.

#### Scenario: Submit and cancel a question
- **WHEN** the user submits a question and then presses cancel
- **THEN** the extension aborts the fetch, restores an actionable idle state, and does not leave a stale answer stream

#### Scenario: Render sources without internal details
- **WHEN** a successful stream includes sources
- **THEN** the popup shows document and section names only, without quotes, section IDs, distances, or model diagnostics
