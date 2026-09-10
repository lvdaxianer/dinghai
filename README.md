# dinghai（定海神针）

> 定海神针，稳住你的状态。

dinghai 是 lvdaxianer 的个人开源基础组件库，从生产项目沉淀、抽离而来，零业务耦合，专注提供可直接复用的基础设施能力。

## 模块清单

| 模块 | 版本 | 说明 |
| --- | --- | --- |
| `dinghai-shared-state` | 1.0.0 | 共享状态缓存：Redis/Caffeine 双后端、熔断容错、可观测性、Spring Boot 3 自动装配 |

## dinghai-shared-state

类型化共享状态缓存库，提供后端中立的缓存与临时状态契约：

- **双后端**：Caffeine（单进程本地）与 Redis（分布式，Lettuce 客户端，支持单机与 Sentinel 模式）
- **熔断容错**：Redis 故障时自动熔断并降级到本地 Caffeine，恢复后自动回切
- **可观测性**：内置健康快照与指标快照提供器
- **自动装配**：Spring Boot 3 引入即用，无需手写配置类

要求：Java 21+，Spring Boot 3.x。

### 快速开始

引入 Maven 坐标：

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-shared-state</artifactId>
    <version>1.0.0</version>
</dependency>
```

依赖引入后，`SharedStateCacheConfiguration` 通过 Spring Boot 自动装配（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）生效，自动注册以下 Bean：

| Bean 名称 / 类型 | 装配条件 |
| --- | --- |
| `cacheEnvelopeCodec`（`CacheEnvelopeCodec`） | 容器存在 `ObjectMapper` Bean，且应用未自定义 `CacheEnvelopeCodec`（应用可提供自定义实现替换默认 Jackson 编解码器） |
| `sharedStateRedisConnectionFactory`（`LettuceConnectionFactory`） | 满足 `RedisConfiguredCondition`；独立于其他业务 `RedisConnectionFactory` |
| `sharedStateRedisOperations`（`RedisTemplate<String, String>`，统一字符串序列化） | 满足 `RedisConfiguredCondition`，且存在 `sharedStateRedisConnectionFactory` |
| `redisSharedStateCache`（`RedisSharedStateCache`） | 满足 `RedisConfiguredCondition`，且存在 `sharedStateRedisOperations` |
| `caffeineSharedStateCache`（`CaffeineSharedStateCache`） | 无条件 |
| `sharedStateCache`（`ResilientSharedStateCache`，`@Primary`） | 无条件 |
| `sharedStateCacheHealthProvider`（`SharedStateCacheHealthProvider`） | 无条件 |
| `sharedStateCacheMetricsProvider`（`SharedStateCacheMetricsProvider`） | 无条件 |

业务代码直接注入 `SharedStateCache` 接口（实际类型为 `ResilientSharedStateCache`）即可。

### 配置项

配置前缀为 `dinghai.cache`，全部配置项如下：

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `dinghai.cache.caffeine.maximum-size` | `long` | `10000` | Caffeine 后端最大缓存条目数，必须为正数 |
| `dinghai.cache.redis.mode` | `standalone` / `sentinel` | `standalone` | Redis 连接模式，其他值会在装配前直接拒绝 |
| `dinghai.cache.redis.host` | `String` | 无 | 单机模式 Redis 主机；非空白即视为启用 Redis，空白则回退 Caffeine |
| `dinghai.cache.redis.port` | `int` | `6379` | 单机模式 Redis 端口，须在 1~65535 之间 |
| `dinghai.cache.redis.database` | `int` | `0` | Redis 数据库编号，不允许为负数 |
| `dinghai.cache.redis.password` | `String` | 无 | Redis 服务端密码，空白表示无密码 |
| `dinghai.cache.redis.sentinel.master` | `String` | 无 | Sentinel master 名称；sentinel 模式下必填且不能为空白 |
| `dinghai.cache.redis.sentinel.nodes` | `List<String>` | 空列表 | Sentinel 节点列表（`host:port`，支持逗号分隔）；sentinel 模式下必填且不能为空 |
| `dinghai.cache.redis.sentinel.username` | `String` | 无 | Sentinel ACL 用户名，非空白时才启用 ACL 用户名认证 |
| `dinghai.cache.redis.sentinel.password` | `String` | 无 | Sentinel ACL 密码，非空白时才设置，避免覆盖匿名访问 |
| `dinghai.cache.circuit.failure-threshold` | `int` | `3` | Redis 连续失败阈值，达到后熔断打开，必须为正数 |
| `dinghai.cache.circuit.open-duration` | `Duration` | `30s` | 熔断打开时长，到期后进入半开探测，必须为正时长 |
| `dinghai.cache.circuit.warm-ttl` | `Duration` | `5m` | 降级期间本地预热条目的有效时长，必须为正时长 |

单机模式最小配置示例：

```yaml
dinghai:
  cache:
    redis:
      host: 127.0.0.1
      port: 6379
```

Sentinel 模式配置示例：

```yaml
dinghai:
  cache:
    redis:
      mode: sentinel
      sentinel:
        master: mymaster
        nodes: 127.0.0.1:26379,127.0.0.2:26379
```

### 后端选择行为

- **未配置 Redis**（单机模式 `host` 为空白，且未显式声明 sentinel 模式）：仅装配 Caffeine 后端，`sharedStateCache` 直接走本地缓存。
- **显式配置 Redis**（单机模式配置 `host`，或 `mode: sentinel` 且 sentinel 配置完整）：装配 Redis 后端作为主后端，外层由 `ResilientSharedStateCache` 提供熔断回退，Redis 故障时降级到 Caffeine。
- **不受其他业务 Redis Bean 影响**：共享状态 Redis 是否启用只取决于 `dinghai.cache.redis.*` 自身配置（`RedisConfiguredCondition` / `isConfigured()`）。启用时，自动装配会创建 `sharedStateRedisConnectionFactory` 并由 `sharedStateRedisOperations` 显式注入它；应用里的业务 `RedisConnectionFactory` 不会被复用。

### 容错与可观测性

`ResilientSharedStateCache` 内置熔断状态机，状态取值见 `SharedStateCircuitState`：

- `CLOSED`：Redis 请求正常放行；
- `OPEN`：连续失败达到 `failure-threshold` 后熔断打开，请求降级到本地 Caffeine 预热条目；
- `HALF_OPEN`：`open-duration` 到期后仅放行一个恢复探测请求，成功则回到 `CLOSED`。

可观测性通过两个提供器 Bean 暴露，可注入到任何 Spring 管理组件：

- `SharedStateCacheHealthProvider#getHealth()` 返回 `SharedStateCacheHealth` 健康快照：`isRedisConfigured`、`circuitState`、`consecutiveFailures`、`activeBackend`、`isDegraded`；
- `SharedStateCacheMetricsProvider#getMetrics()` 返回 `SharedStateCacheMetrics` 指标快照：`totals` 全局总计与 `dimensionCounts` 按事件/命名空间维度的计数，并提供 `redisFailureCount()`、`circuitOpenCount()`、`fallbackCount()`、`consistencyLossCount()`、`recoveryCount()`、`halfOpenCount()`、`backendSelectionCount()` 等便捷访问方法。

### 键格式

缓存键统一为 `dinghai:{module}:{resource}:{identifier}` 格式，由 `CacheKeyBuilder` 负责构建：

- 键片段（module / resource / identifier）不能为空且不允许包含 `:`；
- 键片段长度不得超过 128 字符，拼接后的完整键不得超过 512 字符，超限时键构建直接抛出 `IllegalArgumentException`（长度按原始片段计，先于哈希替换判断）；
- 当命名空间策略为**强制 TTL**（`NamespacePolicy.isTtlRequired()`，如 `NamespacePolicy.stateFallback(...)`）时，identifier 会被替换为其 SHA-256 小写十六进制哈希，避免临时状态键泄露原始敏感标识符；
- 普通可重建缓存保留可读 identifier，便于运维定位。

`CacheKey` 是 sealed 接口，只能由 `CacheKeyBuilder` 创建，保证键结构统一。

### 使用示例

```java
import io.github.lvdaxianer.dinghai.sharedstate.CacheExpiry;
import io.github.lvdaxianer.dinghai.sharedstate.CacheKey;
import io.github.lvdaxianer.dinghai.sharedstate.CacheKeyBuilder;
import io.github.lvdaxianer.dinghai.sharedstate.CacheKeyNamespace;
import io.github.lvdaxianer.dinghai.sharedstate.CacheValue;
import io.github.lvdaxianer.dinghai.sharedstate.CompareAndSetCommand;
import io.github.lvdaxianer.dinghai.sharedstate.NamespacePolicy;
import io.github.lvdaxianer.dinghai.sharedstate.NamespacePolicyRegistry;
import io.github.lvdaxianer.dinghai.sharedstate.SharedStateCache;

import java.time.Duration;
import java.util.Optional;

// 声明命名空间策略并创建键构建器（应用中可作为单例持有）
NamespacePolicyRegistry registry = NamespacePolicyRegistry.of(
        NamespacePolicy.cacheFallback("profile-cache"),
        NamespacePolicy.stateFallback("auth-state"));
CacheKeyBuilder keyBuilder = new CacheKeyBuilder(registry);

// 注入主缓存 Bean（实际类型为 ResilientSharedStateCache）
// @Autowired SharedStateCache sharedStateCache;

// 普通缓存：保留可读 identifier，键形如 dinghai:user:profile:u-1001
CacheKeyNamespace profileNs = new CacheKeyNamespace("profile-cache", "user", "profile");
CacheKey<String> profileKey = keyBuilder.build(profileNs, "u-1001", String.class);
sharedStateCache.put(profileKey, "{\"name\":\"ding\"}", CacheExpiry.after(Duration.ofMinutes(10)));
Optional<String> profile = sharedStateCache.get(profileKey);

// 强制 TTL 的临时状态：identifier 以 SHA-256 哈希入库
CacheKeyNamespace sessionNs = new CacheKeyNamespace("auth-state", "auth", "session");
CacheKey<String> sessionKey = keyBuilder.build(sessionNs, "raw-session-token", String.class);
sharedStateCache.putIfAbsent(sessionKey, "token-value", CacheExpiry.after(Duration.ofMinutes(30)));

// 原子比较并更新
boolean swapped = sharedStateCache.compareAndSet(sessionKey,
        new CompareAndSetCommand<>("token-value", "rotated-token",
                CacheExpiry.after(Duration.ofMinutes(30))));
```

`SharedStateCache` 完整操作列表（详见接口源码）：

| 方法 | 说明 |
| --- | --- |
| `<T> Optional<T> get(CacheKey<T> key)` | 读取类型化缓存值 |
| `<T> void put(CacheKey<T> key, T value, CacheExpiry expiry)` | 新增或覆盖缓存值 |
| `<T> boolean putIfAbsent(CacheKey<T> key, T value, CacheExpiry expiry)` | 仅在键不存在时写入 |
| `<T> Optional<T> getAndDelete(CacheKey<T> key)` | 原子读取并删除 |
| `long increment(CacheKey<Long> key, long delta, CacheExpiry expiry)` | 原子增加长整型值 |
| `<T> boolean compareAndSet(CacheKey<T> key, CompareAndSetCommand<T> command)` | 原子比较并更新 |
| `<T> boolean compareAndDelete(CacheKey<T> key, T expectedValue)` | 值匹配时原子删除 |
| `<T> Optional<T> getAndSet(CacheKey<T> key, T value, CacheExpiry expiry)` | 原子读取旧值并替换为新值 |
| `Optional<CacheExpiry> remainingTtl(CacheKey<?> key)` | 查询剩余 TTL；不存在时为空，永久键为 `eternal()` |
| `boolean expire(CacheKey<?> key, CacheExpiry expiry)` | 为已有键设置 TTL |
| `boolean persist(CacheKey<?> key)` | 取消普通命名空间键的 TTL；强制 TTL 命名空间会拒绝 |
| `<T> Map<CacheKey<T>, T> getAll(Collection<CacheKey<T>> keys)` | 批量读取，仅返回命中的键 |
| `<T> void putAll(Map<CacheKey<T>, CacheValue<T>> entries)` | 使用每条 `CacheValue` 的独立 TTL 批量写入 |
| `long deleteAll(Collection<? extends CacheKey<?>> keys)` | 批量删除，返回实际删除的唯一键数量 |
| `boolean delete(CacheKey<?> key)` | 删除缓存值 |
| `boolean exists(CacheKey<?> key)` | 判断键是否存在 |

注意：原子操作的一致性范围由后端声明，Caffeine 后端仅保证单进程原子性。

`CacheValue<T>` 将批量写入值和 `CacheExpiry` 绑定。`putAll` 会在写入前校验全部条目的类型和命名空间 TTL 约束，避免调用方参数错误导致部分写入；Redis 后端以单次 pipeline 提交独立键写入，并在提交后逐条校验执行结果，任一条目失败即抛出 `RedisBackendException`（fail-fast），`getAll` 与 `deleteAll` 分别使用原生批量读取和删除命令。注意批量操作不保证原子性，失败后缓存可能处于部分写入状态。

TTL 生命周期中，`remainingTtl` 对不存在键返回空、对永久键返回 `CacheExpiry.eternal()`；`expire` 和 `persist` 仅对已有键成功时返回 `true`。强制 TTL 命名空间不能调用 `persist`，以保证临时安全状态不会变成永久值。

### 测试

默认执行全部测试（无需外部依赖）：

```bash
mvn test
```

真实 Redis 契约测试（`RedisSharedStateCacheContractTest`）默认跳过，需显式开启并保证目标 Redis 可连接：

```bash
mvn test -Ddinghai.redis.contract-test=true
```

可用以下系统属性指定契约测试目标（括号内为默认值）：

| 系统属性 | 说明 |
| --- | --- |
| `dinghai.redis.contract-host`（`127.0.0.1`） | 单机模式 Redis 主机 |
| `dinghai.redis.contract-port`（`6379`） | 单机模式 Redis 端口 |
| `dinghai.redis.contract-password`（空） | Redis 密码 |
| `dinghai.redis.contract-mode`（`standalone`） | 连接模式 |
| `dinghai.redis.contract-sentinel-master`（`mymaster`） | Sentinel master 名称 |
| `dinghai.redis.contract-sentinel-nodes`（`127.0.0.1:26379`） | Sentinel 节点列表 |
| `dinghai.redis.contract-sentinel-username`（空） | Sentinel ACL 用户名 |
| `dinghai.redis.contract-sentinel-password`（空） | Sentinel ACL 密码 |

## License

本项目基于 [MIT License](LICENSE) 开源。
