# dinghai-thread-pool

English | [中文](README.md)

Managed business thread pools with a single-instance observability governor for Spring Boot 3. Business code obtains managed executors from `ThreadPoolGovernor`, and every task is bounded by a global worker permit during actual execution. The child controller (Starter) samples pool snapshots into a local SQLite database and exposes read-only collection endpoints; the governor (governance-server) polls each application's metric pages on a schedule and persists instances, cursors, collection gaps, and alerts.

Requirements: Java 21+, Spring Boot 3.x.

## Modules

| Module | Description |
| --- | --- |
| `dinghai-thread-pool-core` | Zero-dependency core: `ThreadPoolGovernor` implements a global worker permit with a fair semaphore, creates managed thread pools, and enforces the three overload policies |
| `dinghai-thread-pool-governance-api` | Protocol models shared between child controller and governor (metric pages, pool snapshots, instance generations); no dependencies |
| `dinghai-thread-pool-spring-boot-starter` | Child controller: auto-configures the governor, MDC task decorator, SQLite metric sampling and storage, read-only collection endpoints, Micrometer metrics, and a health check |
| `dinghai-thread-pool-governance-server` | Governor: standalone Spring Boot application with a single-instance SQLite control plane that polls child metrics and manages collection gaps and alerts |
| `governance-ui` | Vue 3 + Element Plus analysis console; reads only governor REST APIs |

## Quick Start

Add the child controller Starter:

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-thread-pool-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Minimal configuration (auto-configuration is off by default; you must enable it explicitly and declare capacities):

```yaml
dinghai:
  thread-pool:
    enabled: true
    process-thread-capacity: 64
    managed-worker-budget: 16
    metrics:
      sqlite-path: /var/lib/app/dinghai/thread-pool-metrics.db
    pools:
      file-storage-web:
        minimum-size: 2
        maximum-size: 8
        weight: 10
        priority: HIGH
        reliability: FAIL_FAST
        queue-capacity: 64
```

All capacity and budget validation runs at startup; invalid values fail the application startup with no silent fallback. Once configured, obtain an `ExecutorService` from `ThreadPoolGovernor.executor(name)`; do not hold on to the raw `ThreadPoolExecutor`.

## Configuration

Configuration prefix is `dinghai.thread-pool`. All validation is code logic executed at startup, not Bean Validation annotations.

### Top-level parameters

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | Whether to configure the child controller. Off by default; must be explicitly set to `true` |
| `process-thread-capacity` | `int` | none (required) | The process thread capacity explicitly declared by the application. Startup fails if missing, less than 1, or less than `managed-worker-budget`. Reserve budget for container, JVM, scheduler, and third-party library threads |
| `managed-worker-budget` | `int` | `1` | Total number of global worker permits created by the governor; constrains only workers managed by `ThreadPoolGovernor`. Must be positive and no greater than `process-thread-capacity` |
| `endpoint-enabled` | `boolean` | `false` | Whether to register the `/actuator/dinghai-thread-pool/*` read-only collection endpoints. When enabled, configure TLS and service identity authentication for this path in the gateway or Spring Security |

### pools (managed pools)

At least one pool must be declared under `pools`; startup fails otherwise. Pool names must be lowercase kebab-case (`[a-z][a-z0-9-]{0,49}`); worker threads are named `<pool>-<threadId>`.

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `pools.<name>.minimum-size` | `int` | `1` | Guaranteed minimum threads of the pool; must be non-negative and no greater than `maximum-size`. Startup fails when the sum of all pools' minimums exceeds `managed-worker-budget` |
| `pools.<name>.maximum-size` | `int` | `1` | Desired maximum threads; must be positive and no greater than `managed-worker-budget`. At registration the elastic quota is redistributed by weight, so the actual maximum is `min(configured, minimum + share of the remaining budget)` |
| `pools.<name>.weight` | `int` | `1` | Weight for elastic quota distribution; must be positive. After reserving each pool's minimum, the remaining budget is distributed proportionally to weight |
| `pools.<name>.priority` | `HIGH` / `MEDIUM` / `LOW` | none (required) | Priority label; appears only in snapshots and metrics and does not affect runtime scheduling. Startup fails if missing or invalid |
| `pools.<name>.reliability` | `FAIL_FAST` / `PERSIST_FOR_RETRY` / `BEST_EFFORT_DROP` | none (required) | Overload policy (see below). Startup fails if missing or invalid |
| `pools.<name>.queue-capacity` | `int` | `100` | Waiting queue capacity; must be positive |
| `pools.<name>.owner` | `String` | none | Business owner attribution; required when `reliability=BEST_EFFORT_DROP`, startup fails if missing |

### metrics (local metric storage)

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `metrics.sqlite-path` | `Path` | `dinghai-thread-pool.db` | Local metric SQLite file path. **Must reside on a persistent volume**; it replaces cursor offset files and in-memory buffers, but does not replace Prometheus for long-term metric storage |
| `metrics.retention` | `Duration` | `24h` | Metric retention period; data older than this is purged on every sampling run |
| `metrics.sample-interval` | `Duration` | `30s` | Pool snapshot sampling interval (fixed-delay) |
| `metrics.page-limit-max` | `int` | `1000` | Maximum page size of the collection endpoint; must be positive. The endpoint `limit` parameter is clamped to `[1, this value]` |

Full configuration example:

```yaml
dinghai:
  thread-pool:
    enabled: true
    process-thread-capacity: 64
    managed-worker-budget: 16
    endpoint-enabled: false
    metrics:
      sqlite-path: /var/lib/app/dinghai/thread-pool-metrics.db
      retention: 24h
      sample-interval: 30s
      page-limit-max: 1000
    pools:
      file-storage-web:
        minimum-size: 2
        maximum-size: 8
        weight: 10
        priority: HIGH
        reliability: FAIL_FAST
        queue-capacity: 64
      report-export:
        minimum-size: 1
        maximum-size: 4
        weight: 5
        priority: LOW
        reliability: PERSIST_FOR_RETRY
        queue-capacity: 32
```

## Usage

Business code uses managed pools through `ThreadPoolGovernor`:

```java
ExecutorService executor = threadPoolGovernor.executor("report-export");
executor.submit(() -> exportReport(request));
```

A task acquires a global worker permit (fair semaphore) only **during actual execution**; the time spent waiting for a permit is recorded in metrics, and interruption while waiting throws `IllegalStateException`. MDC is snapshotted at submission, installed before execution, and restored afterwards by `dinghaiThreadPoolTaskDecorator` (`MdcTaskDecorator`, overridable by a same-named host Bean).

### Overload policies

When the queue is full and the pool cannot grow, the pool's `reliability` policy applies; the rejection is counted in metrics first:

- **`FAIL_FAST`**: immediately throws the stable `ThreadPoolOverloadException` (extends `RejectedExecutionException`, fixed message `thread pool is overloaded: <pool>`, never exposing internal state). No extra requirements.
- **`PERSIST_FOR_RETRY`**: persists the rejected task via the host-provided `ThreadPoolRetryPersistence` Bean's `persist(poolName, task)`. Startup fails when no such Bean exists; exceptions thrown by the persistence itself are wrapped in `IllegalStateException` and rethrown. The port contract requires failures to be propagated to the caller.
- **`BEST_EFFORT_DROP`**: drops the task and calls `ThreadPoolDropNotifier.dropped(poolName, owner)` to deliver an attributable drop notification. `owner` is required; the default notifier is a no-op and the host may provide one as needed.

### Integration with dinghai-file-storage

When an application using the file-storage Starter declares a pool named `file-storage-web`, the `fileStorageWebTaskExecutor` (keeping its original Spring Bean name and `ThreadPoolTaskExecutor` type) decorates tasks as `governor.decorate("file-storage-web", task)`, reusing the global permit and statistics instead of growing beyond the process thread budget.

## Observability Endpoints

Endpoints are off by default and registered when `endpoint-enabled` is set (the prefix is fixed at `/actuator/dinghai-thread-pool`). Every request first passes the host-provided `ThreadPoolMetricsAccessPolicy` Bean (denied by default, returning 403). TLS and service identity authentication for this path must be configured in the gateway or Spring Security.

| Endpoint | Description |
| --- | --- |
| `GET /metrics?cursor=&limit=` | Pages through pool snapshot metrics stored in local SQLite, returning `nextCursor`, `hasMore`, `oldestCursor`, and the metric array. The cursor is an opaque server-side HMAC-signed value (the key lives only in the local SQLite database); an empty cursor reads from the beginning |
| `GET /snapshot` | Returns an instant snapshot of all managed pools (active count, pool size, queue, actual maximum, submitted/completed/rejected/failed counts, wait and execution times, priority and reliability labels), used to recover from expired cursors |
| `GET /identity` | Returns the stable generation bound to the local SQLite volume. Restarts on the same volume keep the generation; rebuilding or replacing the SQLite volume generates a new one |

When a cursor has expired (the requested position predates the oldest retained data), the endpoint returns **HTTP 409** with body `{"code":"CURSOR_EXPIRED","oldestCursor":"..."}`; callers should recover via `snapshot` and resume from `oldestCursor`. Forged or malformed cursors are treated as parameter errors.

The child controller exports Micrometer metrics (tag `pool=<name>`, all Gauges): `dinghai.thread.pool.active`, `dinghai.thread.pool.queue.size`, `dinghai.thread.pool.completed`, `dinghai.thread.pool.rejected`. It also registers the `dinghaiThreadPoolMetricStore` health check, which probes SQLite readability with a minimal paged read.

## Governance Server

`dinghai-thread-pool-governance-server` is a standalone Spring Boot application that stores instances, versions, cursors, collection gaps, alerts, and collected thread-pool history in a single-instance SQLite database and **provides no high availability**. Its history is retained for 30 days by default. It never stores access tokens, mTLS private keys, task parameters, or task payloads.

### Configuration

Configuration prefix is `dinghai.thread-pool.governance`. Web parameters such as the port use standard Spring Boot configuration.

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `sqlite-path` | `Path` | `dinghai-governance.db` | Governor SQLite file path; startup fails if missing. **Must be mounted on a persistent volume** |
| `poll-interval` | `Duration` | `30s` | Polling interval (fixed-delay); must be positive |
| `request-timeout` | `Duration` | `5s` | Connect and read timeout for child controller requests; must be positive |
| `retention` | `Duration` | `30d` | Governor thread-pool history retention; must be positive and is purged daily |
| `page-limit` | `int` | `1000` | Page size per fetch; must be positive |
| `api-token` | `String` | none | When non-empty, child requests carry `Authorization: Bearer <token>`. The token is injected by deployment and can be rotated |

### Management API

The management API denies access by default. The following endpoints are enabled once the host provides a `GovernanceAccessPolicy` Bean (default `() -> false`):

| Endpoint | Description |
| --- | --- |
| `POST /governance/instances` | Registers or updates a child instance with body `{instanceId, baseUrl, environment, version}`. `baseUrl` must be an absolute HTTPS URL, otherwise 400; a changed `version` means the child's local SQLite may have been rebuilt, so the governor clears the old cursor and resumes collection from the new incremental window |
| `GET /governance/instances` | Lists registered instances |
| `GET /governance/gaps?instanceId=` | Lists collection gaps (requested cursor, oldest cursor, detection time, recovery status OPEN/RECOVERED) |
| `GET /governance/alerts` | Lists alert states; alert keys are `collection-gap:<instanceId>` and `collection-failure:<instanceId>` |
| `GET /governance/metrics?instanceId=&poolName=&from=&to=` | Lists persisted governor SQLite samples; filters are query parameters and `from`/`to` are inclusive epoch milliseconds |

### Vue Console

The frontend project is located at `governance-ui`. Vite proxies `/governance` to a local governor during development; production should reverse-proxy it to the same governor address:

```bash
cd dinghai-thread-pool/governance-ui
npm install
npm run dev
```

It presents instance/pool filters, active and maximum threads, queue depth, cumulative completion/rejection/failure, wait and execution trends, top pools, alerts, collection gaps, and recent sample details. It reads only governor APIs; the governor pulls children and persists SQLite history.

### Polling Collection and Recovery

The governor polls every registered instance on a schedule (failures of one instance are isolated from the others):

1. Before polling, it reads the child's `identity` for a generation check; when the generation differs from the record, the old cursor is deleted and the version updated. Older child controllers that have not been upgraded return 404, in which case the governor skips the generation check in compatibility mode and continues the original metrics/snapshot collection;
2. Metric pages are fetched from the confirmed cursor, batch-persisted to governor SQLite, published to real-time aggregation, and only then commit `nextCursor`; positive child `metricId` values are deduplicated by `(instanceId, sourceMetricId)`, so retries do not duplicate history;
3. When a cursor has expired (the child returns 409), the governor records a collection gap, fires the `collection-gap` alert, restores observability from the child snapshot, commits `oldestCursor`, marks the gap RECOVERED, and resolves the alert;
4. A whole-instance collection failure fires the `collection-failure` alert and increments the failure counter; success resolves it.

To onboard a child controller to the governor: turn on `enabled` and `endpoint-enabled`, mount `metrics.sqlite-path` on a persistent volume, provide a `ThreadPoolMetricsAccessPolicy` Bean that admits the governor (for example by validating the Bearer token), serve HTTPS/TLS via the gateway or runtime environment, and let the deployer register the instance via `POST /governance/instances`. Child controllers never register themselves; collection always flows as **the governor pulling from the children**.

## Design Notes

- **Explicit over implicit**: the child controller is not configured by default, endpoints are off by default, the management API denies by default, and invalid capacities or budgets always fail startup — no silent fallbacks anywhere.
- **Global permit applies during execution**: queued tasks hold no worker permit; the fair semaphore is acquired only just before actual execution. Elastic quotas are distributed by weight, while priority is purely an observability label.
- **Stable error contract**: overload throws `ThreadPoolOverloadException` with a fixed message; component exceptions never echo configuration values, cursor keys, or task content.
- **SQLite persistence**: both child controller and governor use WAL with a 5s busy timeout. SQLite replaces cursor offset files and in-memory buffers, but does not replace Prometheus for long-term metric storage. Both SQLite paths must be mounted on persistent volumes.
- **Replaceable Beans**: `MdcTaskDecorator`, `ThreadPoolRetryPersistence`, `ThreadPoolDropNotifier`, `ThreadPoolMetricsAccessPolicy`, `GovernanceAccessPolicy`, and others can be overridden by the host as needed.

## Testing

```bash
mvn -pl dinghai-thread-pool -am test
```

All tests are self-contained JUnit tests with no external environment dependencies; SQLite-related tests write to real files under temporary directories.
