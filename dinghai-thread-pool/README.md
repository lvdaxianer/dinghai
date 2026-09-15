# dinghai-thread-pool

面向 Spring Boot 3 的受管业务线程池与单实例观测总控。

## 子控

引入 `dinghai-thread-pool-spring-boot-starter` 后，显式启用：

```yaml
dinghai:
  thread-pool:
    enabled: true
    managed-worker-budget: 16
    endpoint-enabled: false
    metrics:
      sqlite-path: /var/lib/app/dinghai/thread-pool-metrics.db
      retention: 24h
      page-limit-max: 1000
    pools:
      file-storage-web-h:
        minimum-size: 2
        maximum-size: 8
        weight: 10
        priority: HIGH
        reliability: FAIL_FAST
        queue-capacity: 64
```

`managed-worker-budget` 仅约束由 `ThreadPoolGovernor` 创建的 worker。业务代码从
`ThreadPoolGovernor.executor(name)` 获取 `ExecutorService`；不要持有原始
`ThreadPoolExecutor`。任务执行期由全局许可限制，MDC 通过 `MdcTaskDecorator` 传播。

指标查询端点默认关闭。打开 `endpoint-enabled` 后暴露
`GET /actuator/dinghai-thread-pool/metrics?cursor=&limit=`。必须在网关或 Spring Security
中为此路径配置 TLS 与服务身份认证。

## 总控

`dinghai-thread-pool-governance-server` 使用单实例 SQLite 保存实例、cursor 和采集缺口，
不提供高可用。原始时序指标由应用的 Micrometer/Prometheus 体系保存。

总控只在指标页成功被实时聚合后提交 `nextCursor`。因此失败会幂等重拉，不会跳过数据。
SQLite 文件路径必须挂载持久卷。
