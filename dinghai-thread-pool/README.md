# dinghai-thread-pool

[English](README_EN.md) | 中文

面向 Spring Boot 3 的受管业务线程池与单实例观测总控。业务代码通过 `ThreadPoolGovernor` 获取受管执行器，全部任务在真实执行期受全局 worker 许可约束；子控（Starter）把池快照采样到本地 SQLite 并暴露只读采集端点，总控（governance-server）单实例轮询拉取各应用的指标页，持久化实例、cursor、采集缺口与告警。

要求：Java 21+，Spring Boot 3.x。

## 模块构成

| 模块 | 说明 |
| --- | --- |
| `dinghai-thread-pool-core` | 零依赖核心：`ThreadPoolGovernor` 以公平信号量实现全局 worker 许可，创建受管线程池并落实三种过载策略 |
| `dinghai-thread-pool-governance-api` | 子控与总控之间的协议模型（指标页、池快照、实例世代），无任何依赖 |
| `dinghai-thread-pool-spring-boot-starter` | 子控：自动装配治理器、MDC 任务装饰器、SQLite 指标采样与存储、只读采集端点、Micrometer 指标与健康检查 |
| `dinghai-thread-pool-governance-server` | 总控：独立 Spring Boot 应用，单实例 SQLite 控制面，定时轮询拉取子控指标并管理采集缺口与告警 |
| `governance-ui` | Vue 3 + Element Plus 总控分析台，只调用总控 REST API，不直连子控 |

## 快速开始

引入子控 Starter：

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-thread-pool-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

最小配置（自动装配默认关闭，必须显式启用并声明容量）：

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

所有容量与预算校验都在启动期执行，非法值直接启动失败，不存在静默兜底。装配完成后从 `ThreadPoolGovernor.executor(name)` 获取 `ExecutorService` 使用；不要持有原始 `ThreadPoolExecutor`。

## 配置项

配置前缀为 `dinghai.thread-pool`。所有校验都是代码逻辑（启动期执行），不依赖 Bean Validation 注解。

### 顶层参数

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `false` | 是否装配子控。默认关闭，必须显式设为 `true` |
| `process-thread-capacity` | `int` | 无（必填） | 应用显式声明的进程线程容量。缺失、小于 1 或小于 `managed-worker-budget` 时启动失败。应为容器、JVM、调度和三方库线程预留预算 |
| `managed-worker-budget` | `int` | `1` | 治理器创建的全局 worker 许可总数，仅约束由 `ThreadPoolGovernor` 管理的 worker。必须为正且不大于 `process-thread-capacity` |
| `endpoint-enabled` | `boolean` | `false` | 是否注册 `/actuator/dinghai-thread-pool/*` 只读采集端点。打开后必须在网关或 Spring Security 中配置 TLS 与服务身份认证 |

### pools（受管池）

`pools` 至少声明一个池，缺失时启动失败。池名必须是小写 kebab-case（`[a-z][a-z0-9-]{0,49}`），工作线程命名为 `<池名>-<线程号>`。

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pools.<name>.minimum-size` | `int` | `1` | 池的保底线程数，不小于 0 且不大于 `maximum-size`。全部池 minimum 之和超过 `managed-worker-budget` 时启动失败 |
| `pools.<name>.maximum-size` | `int` | `1` | 池的期望最大线程数，必须为正且不大于 `managed-worker-budget`。注册时按 weight 重新分配弹性配额，实际最大值为 `min(配置值, minimum + 按权重分得的剩余预算)` |
| `pools.<name>.weight` | `int` | `1` | 弹性配额分配权重，必须为正。扣除各池保底后，剩余预算按权重占比分配 |
| `pools.<name>.priority` | `HIGH` / `MEDIUM` / `LOW` | 无（必填） | 优先级标签，仅出现在快照与指标中，不参与运行时调度。缺失或非法时启动失败 |
| `pools.<name>.reliability` | `FAIL_FAST` / `PERSIST_FOR_RETRY` / `BEST_EFFORT_DROP` | 无（必填） | 过载策略（见下文）。缺失或非法时启动失败 |
| `pools.<name>.queue-capacity` | `int` | `100` | 等待队列容量，必须为正 |
| `pools.<name>.owner` | `String` | 无 | 业务所有者归属，`reliability=BEST_EFFORT_DROP` 时必填，缺失启动失败 |

### metrics（本地指标存储）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `metrics.sqlite-path` | `Path` | `dinghai-thread-pool.db` | 本地指标 SQLite 文件路径。**必须位于持久卷**，它替代 cursor offset 文件和内存缓冲区，但不替代 Prometheus 的长期指标存储 |
| `metrics.retention` | `Duration` | `24h` | 指标保留时长，每次采样时清除超过保留期的数据 |
| `metrics.sample-interval` | `Duration` | `30s` | 池快照采样间隔（fixed-delay） |
| `metrics.page-limit-max` | `int` | `1000` | 采集端点单页最大条数，必须为正；端点 `limit` 参数被钳制到 `[1, 该值]` |

完整配置示例：

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

## 业务接入

业务代码通过 `ThreadPoolGovernor` 使用受管池：

```java
ExecutorService executor = threadPoolGovernor.executor("report-export");
executor.submit(() -> exportReport(request));
```

任务在**真实执行期**才申请全局 worker 许可（公平信号量），排队等待许可的耗时计入指标；等待被中断时抛 `IllegalStateException`。MDC 由 `dinghaiThreadPoolTaskDecorator`（`MdcTaskDecorator`，宿主可用同名 Bean 覆盖）在提交时快照、执行前安装、执行后恢复。

### 过载策略

队列满且无法扩容时按池的 `reliability` 处理，拒绝计数先计入指标：

- **`FAIL_FAST`**：直接抛出稳定的 `ThreadPoolOverloadException`（继承 `RejectedExecutionException`，消息固定为 `thread pool is overloaded: <池名>`，不暴露内部状态）。无额外要求。
- **`PERSIST_FOR_RETRY`**：调用宿主提供的 `ThreadPoolRetryPersistence` Bean 的 `persist(poolName, task)` 持久化被拒绝任务；容器中没有该 Bean 时启动失败，持久化自身抛出的异常包装为 `IllegalStateException` 上抛。该端口约定调用失败必须向调用方抛出异常。
- **`BEST_EFFORT_DROP`**：丢弃任务并调用 `ThreadPoolDropNotifier.dropped(poolName, owner)` 发送可归属的丢弃通知；必须配置 `owner`，通知器缺省为空操作，宿主可按需提供。

### 与 dinghai-file-storage 集成

引入 file-storage Starter 的应用声明同名池 `file-storage-web` 后，`fileStorageWebTaskExecutor`（原 Spring Bean 名称与 `ThreadPoolTaskExecutor` 类型不变）会把任务装饰为 `governor.decorate("file-storage-web", task)`，复用全局许可与统计，不再脱离进程线程预算独立扩张。

## 观测端点

端点默认关闭，打开 `endpoint-enabled` 后注册（前缀固定为 `/actuator/dinghai-thread-pool`）。每个请求先经宿主提供的 `ThreadPoolMetricsAccessPolicy` Bean 判定（缺省拒绝，返回 403）。必须在网关或 Spring Security 中为此路径配置 TLS 与服务身份认证。

| 端点 | 说明 |
| --- | --- |
| `GET /metrics?cursor=&limit=` | 分页读取本地 SQLite 中的池快照指标页，返回 `nextCursor`、`hasMore`、`oldestCursor` 与指标数组。cursor 为服务端 HMAC 签名的不透明值（密钥只存于本地 SQLite），空 cursor 从头读 |
| `GET /snapshot` | 返回全部受管池的即时快照（活跃数、池大小、队列、实际最大值、提交/完成/拒绝/失败计数、等待与执行耗时、优先级与策略标签），用于过期 cursor 恢复 |
| `GET /identity` | 返回与本地 SQLite 持久卷绑定的稳定世代。同一持久卷重启保留该世代；重建或更换 SQLite 卷会生成新世代 |

cursor 过期（请求位置早于当前最旧数据）时端点返回 **HTTP 409**，响应体 `{"code":"CURSOR_EXPIRED","oldestCursor":"..."}`，调用方应改用 `snapshot` 恢复后从 `oldestCursor` 继续。伪造或非法 cursor 按参数错误处理。

子控导出 Micrometer 指标（tag `pool=<池名>`，均为 Gauge）：`dinghai.thread.pool.active`、`dinghai.thread.pool.queue.size`、`dinghai.thread.pool.completed`、`dinghai.thread.pool.rejected`。并注册 `dinghaiThreadPoolMetricStore` 健康检查，以最小分页读取探测 SQLite 可读性。

## 治理总控

`dinghai-thread-pool-governance-server` 是独立 Spring Boot 应用，使用单实例 SQLite 保存实例、版本、cursor、采集缺口、告警和已采集的线程池历史指标，**不提供高可用**。总控历史默认保留 30 天；不保存访问 Token、mTLS 私钥或业务任务参数、内容等敏感负载。

### 配置项

配置前缀为 `dinghai.thread-pool.governance`。端口等 Web 参数走 Spring Boot 标准配置。

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `sqlite-path` | `Path` | `dinghai-governance.db` | 总控 SQLite 文件路径，缺失时启动失败。**必须挂载持久卷** |
| `poll-interval` | `Duration` | `30s` | 轮询间隔（fixed-delay），必须为正 |
| `request-timeout` | `Duration` | `5s` | 请求子控的连接与读取超时，必须为正 |
| `retention` | `Duration` | `30d` | 总控线程池历史指标保留期，必须为正；每日清理一次 |
| `page-limit` | `int` | `1000` | 单页拉取条数，必须为正 |
| `api-token` | `String` | 无 | 非空时请求子控携带 `Authorization: Bearer <token>`。Token 由部署注入且可轮换 |

### 管理 API

总控管理 API 默认拒绝访问。宿主提供 `GovernanceAccessPolicy` Bean（缺省 `() -> false`）后放行以下接口：

| 端点 | 说明 |
| --- | --- |
| `POST /governance/instances` | 注册或更新子控实例，body 为 `{instanceId, baseUrl, environment, version}`。`baseUrl` 必须是 HTTPS 绝对地址，否则 400；`version` 变化代表子控本地 SQLite 可能已重建，总控清除旧 cursor 并从新的增量窗口恢复采集 |
| `GET /governance/instances` | 查询已注册实例清单 |
| `GET /governance/gaps?instanceId=` | 查询采集缺口（请求 cursor、最旧 cursor、发现时间、恢复状态 OPEN/RECOVERED） |
| `GET /governance/alerts` | 查询告警状态，告警键为 `collection-gap:<instanceId>` 与 `collection-failure:<instanceId>` |
| `GET /governance/metrics?instanceId=&poolName=&from=&to=` | 查询总控 SQLite 已持久化的线程池采样；全部条件为 query 参数，`from`/`to` 为毫秒时间戳且包含边界 |

### Vue 分析台

前端工程位于 `governance-ui`，开发时由 Vite 代理 `/governance` 到本地总控，生产部署时将反向代理配置到同一总控地址：

```bash
cd dinghai-thread-pool/governance-ui
npm install
npm run dev
```

界面展示实例/线程池筛选、活跃与最大线程、队列深度、完成/拒绝/失败累计、等待与执行耗时趋势、Top 线程池、告警、采集缺口和最近采样明细。它只读取总控 API；总控负责拉取子控并落 SQLite，浏览器不会访问子控。

### 轮询采集与恢复

总控定时轮询每个已注册实例（单实例失败隔离，不影响其他实例）：

1. 轮询前先读取子控 `identity` 做世代检查；世代与记录不符时删除旧 cursor 并更新版本。尚未升级的旧子控返回 404 时按兼容模式跳过世代检查，继续原有的 metrics/snapshot 采集；
2. 从已确认 cursor 起循环拉取指标页，先批量持久化到总控 SQLite，再发布实时聚合，最后提交 `nextCursor`；子控正数 `metricId` 在总控按 `(instanceId, sourceMetricId)` 去重，因此失败重拉不会重复写入；
3. cursor 过期（子控返回 409）时记录采集缺口、触发 `collection-gap` 告警、拉取子控快照恢复可观测性、提交 `oldestCursor` 后将缺口标记为 RECOVERED 并解决告警；
4. 整实例采集失败触发 `collection-failure` 告警并计数，成功则解决。

子控侧接入总控需要：`enabled` 与 `endpoint-enabled` 打开、`metrics.sqlite-path` 挂持久卷、提供放行总控的 `ThreadPoolMetricsAccessPolicy` Bean（例如校验 Bearer token）、HTTPS/TLS 由网关或运行环境提供，然后由部署方调用 `POST /governance/instances` 完成注册。子控不主动注册，采集方向始终是**总控拉子控**。

## 设计要点

- **显式优于隐式**：子控默认不装配、端点默认关闭、管理 API 默认拒绝、容量与预算非法一律启动失败，无任何静默兜底。
- **全局许可在执行期生效**：任务排队不占用 worker 许可，真实执行前才申请公平信号量；池的弹性配额由 weight 分配，priority 仅是观测标签。
- **稳定错误契约**：过载抛出固定消息的 `ThreadPoolOverloadException`，组件异常不回显配置值、cursor 密钥或任务内容。
- **SQLite 持久化**：子控与总控均使用 WAL 与 5s busy timeout；SQLite 替代 cursor offset 文件和内存缓冲区，但不替代 Prometheus 长期指标存储。子控与总控的 SQLite 路径都必须挂载持久卷。
- **可替换 Bean**：`MdcTaskDecorator`、`ThreadPoolRetryPersistence`、`ThreadPoolDropNotifier`、`ThreadPoolMetricsAccessPolicy`、`GovernanceAccessPolicy` 等均由宿主按需覆盖。

## 测试

```bash
mvn -pl dinghai-thread-pool -am test
```

全部为单元与集成一体的 JUnit 测试，无外部环境依赖；SQLite 相关测试使用临时目录真实落盘。
