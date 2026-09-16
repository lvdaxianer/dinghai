# dinghai-productiq Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `dinghai` 仓库下交付可构建、可测试的 `dinghai-productiq` 产品知识本体问答助手闭环。

**Architecture:** `dinghai-productiq/backend` 是按能力分包的 Spring Boot 模块化单体；SQLite 保存权威事实，Qdrant 负责可重建语义候选，模型网关通过接口适配。`skills` 与 `plugins` 是独立交付边界，分别使用知识包协议和 HTTP/SSE 契约。

**Tech Stack:** Java 21、Spring Boot 3.5、Maven、SQLite JDBC、Jackson、Spring Web/SSE、Java HTTP Client、Manifest V3、原生 JavaScript、Docker Compose。

---

### Task 1: 工程骨架与构建基线

**Owned module:** `dinghai-productiq` layout and backend bootstrap. No database, retrieval, chat, or plugin behavior in this task.

**Files:**
- Create: `dinghai-productiq/README.md`
- Create: `dinghai-productiq/backend/pom.xml`
- Create: `dinghai-productiq/backend/src/main/java/io/github/lvdaxianer/projectiq/KnowledgeAssistantApplication.java`
- Create: `dinghai-productiq/backend/src/main/resources/application.yml`
- Create: `dinghai-productiq/backend/src/test/java/io/github/lvdaxianer/projectiq/KnowledgeAssistantApplicationTest.java`
- Create: `dinghai-productiq/.gitignore`, `dinghai-productiq/knowledge/.gitkeep`, `dinghai-productiq/docs/.gitkeep`, `dinghai-productiq/skills/.gitkeep`, `dinghai-productiq/plugins/.gitkeep`

- [ ] **Step 1: Write the failing bootstrap test**

  Add a Spring context test asserting `KnowledgeAssistantApplication` loads and the configured application name is `knowledge-assistant`.

- [ ] **Step 2: Run the focused test to verify RED**

  Run `mvn -f dinghai-productiq/backend/pom.xml -Dtest=KnowledgeAssistantApplicationTest test`; expected failure is missing application class/project files.

- [ ] **Step 3: Implement the minimal backend and layout**

  Add the standalone POM with group/artifact coordinates, Java 21, Spring Boot web/actuator/test dependencies; add the application class, YAML defaults, ignored runtime data directories and README build instructions.

- [ ] **Step 4: Run focused and broader verification**

  Run the focused test, then `mvn -f dinghai-productiq/backend/pom.xml test` and `mvn -pl dinghai-productiq/backend -am test` (the latter may report the backend is intentionally outside root modules; record the exact result).

- [ ] **Step 5: Commit**

  Run `git ls-files --others --exclude-standard`, inspect staged paths, then commit the layout and planning artifacts with `Refs: OpenSpec productiq-knowledge-assistant task 1.1`.

**Handoff evidence:** backend test output, `pom.xml` coordinates, root package path, and `git diff --check`.

### Task 2: 知识协议与离线 Skills

**Owned module:** `dinghai-productiq/skills` and backend `knowledge` validation package. No SQLite transaction or HTTP API in this task.

**Files:**
- Create: `dinghai-productiq/backend/src/main/java/io/github/lvdaxianer/projectiq/knowledge/domain/KnowledgePackage.java` and immutable nested records
- Create: `dinghai-productiq/backend/src/main/java/io/github/lvdaxianer/projectiq/knowledge/application/KnowledgePackageValidator.java`
- Create: `dinghai-productiq/backend/src/test/java/io/github/lvdaxianer/projectiq/knowledge/application/KnowledgePackageValidatorTest.java`
- Create: `dinghai-productiq/skills/knowledge-loop-protocol/SKILL.md`
- Create: `dinghai-productiq/skills/knowledge-producer/SKILL.md`
- Create: `dinghai-productiq/skills/knowledge-checker/SKILL.md`
- Create: `dinghai-productiq/skills/knowledge-orchestrator/SKILL.md`
- Create: `dinghai-productiq/skills/references/knowledge-package.schema.json`, `checker-verdict.schema.json`
- Create: `dinghai-productiq/skills/tests/knowledge-package-fixture.json`, `invalid-evidence-fixture.json`

- [ ] **Step 1: Write RED validator tests**

  Cover valid package acceptance, source hash mismatch, invalid IDs/types, missing section concepts, missing concept evidence, unsupported relation, and non-verbatim quote. Tests must assert stable error codes and candidate paths.

- [ ] **Step 2: Run `mvn -f dinghai-productiq/backend/pom.xml -Dtest=KnowledgePackageValidatorTest test` and confirm RED**

- [ ] **Step 3: Implement deterministic validation**

  Parse the schema-shaped records with Jackson, calculate SHA-256, enforce allowlists and cross references, validate source anchors and quote containment, and return a complete immutable validation result.

- [ ] **Step 4: Add executable Skill instructions and schema fixtures**

  Keep producer/checker/orchestrator role boundaries, five repair rounds, dictionary-order processing, trusted-pair publication and explicit remove behavior in the files under `skills/`.

- [ ] **Step 5: Verify and commit**

  Run validator tests, JSON parse checks, `git diff --check`, and commit with OpenSpec task 1.2 footer.

**Handoff evidence:** validator test count and failure-code assertions; all four Skill files and both schemas present.

### Task 3: SQLite 事实模型

**Owned module:** backend `knowledge/repository` and SQLite schema. No Qdrant or controller code.

**Files:**
- Modify: `dinghai-productiq/backend/pom.xml` (SQLite/JDBC dependencies)
- Create: `dinghai-productiq/backend/src/main/resources/db/migration/V1__knowledge_schema.sql`
- Create: `dinghai-productiq/backend/src/main/java/io/github/lvdaxianer/projectiq/knowledge/repository/KnowledgeStore.java`
- Create: JDBC implementation and row mappers under `knowledge/infrastructure/sqlite/`
- Create: `dinghai-productiq/backend/src/test/java/io/github/lvdaxianer/projectiq/knowledge/repository/SqliteKnowledgeStoreTest.java`

- [ ] **Step 1: Write RED repository tests** for schema creation, document/section/concept/relation/evidence persistence, uniqueness, and source-scoped replacement.
- [ ] **Step 2: Run the focused test and confirm missing schema/repository failure.**
- [ ] **Step 3: Add migrations, records, transaction methods, and prepared SQL.** Use foreign keys and indexes; relation deletion must occur only when no evidence remains.
- [ ] **Step 4: Run focused tests and `mvn -f dinghai-productiq/backend/pom.xml test`.**
- [ ] **Step 5: Commit** with OpenSpec task 2.1 footer.

**Handoff evidence:** isolated temporary SQLite database test output and schema inspection.

### Task 4: 导入、替换、删除与索引任务

**Owned module:** backend `knowledge/application` import use cases and admin API. No retrieval/chat implementation.

**Files:**
- Create: `knowledge/application/KnowledgeImportService.java`, `KnowledgeSourceService.java`
- Create: `knowledge/api/KnowledgeImportController.java`
- Create: DTOs/exceptions and `knowledge/infrastructure/filesystem/TrustedKnowledgeDirectory.java`
- Create: `knowledge/infrastructure/index/VectorIndexJobStore.java`
- Create: tests for path security, idempotency, replacement, explicit delete and failure preservation.

- [ ] **Step 1: Write RED service/controller tests** for invalid `sourceKey`, missing pair, hash/schema failure, repeated completed import, transactional replacement, explicit deletion and no implicit scan deletion.
- [ ] **Step 2: Run focused tests and confirm RED.**
- [ ] **Step 3: Implement restricted path resolution, validation-before-transaction, source-scoped transaction, idempotency result, and post-commit index job creation.** Protect admin endpoints with an internal service key.
- [ ] **Step 4: Run focused tests, backend test suite, and an HTTP MockMvc contract check.**
- [ ] **Step 5: Commit** with OpenSpec task 2.2 footer.

**Handoff evidence:** status mapping (`IMPORTED`, `IMPORTED_WITH_INDEX_PENDING`, `IMPORT_FAILED`) and SQL row counts before/after replacement.

### Task 5: 向量适配与检索本体

**Owned module:** backend `retrieval` and `integration/vector`. No Answerer/SSE controller.

**Files:**
- Create: `retrieval/domain/RetrievalCandidate.java`, `OntologyExpansion.java`
- Create: `retrieval/application/RetrievalService.java`, `OntologyReasoner.java`
- Create: `integration/embedding/EmbeddingClient.java`, `integration/vector/VectorStore.java`
- Create: in-memory fakes and Qdrant/embedding HTTP adapters
- Create: retrieval and reasoner tests, including stale hash, hop/document/concept limits and overrides.

- [ ] **Step 1: Write RED tests** for Top-K hydration, stale candidate rejection, strong relation traversal, weak relation exclusion, four-hop/eight-concept/three-document caps and override filtering.
- [ ] **Step 2: Run focused tests and confirm RED.**
- [ ] **Step 3: Implement deterministic retrieval/reasoning and bounded HTTP adapters.** Use configurable Top-K 20~30 and never return authoritative正文 from vector payload.
- [ ] **Step 4: Verify focused tests, adapter contract tests and backend build.**
- [ ] **Step 5: Commit** with OpenSpec task 3.1/3.2 footer (split if needed).

**Handoff evidence:** candidate metadata includes hop and source hash; reasoner tests prove hard limits.

### Task 6: Reranker、Answerer 与 SSE

**Owned module:** backend `chat`, `integration/model`, observability configuration. No plugin files.

**Files:**
- Create: `chat/application/ChatService.java`, `Reranker.java`, `Answerer.java`
- Create: `chat/api/ChatController.java`, SSE event DTOs
- Create: model gateway clients/fakes and bounded executor/MDC utilities
- Create: chat service/controller/logging tests.

- [ ] **Step 1: Write RED tests** for candidate ID filtering, six-section/three-document/8,000-character limits, no-knowledge/no-relevant/knowledge-updating refusal, ordered SSE events, disconnect cancellation and sensitive log absence.
- [ ] **Step 2: Run focused tests and confirm RED.**
- [ ] **Step 3: Implement structured rerank JSON parsing, evidence-only prompts, refusal branches, SSE mapping, requestId/traceId context, bounded model calls and safe structured logs.**
- [ ] **Step 4: Run focused tests, full backend tests, and `mvn -q -DskipTests package`.**
- [ ] **Step 5: Commit** with OpenSpec task 4.1/4.2 footer (split if needed).

**Handoff evidence:** MockMvc event stream snapshots and assertions that Answerer is not invoked for refusal states.

### Task 7: Chrome 插件与运行资料

**Owned module:** `dinghai-productiq/plugins/chrome-extension`, Docker and docs. No backend behavior changes except documented CORS/config.

**Files:**
- Create: `plugins/chrome-extension/manifest.json`, `popup.html`, `popup.js`, `popup.css`, `service-worker.js`, `options.html`, `options.js`, `package.json`, tests
- Create: `dinghai-productiq/docker-compose.yml`, `.env.example`
- Create: `docs/运行与部署.md`, `docs/验收集.md`, deterministic fixture runner and sample Markdown/knowledge pairs

- [ ] **Step 1: Write RED JavaScript tests** for configuration, SSE parsing, cancellation, source rendering and error/empty/loading states.
- [ ] **Step 2: Run `npm test` and confirm RED.**
- [ ] **Step 3: Implement Manifest V3 popup/options/service worker with AbortController and no model key storage.**
- [ ] **Step 4: Add Compose, environment documentation, collection rebuild/rollback instructions and 30~50-question acceptance fixture format.**
- [ ] **Step 5: Run plugin tests, JSON/lint checks, and Docker Compose config validation.** Commit with OpenSpec task 5.1/5.2 footer.

**Handoff evidence:** plugin test output, `docker compose config`, and acceptance fixture report.

### Task 8: 最终集成与归档

**Files:** all ProductIQ files plus OpenSpec task markers.

- [ ] **Step 1:** Run `openspec validate productiq-knowledge-assistant --strict` and the full backend/plugin/acceptance command set.
- [ ] **Step 2:** Perform plan-implementation consistency audit against every requirement/scenario and record any fixes.
- [ ] **Step 3:** Apply canonical `code-review-spec` to the full diff, fix all actionable findings, then rerun verification.
- [ ] **Step 4:** Mark exactly one OpenSpec task complete after each task gate and commit atomically; run `git ls-files --others --exclude-standard` before each commit.
- [ ] **Step 5:** Archive the completed OpenSpec change, merge all task branches into `main`, verify ancestry and leave the integration checkout clean except for preserved unrelated tool artifacts.

**Handoff evidence:** final verification report, commit SHAs, archive path, and `git status --short`.
