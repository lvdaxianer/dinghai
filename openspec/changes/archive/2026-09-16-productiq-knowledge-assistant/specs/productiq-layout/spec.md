## ADDED Requirements

### Requirement: ProductIQ SHALL be isolated under the dinghai repository
The repository SHALL contain a top-level `dinghai-productiq/` product directory with sibling `backend/`, `skills/`, `plugins/`, `docs/`, `knowledge/`, and `docker-compose.yml` concerns. Existing `dinghai-*` modules SHALL remain build-compatible and SHALL NOT acquire source dependencies on ProductIQ.

#### Scenario: Build the existing repository after ProductIQ is added
- **WHEN** a developer runs the existing root Maven build
- **THEN** the original modules continue to compile without requiring ProductIQ runtime dependencies

### Requirement: Backend SHALL expose stable coordinates and package boundaries
The ProductIQ backend SHALL use group ID `io.github.lvdaxianer.dinghai`, artifact ID `knowledge-assistant`, Java 21, and root package `io.github.lvdaxianer.dinghai.projectiq`. Product code SHALL be grouped under `knowledge`, `retrieval`, `chat`, `integration`, `config`, and `shared` capabilities.

#### Scenario: Inspect the backend application metadata
- **WHEN** a developer opens the backend POM and application entry point
- **THEN** the coordinates, Java version, and root package match the approved values
