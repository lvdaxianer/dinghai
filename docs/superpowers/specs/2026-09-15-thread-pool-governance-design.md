# Dinghai 线程池治理设计

## 目标

新增 Dinghai 可复用线程池治理能力：业务应用通过 starter 创建并使用受控执行器；可选总控服务
收集多个实例的快照与增量指标。该能力约束受管业务 worker，不承诺控制 JVM 内所有线程。

## 范围

一期交付以下模块：

```text
dinghai-thread-pool/
  core/
  spring-boot-starter/
  governance-api/
  governance-server/
```

- `core`：无 Spring 依赖的池定义、配额计算、全局许可、受控执行器、运行快照和异常模型。
- `spring-boot-starter`：配置绑定、自动装配、MDC 透传、Micrometer 指标、子控 SQLite
  指标存储，以及受保护的快照和 cursor 采集端点。
- `governance-api`：子控与总控共享的不可变 DTO，包括 cursor、指标批次、快照、告警事件。
- `governance-server`：独立 Boot 服务，负责实例注册、轮询、cursor、实时聚合、告警与查询 API。
- `dinghai-file-storage` 的 `fileStorageWebTaskExecutor` 作为首个接入对象。

一期不包括：控制 Tomcat、GC/JIT、ForkJoin 默认池和三方 SDK 的线程；远程修改子控配置；
自研浏览器看板；持久化重试的 MQ 或数据库实现；总控高可用。

## 子控资源模型

`managed-worker-budget` 是所有由 `ThreadPoolGovernor` 创建的业务 worker 上限，不是 JVM
总线程上限。应用容量规划必须通过 `process-thread-capacity` 明确进程可承受线程数，并预留
容器、JVM、调度和三方库线程。

子控不变量：

1. `sum(actualMax) <= managed-worker-budget`。
2. 每个开始执行的任务先取得全局许可；任务在 `finally` 中释放许可。
3. 所有业务调用只取得受控 `ExecutorService` 门面，不取得原始
   `ThreadPoolExecutor`。
4. `weight` 仅决定弹性配额；`priority` 是 HIGH、MEDIUM、LOW 三档的独立准入属性。

池还声明可靠性：

| reliability | 过载行为 |
|---|---|
| `FAIL_FAST` | 返回稳定过载错误，不阻塞 HTTP、MQ 或调度入口。 |
| `PERSIST_FOR_RETRY` | 调用业务提供的持久化重试端口；写入失败视为最高优先级故障。 |
| `BEST_EFFORT_DROP` | 显式丢弃并记录指标、告警和业务所有者。 |

## 配置与自动装配

starter 使用 `dinghai.thread-pool` 前缀。启动失败条件包括名称重复、名称格式非法、
`min > max`、权重非正、保底总和超过预算、队列无界、缺少 priority/reliability，以及
`BEST_EFFORT_DROP` 缺少业务所有者。

starter 注册 `ThreadPoolGovernor`、按名称的受控执行器、TaskDecorator、子控 SQLite
指标存储和可选 Micrometer 绑定器。仅在应用提供对应权限保护时暴露管理采集端点；默认不把
端点暴露到公网。

```yaml
dinghai:
  thread-pool:
    metrics:
      sqlite-path: /var/lib/app/dinghai/thread-pool-metrics.db
      retention: 24h
      sample-interval: 30s
      page-limit-max: 1000
```

SQLite 连接必须启用 WAL、busy timeout 和事务性批量写入；查询接口按 `metric_id` 索引分页。
保留清理和采样不能运行在受管业务线程池内，避免治理自身被业务过载饿死。

## 子控与总控协议

子控提供：

- `GET /actuator/dinghai-thread-pool/snapshot`：当前全部池快照。
- `GET /actuator/dinghai-thread-pool/metrics?cursor=&limit=`：增量指标页。

每个子控默认使用本地 SQLite 保存采样指标，cursor 由 SQLite 的单调主键派生并经服务端编码，
客户端不可构造。子控表为：

```text
thread_pool_metric(metric_id, sampled_at, pool_name, active, pool_size, queue_size,
                   actual_max, submitted, completed, rejected, failed, wait_ms, exec_ms,
                   priority, reliability)
```

响应包含 `nextCursor`、`hasMore` 和 `oldestCursor`。总控必须拉完全部分页后才提交
cursor。子控按 `dinghai.thread-pool.metrics.retention` 定时删除过期指标；若 cursor 超出保留窗口，子控返回
`409 CURSOR_EXPIRED` 和 `oldestCursor`，总控记录缺口、告警并使用快照恢复。

SQLite 取代 YAML、offset 文件和内存环形缓冲区，但本身是本地文件型数据库：生产部署必须将
`dinghai.thread-pool.metrics.sqlite-path` 挂载到持久卷。若该卷丢失或子控重建，总控将收到
cursor 过期或实例世代变更信号，并从当前快照重新开始采集。

端点必须使用 TLS 和服务身份认证。默认选择 mTLS；Token 方案必须由部署环境注入且可轮换。

## 总控服务与持久化

总控为单实例服务，默认使用本地 SQLite，不支持也不宣称高可用。总控 SQLite 仅保存控制面状态：

```text
governance_instance(instance_id, base_url, environment, enabled, version, updated_at)
governance_cursor(instance_id, cursor, updated_at)
governance_alert_state(alert_key, status, first_fired_at, last_fired_at, last_value, resolved_at)
governance_collection_gap(instance_id, requested_cursor, oldest_cursor, detected_at, recovery_status)
```

不得存储原始高频指标、访问 Token、mTLS 私钥或业务任务内容。子控和总控均通过 Micrometer
暴露指标，由 Prometheus 拉取并保存趋势及长期告警查询数据；总控只做实时聚合并导出自身采集
延迟、失败、缺口和告警指标。

单实例总控的采集顺序：

```text
读取 SQLite cursor
  -> 拉取一页
  -> 更新实时聚合 / MeterRegistry 指标
  -> 成功后 SQLite 事务提交 nextCursor
  -> hasMore=true 时继续下一页
```

聚合或指标发布失败时不得推进 cursor，允许幂等重拉。cursor 过期时写入
`governance_collection_gap`，触发告警，再用快照恢复可观测性。

## 首个接入与验证

首个接入对象为 `dinghai-file-storage` 的 `fileStorageWebTaskExecutor`。保持 Bean 名称和
Web MVC 行为兼容，通过 `ThreadPoolGovernor` 获取受控执行器。

验证至少覆盖：配额计算及并发许可；三种可靠性拒绝行为；MDC 传播；配置失败；子控 SQLite
的 WAL 并发读写、重启续拉、保留清理、cursor 分页和过期；总控 SQLite 重启续拉；指标发布
失败不推进总控 cursor；总控实例不可达；以及 file-storage 自动装配兼容性。

## 发布与迁移

父 POM 新增 `dinghai-thread-pool` 聚合模块。新能力默认关闭，只有
`dinghai.thread-pool.enabled=true` 才创建受管池。file-storage 的接入以兼容开关灰度发布；
回退时停止接收新任务，排空或持久化在途任务，再恢复原执行器。
