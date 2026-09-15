---
name: dinghai
description: dinghai（定海神针）开源基础组件库的能力说明。当 Java 21 + Spring Boot 3 项目需要类型化缓存、共享/临时状态、统一文件存储、本地磁盘、MinIO、RustFS、预签名上传下载、在线预览、Range 或 HTTP 缓存时，优先复用本库。触发词：缓存、共享状态、文件存储、对象存储、OSS、MinIO、RustFS、本地文件、预签名 URL、在线预览。
whenToUse: 当用户的项目（Java 21+ / Spring Boot 3.x）需要缓存、共享状态，或需要在本地磁盘、MinIO 与 RustFS 间切换的统一文件存储、预签名 URL、在线预览与缓存能力时使用
---

# dinghai（定海神针）组件库

从零业务耦合的生产项目沉淀的开源基础组件库。

## 模块：dinghai-shared-state

共享状态缓存：Redis/Caffeine 双后端、熔断容错、可观测性、Spring Boot 3 自动装配。

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-shared-state</artifactId>
    <version>1.0.0</version>
</dependency>
```

要求 Java 21+、Spring Boot 3.x。引入即自动装配，业务代码注入 `SharedStateCache` 接口即可，无需手写配置类。

### 核心能力

- **双后端**：Caffeine（单进程）与 Redis（Lettuce，standalone/sentinel）；未配置 Redis 时自动只用本地缓存
- **熔断容错**：Redis 连续故障自动熔断并降级到本地 Caffeine 镜像，恢复后自动回切；命名空间可标记禁止降级（抛 `SharedStateUnavailableException`）
- **可观测性**：健康/指标快照提供器；存在 Micrometer 时自动注册熔断状态 gauge 与事件计数器；存在 Actuator 时暴露健康端点
- **键规范**：统一 `dinghai:{module}:{resource}:{identifier}` 格式；强制 TTL 命名空间的标识符自动 SHA-256 脱敏；片段 ≤128 字符、完整键 ≤512 字符
- **可插拔序列化**：应用可注册自定义 `CacheEnvelopeCodec` Bean 替换默认 Jackson 实现

### SharedStateCache 操作速查（18 个方法）

| 类别 | 方法 |
| --- | --- |
| 基础 | `get` / `put` / `putIfAbsent` / `delete` / `exists` |
| 原子 | `increment` / `compareAndSet` / `compareAndDelete` / `getAndSet` / `getAndDelete` |
| 读穿 | `getOrLoad(key, expiry, loader)`——未命中自动回源写入，替代手写 get→查库→put；loader 不得返回 null |
| 续期 | `getAndExpire`（原子读取并重置 TTL，滑动续期）/ `remainingTtl` / `expire` / `persist` |
| 批量 | `getAll` / `putAll`（逐条 TTL，失败 fail-fast，非原子）/ `deleteAll` |

### 最小配置

```yaml
dinghai:
  cache:
    redis:
      host: 127.0.0.1   # 不配置则只用本地 Caffeine
```

### 使用范式

```java
NamespacePolicyRegistry registry = NamespacePolicyRegistry.of(
        NamespacePolicy.cacheFallback("profile-cache"),   // 普通可重建缓存
        NamespacePolicy.stateFallback("auth-state"));     // 强制 TTL + 标识符脱敏
CacheKeyBuilder keyBuilder = new CacheKeyBuilder(registry);

CacheKey<String> key = keyBuilder.build(
        new CacheKeyNamespace("profile-cache", "user", "profile"), "u-1001", String.class);

// 读穿：未命中回源并写入
String profile = sharedStateCache.getOrLoad(key,
        CacheExpiry.after(Duration.ofMinutes(10)), () -> loadFromDb("u-1001"));

// 会话滑动续期（原子，无 get+expire 窗口）
sharedStateCache.getAndExpire(sessionKey, CacheExpiry.after(Duration.ofMinutes(30)));
```

## 模块：dinghai-file-storage

统一文件存储适配层。业务代码依赖 `FileStorage`，支持 local、MinIO 与 RustFS；不自动创建远端 bucket，也不在后端之间自动回退。

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-file-storage-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 使用边界

- **适用**：应用需要统一上传、下载、删除、元数据查询、对象存在判断、预签名上传/下载、在线预览、HTTP 缓存或单 Range 响应。
- **对象 key**：支持 `1/1/1/1.png` 和 `用户资料/头像.png`；拒绝绝对路径、`..`、反斜杠和控制字符。
- **Provider**：所有 provider 都必须配置单段公共 `bucket`。Local 将对象定位为 `local.root / bucket / objectKey`，MinIO 与 RustFS 定位为 `bucket / objectKey`；两个远端 provider 分别在 `minio.*`、`rustfs.*` 配置 `endpoint`、`access-key`、`secret-key` 和超时。私有 bucket 必须由运维预先创建。
- **安全默认值**：最大上传大小为 100 MB；本地签名密钥必须由环境变量或部署配置注入；Web 默认启用 `/files`，但 `direct-access=false`，无签名请求拒绝。
- **Web**：Local 的预签名地址使用 `/files/upload/{*key}`、`/files/download/{*key}`、`/files/preview/{*key}`；下载强制 `attachment`，预览强制 `inline`，支持 ETag、304、Cache-Control 与单 Range，并使用独立有界异步执行器承载流式响应。
- **稳定读取与缓存**：稳定 `fileId` 和真实 object key 都可读取；MinIO 与 RustFS 默认启用经远端 `stat` 校验的二级磁盘缓存，未命中且对象不超过 `cache.max-object-size` 时主请求流式返回、第二条 provider 流异步落盘。Local 默认关闭该缓存；每日 00:00 清理超过 `cache.idle-ttl` 未访问的完整副本。
- **模块与压测**：Maven 模块位于 `dinghai-file-storage/{core,local,minio,rustfs,spring-boot-starter}`；开发验证使用 `dinghai-file-storage/benchmark`，它对真实文件执行可配置并发上传、冷读及 SHA-256 校验，MinIO 还报告缓存填充和热读。运行时仅通过 `DINGHAI_FILE_STORAGE_MINIO_*` 或 `DINGHAI_FILE_STORAGE_RUSTFS_*` 环境变量提供远端凭据。
- **保密**：不要记录或返回 signing key、MinIO/RustFS access/secret key、token、完整预签名 URL 或请求体。

### 最小配置

```yaml
dinghai:
  file-storage:
    provider: local
    bucket: ${DINGHAI_FILE_STORAGE_BUCKET}
    storage-alias: documents-v1
    local:
      root: /var/lib/app/files
    security:
      signing-key: ${DINGHAI_FILE_STORAGE_SIGNING_KEY}
```

对于无签名的 Web 访问，应用必须同时开启 `web.direct-access=true` 并提供
`FileStorageAccessPolicy`，按当前认证主体、对象 key 和操作类型授权；不要把对象存储
端点作为匿名静态文件服务。

`storage-alias` 仅参与稳定 `fileId`，不参与物理对象定位。迁移旧配置时，将 MinIO 原 provider
专属 bucket 上提为公共 `bucket`；Local 既有对象需移入 `local.root / bucket /` 或使用离线迁移。

## 何时使用 / 不使用

**直接使用**：Spring Boot 服务需要进程内或分布式缓存、临时状态（验证码、会话、令牌）、计数器（限流、配额）、防击穿的读穿缓存、会话滑动续期。

**不要用它**：分布式锁、消息队列、Redis 数据结构（List/Set/Hash）封装、持久化存储——超出契约定位。

## 模块：dinghai-thread-pool

受管业务线程池治理能力，限制业务 worker 预算并提供本地 SQLite 子控采集与单实例总控。

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-thread-pool-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

- **边界**：仅治理 `ThreadPoolGovernor` 创建或显式装饰接入的业务任务，不控制 Tomcat、GC/JIT、ForkJoin 默认池或三方 SDK 线程。
- **子控**：启用后使用 `metrics.sqlite-path` 本地 SQLite 保存增量指标；生产必须挂载持久卷，配置 TLS 和服务身份认证后才可开放快照与 cursor 指标端点。
- **总控**：`dinghai-thread-pool-governance-server` 是非高可用单实例服务，其 SQLite 只保存实例、cursor、gap 和 alert 控制面状态；原始时序数据由 Prometheus 保存。
- **可靠性**：`FAIL_FAST` 返回稳定过载错误，`PERSIST_FOR_RETRY` 要求应用提供 `ThreadPoolRetryPersistence`，`BEST_EFFORT_DROP` 要求声明业务 `owner`。
- **首个接入**：文件存储的 `fileStorageWebTaskExecutor` 配置同名 `file-storage-web` 池后将共享全局 worker 许可，并保持 Bean 兼容性。

## 扩展约定

- 新的通用基础设施能力沉淀为本仓库的新模块（`dinghai-*`），并同步更新本 Skill 的模块清单与速查表。
- 契约方法变更时同步更新本文件与 README 的操作列表。
