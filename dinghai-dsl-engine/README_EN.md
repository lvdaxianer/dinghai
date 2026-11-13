# dinghai-dsl-engine

`dinghai-dsl-engine` is a DSL parsing and compilation engine, not a raw SQL gateway:

```text
DslRequest -> DslAdapterRegistry -> parser/AST/semantic validation
  -> ExecutionPlan -> DataAdapterRegistry -> backend compiler
  -> parameterized command -> DataSource/JDBC -> QueryResult
```

Clients submit only registered structured DSLs. They cannot submit raw SQL, SQL fragments, database credentials, or an adapter name.

## Artifacts

| Artifact | Purpose |
| --- | --- |
| `dinghai-dsl-engine-core` | Immutable request, plan, result, and diagnostics |
| `dinghai-dsl-engine-spi` | DSL/data adapter, compiler, cache, execution, metrics, and audit extension points |
| `dinghai-dsl-engine-relational` | `relational-query` v1 parser, typed AST, whitelist, and plan builder |
| `dinghai-dsl-engine-mysql` | MySQL/Doris MySQL-protocol common compiler and JDBC execution |
| `dinghai-dsl-engine-postgresql` | PostgreSQL compiler and JDBC execution |
| `dinghai-dsl-engine-spring-boot-starter` | Startup configuration, DataSource, registry, and engine assembly |

Provider artifacts are separate; enterprise code must not be copied into the public core.

Applications must explicitly choose a provider artifact. Provider dependencies in the Spring Boot starter are optional, so applications do not receive both database drivers automatically:

```xml
<dependency>
  <groupId>io.github.lvdaxianer</groupId>
  <artifactId>dinghai-dsl-engine-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>io.github.lvdaxianer</groupId>
  <artifactId>dinghai-dsl-engine-mysql</artifactId>
  <version>1.0.0</version>
</dependency>
```

For PostgreSQL, replace the last artifact with
`dinghai-dsl-engine-postgresql` and set the default adapter to
`postgresql`.

## Spring Boot configuration

```yaml
dinghai:
  data-query:
    default-data-adapter: mysql # mysql or postgresql
    dsl-adapters:
      relational-query: enabled
    max-request-bytes: 1048576
    max-concurrency: 32
    timeout: 30s
    max-rows: 10000

spring:
  datasource:
    url: jdbc:mysql://db.example.internal:3306/analytics
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Doris on its MySQL-protocol port reuses the `mysql` adapter and only uses the common supported SQL subset. PostgreSQL never silently falls back to MySQL. The host application must provide `RelationalSchemaPolicy` for table/column whitelisting.

Startup fails before readiness when the selected adapter, DataSource, DSL adapter, or capability declaration is missing or duplicated.

## Logging and enterprise boundary

The default safe logger records only trace ID, plan digest, adapter, outcome code, duration, and row count. It never records passwords, tokens, raw SQL, parameter values, or complete result sets. Hosts may inject `QueryAuditSink` and `QueryTelemetry`.

The public repository does not implement controllers, login, service-token allowlists, tenant authorization, or enterprise identity. An enterprise gateway may use `Authorization: Bearer ${SERVICE_TOKEN}` and restrict paths such as `/api/data-query/metricView/.*`; tokens must be mapped server-side and stored in a secret manager. Never commit real tokens, passwords, or production configuration.

The first DSL is `relational-query` v1. It rejects raw SQL, DDL/DML, cross-data-source joins, and default result caching. Future DSLs implement `DslAdapter`, and future backends implement `DataAdapter` without changing the generic core.
