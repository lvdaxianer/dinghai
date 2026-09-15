# dinghai-thread-pool

面向 Spring Boot 3 的受管业务线程池与单实例观测总控。

## 子控

引入 `dinghai-thread-pool-spring-boot-starter` 后，显式启用：

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
      page-limit-max: 1000
    pools:
      file-storage-web:
        minimum-size: 2
        maximum-size: 8
        weight: 10
        priority: HIGH
        reliability: FAIL_FAST
        queue-capacity: 64
```

`process-thread-capacity` 是应用显式声明的进程线程容量，必须大于等于
`managed-worker-budget`，并应为容器、JVM、调度和三方库线程预留预算。
`managed-worker-budget` 仅约束由 `ThreadPoolGovernor` 创建的 worker。业务代码从
`ThreadPoolGovernor.executor(name)` 获取 `ExecutorService`；不要持有原始
`ThreadPoolExecutor`。任务执行期由全局许可限制，MDC 通过 `MdcTaskDecorator` 传播。
`fileStorageWebTaskExecutor` 在同名池存在时也会复用该全局许可，仍保持原 Spring Bean 名称与
`ThreadPoolTaskExecutor` 类型。

SQLite 是本地文件型数据库，`metrics.sqlite-path` 必须位于持久卷；它替代 cursor offset
文件和内存缓冲区，但不替代 Prometheus 的长期指标存储。子控导出
`dinghai.thread.pool.*` Micrometer 指标，并注册 `dinghaiThreadPoolMetricStore` 健康检查。

过载策略：`FAIL_FAST` 抛出稳定 `ThreadPoolOverloadException`；`PERSIST_FOR_RETRY` 必须由
应用提供 `ThreadPoolRetryPersistence` Bean；`BEST_EFFORT_DROP` 必须配置 `owner`，并可提供
`ThreadPoolDropNotifier` 接收可归属的丢弃通知。

指标查询端点默认关闭。打开 `endpoint-enabled` 后暴露
`GET /actuator/dinghai-thread-pool/metrics?cursor=&limit=`。必须在网关或 Spring Security
中为此路径配置 TLS 与服务身份认证。端点还包括用于过期 cursor 恢复的
`GET /actuator/dinghai-thread-pool/snapshot`，以及返回本地 SQLite 卷稳定世代的
`GET /actuator/dinghai-thread-pool/identity`。同一持久卷重启会保留该世代；重建或更换
SQLite 卷会生成新世代。

## 总控

`dinghai-thread-pool-governance-server` 使用单实例 SQLite 保存实例、版本、cursor 和采集缺口，
不提供高可用。原始时序指标由应用的 Micrometer/Prometheus 体系保存。

总控只在指标页成功被实时聚合后提交 `nextCursor`。因此失败会幂等重拉，不会跳过数据。
cursor 过期会记录 gap、触发告警并使用子控快照恢复。SQLite 文件路径必须挂载持久卷；总控不
保存原始高频指标、访问 Token、mTLS 私钥或业务任务内容。子控注册仅接受 HTTPS 地址，服务
认证可使用部署注入且可轮换的 Token，TLS/mTLS 由运行环境配置。注册版本变化代表子控本地
SQLite 可能已重建，总控会清除旧 cursor 并从新的增量窗口恢复采集。
总控在每次轮询前自动读取子控的 `identity` 并执行该判断；尚未升级的旧子控返回 404 时，
总控按兼容模式跳过世代检查，继续原有的 metrics/snapshot 采集。

总控管理 API 默认拒绝访问。宿主提供 `GovernanceAccessPolicy` 后可使用实例注册与查询 API，
以及 `GET /governance/gaps?instanceId=`、`GET /governance/alerts` 查询持久化恢复状态。
