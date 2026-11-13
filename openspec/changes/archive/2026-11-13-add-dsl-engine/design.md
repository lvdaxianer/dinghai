# Design

## Context

See `proposal.md` and the four capability specs for the required behavior. The current repository is a Java 21/Spring Boot component library with independent modules and provider-scoped starter patterns, but it has no DSL compiler or database query runtime. The public repository must remain domain-neutral and must not depend on enterprise identity, authorization, MetricView, or application-specific schema rules.

## Goals / Non-Goals

**Goals:**

- Separate DSL front-end compilation from target-system compilation and execution.
- Provide stable public contracts for arbitrary versioned DSL adapters and backend adapters.
- Implement `relational-query` v1 as a reference DSL without making relational querying the core abstraction.
- Execute only structured, validated plans through parameterized JDBC commands.
- Reuse one MySQL adapter for MySQL and Doris MySQL-protocol endpoints using a common capability profile.
- Assemble adapters once at Spring Boot startup, fail fast on invalid configuration, and freeze registries.
- Keep cache, limits, metrics, trace, audit, and diagnostic hooks bounded and provider-neutral.

**Non-Goals:**

- Implementing ontology, metric, enterprise, or other business-specific DSL semantics.
- Providing HTTP controllers, authentication, service-token allowlists, tenant authorization, or enterprise schema policy.
- Accepting or executing raw SQL, SQL fragments, DDL, or DML.
- Implementing a separate Doris adapter or exposing Doris-only syntax.
- Default query-result caching or cross-database joins.

## Decisions

### 1. Use a layered compiler pipeline

The public flow is:

```text
DslRequest
  -> DslAdapterRegistry
  -> DSL parser and typed AST
  -> semantic analysis and normalization
  -> backend-neutral ExecutionPlan
  -> DataAdapterRegistry
  -> backend compiler
  -> CompiledCommand
  -> executor
  -> QueryResult
```

`ExecutionPlan` is the extensible core contract. Relational DSLs produce a relational plan subtype; the core does not make `QueryPlan` the return type of every DSL. A backend compiler converts a validated plan to a target command, while an executor owns connections and execution. This avoids a direct DSL-to-SQL matrix and allows future non-SQL backends to consume other plan types.

Alternative considered: letting each DSL adapter generate SQL directly. Rejected because it couples DSL semantics to one database, prevents capability negotiation, and creates an unreviewable raw-SQL escape path.

### 2. Use provider-scoped Maven modules

The module layout is:

```text
dinghai-dsl-engine/
  core/
  spi/
  relational/
  mysql/
  postgresql/
  spring-boot-starter/
```

`core` has no JDBC vendor dependency. `mysql` owns MySQL JDBC integration and `postgresql` owns PostgreSQL JDBC integration. The starter depends on the selected provider modules rather than forcing every driver into every application. This follows the existing file-storage provider-scoped dependency pattern and prevents a public base module from acquiring unnecessary vendor closures.

Alternative considered: one monolithic starter containing both drivers. Rejected because it increases transitive dependencies and weakens the public/provider boundary.

### 3. Make `relational-query` v1 a reference adapter

The first concrete DSL uses a strict JSON envelope with `dslType`, `version`, `payload`, and parameter references. It produces a typed relational plan containing sources, projections, predicates, joins, grouping, ordering, and limits. Identifiers are resolved through a schema/policy SPI supplied by the host application; values are represented as typed parameter bindings. The parser accepts no SQL strings or arbitrary expressions.

The relational module owns grammar, AST, semantic rules, safe normalization, and plan construction. It does not own user/domain authorization or the physical schema. This gives the engine an executable reference while keeping the core domain-neutral.

### 4. Keep registries immutable and exact

Spring collects `DslAdapter`, `DataAdapter`, compiler, capability, and policy beans. A bootstrap component validates duplicate type/version claims, configured enablement, default adapter availability, required DataSource and driver capabilities, then constructs immutable registries. Runtime requests resolve only against these registries. No request can create an adapter or replace the configured backend.

Alternative considered: a mutable plugin registry. Rejected because runtime mutation makes behavior and security depend on request timing and complicates cache invalidation.

### 5. Separate dialect compilation from JDBC execution

`MySqlDataAdapter` combines a common-capability MySQL compiler with a JDBC executor. The compiler returns a `CompiledCommand` containing SQL text, typed parameter bindings, output column descriptors, and a capability/profile identifier. The executor obtains a configured pooled `DataSource`, binds values using `PreparedStatement`, enforces timeout and row bounds, and maps `ResultSet` values through precomputed column codecs.

The default MySQL capability profile is the intersection of MySQL and Doris MySQL-protocol capabilities. A Doris endpoint therefore uses the MySQL adapter without a Doris-specific type or compiler. PostgreSQL has its own compiler and adapter and cannot fall back to MySQL.

Alternative considered: using `JdbcTemplate` as the public abstraction. Rejected as the core contract because it couples plan compilation and execution to Spring; the core remains JDBC/DataSource-oriented, while the starter can integrate Spring facilities.

### 6. Use unified result metadata and stable aliases

Compilers assign safe, deterministic output labels and return `ColumnMapping` descriptors. The mapper uses labels and logical types rather than per-row reflection or positional assumptions. `QueryResult` contains column descriptors, rows, row count, and truncation state. Mapping to enterprise DTOs remains outside the public adapter.

### 7. Cache only reusable compilation artifacts by default

The runtime provides bounded cache hooks for parsed ASTs, normalized plans, and compiled command templates. Cache keys include DSL/version, adapter/compiler version, capability profile, schema version, and policy version. Parameter values, credentials, tokens, and query results are not stored by default. Cache failures bypass the cache and repeat bounded compilation; backend failures fail closed.

### 8. Treat limits and observability as part of the execution boundary

Before acquiring a database connection, the runtime checks input size, AST/plan depth, node count, join count, row limit, concurrency budget, and timeout. Metrics cover each pipeline stage, cache hits, pool wait, execution latency, row count, mapping failures, and timeouts. Logs contain only controlled summaries and trace identifiers; an audit sink receives plan identity and outcome without sensitive values. The public engine exposes hooks, while enterprise applications decide authorization context and audit storage.

## Risks / Trade-offs

- **[Risk]** A future DSL may not produce a relational plan. → Keep `ExecutionPlan` generic and make relational planning one plan family rather than the core interface.
- **[Risk]** MySQL and Doris diverge despite sharing a protocol. → Use an explicit common capability profile and reject unsupported features instead of vendor-specific fallback.
- **[Risk]** Aggressive plan rewrites change NULL or outer-join semantics. → Start with deterministic normalization and safe local rewrites; defer join reordering and predicate pushdown.
- **[Risk]** Schema and permission rules differ by enterprise application. → Define policy/schema interfaces and include their versions in plan and cache identity; keep implementations outside this repository.
- **[Risk]** JDBC result types vary by driver and database. → Map through logical type codecs and test representative MySQL, Doris-protocol, and PostgreSQL values.
- **[Risk]** New provider dependencies can pollute existing artifacts. → Keep provider modules and starters separate, inspect dependency trees, and add provider-scoped tests.
- **[Risk]** High-cardinality or expensive requests can exhaust pools. → Reject over-complex plans before connection acquisition and enforce bounded pool wait, query timeout, row limit, and concurrency controls.

## Migration Plan

1. Add the new modules and provider dependencies without changing existing modules or public APIs.
2. Introduce core contracts and unit-test them independently.
3. Add the reference relational DSL and its security/semantic tests.
4. Add MySQL/Doris-common and PostgreSQL adapters with mock JDBC tests and optional container integration tests.
5. Add the Spring Boot starter and fail-fast bootstrap tests for missing/unknown/duplicate configuration.
6. Add runtime controls, metrics, cache hooks, documentation, and provider dependency checks.
7. Applications opt in by adding the desired provider starter and configuring a server-owned DataSource and default adapter. Existing dinghai modules remain unaffected.
8. Rollback is dependency removal and module removal; no existing database schema or existing module behavior is changed by this feature.
