# Tasks

## 1. Core contracts and module scaffolding

- [x] 1.1 Add the `dinghai-dsl-engine` aggregator and core/SPI modules, define immutable `DslRequest`, `DslContext`, `ExecutionPlan`, `QueryResult`, diagnostics, `DslAdapter`, `DataAdapter`, compiler, executor, policy, cache, and audit contracts, and verify the new modules compile with focused contract tests.
- [x] 1.2 Implement exact, versioned immutable DSL/Data Adapter registries with duplicate and unknown-type failures, and verify registry unit tests cover selection, version rejection, duplicate registration, and no fallback.

## 2. Reference relational DSL

- [x] 2.1 Add the `relational-query` v1 JSON request model and typed AST for source, projection, predicate, join, grouping, ordering, aggregation, parameters, and limit, and verify valid/invalid shape tests.
- [x] 2.2 Implement relational semantic validation, identifier/policy resolution, safe normalization, complexity checks, and immutable relational execution-plan generation, and verify raw SQL, invalid identifiers, unsafe joins, and plan-boundary tests.

## 3. MySQL and Doris-compatible backend

- [x] 3.1 Add provider-scoped MySQL JDBC dependencies and implement the MySQL/Doris-common capability model and parameterized relational compiler, and verify SQL shape, identifier quoting, capability rejection, and parameter-binding tests.
- [x] 3.2 Implement MySQL DataSource execution, timeout/row bounds, JDBC parameter binding, stable output aliases, logical type codecs, and unified result mapping, and verify mock-JDBC tests plus optional MySQL/Doris-protocol integration tests when services are available.

## 4. PostgreSQL backend

- [x] 4.1 Add provider-scoped PostgreSQL JDBC dependencies and implement the PostgreSQL relational compiler and capability declarations, and verify PostgreSQL-specific quoting, binding, and unsupported-feature tests.
- [x] 4.2 Implement PostgreSQL DataSource execution and result mapping without MySQL fallback, and verify adapter selection, mock-JDBC execution, mapping, timeout, and explicit missing-adapter failure tests.

## 5. Spring Boot bootstrap

- [x] 5.1 Implement configuration properties, provider-scoped auto-configuration, DataSource wiring, and startup assembly for enabled DSL and Data Adapters, and verify context loading with valid configuration.
- [x] 5.2 Add fail-fast validation and registry freezing for missing DataSource/driver, unknown adapter, duplicate adapter, missing DSL adapter, and invalid capability configuration, and verify each failure before readiness.

## 6. Runtime controls and observability

- [x] 6.1 Implement bounded AST/plan/compiled-command caches with versioned keys, cache failure bypass, and no default result caching, and verify hit, miss, invalidation, bounds, and failure behavior.
- [x] 6.2 Implement execution limits, concurrency/timeout controls, metrics, trace context, safe diagnostic logging, and audit sink events without sensitive values, and verify limit rejection, timeout, redaction, and telemetry tests.

## 7. Documentation and dependency hygiene

- [x] 7.1 Document module coordinates, DSL envelope, adapter extension points, MySQL/Doris JDBC usage, PostgreSQL usage, configuration, limitations, and enterprise integration boundaries in README files, and verify every documented sample is internally consistent.
- [x] 7.2 Run project-hygiene checks for new POMs, dependencies, environment placeholders, ignored artifacts, line endings, and secret leakage, and verify `git status`, `git ls-files`, and baseline checks are clean.

## 8. Integrated verification

- [x] 8.1 Run focused tests for every new module and the full Maven reactor, verify no existing module regression, and record skipped external-service tests explicitly. Focused DSL reactor: 54 tests, 0 failures, 0 errors, 0 skipped. Full Maven reactor: 557 tests, 0 failures, 0 errors, 43 skipped; the skipped cases are existing external-service/integration scenarios and no DSL test is skipped.
- [x] 8.2 Run DSL/parser/compiler and JDBC adapter benchmarks or bounded smoke tests, review the complete diff against all four capability specs, and verify OpenSpec validation passes in strict mode. Bounded smoke coverage is provided by the parser/compiler/JDBC mock tests and runtime limit tests; `git diff --check` and `openspec validate add-dsl-engine --strict` pass.

## Workflow follow-up

- Archive the completed OpenSpec change after implementation, review, verification, and per-task audits pass.
- Verify the final `main` integration branch contains every completed task and remains clean.
