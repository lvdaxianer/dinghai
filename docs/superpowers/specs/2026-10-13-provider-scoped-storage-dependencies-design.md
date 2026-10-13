# 按 provider 隔离文件存储依赖设计

## 背景

当前 `dinghai-file-storage-spring-boot-starter` 直接依赖 Local、MinIO、RustFS、阿里云 OSS 和华为云 OBS 的全部模块。`dinghai.file-storage.provider` 只在运行时控制 Bean 选择，不能阻止未使用的 SDK 进入最终应用依赖树；同时 RustFS 适配器通过 `MinioFileStorageAdapter` 实现，导致 RustFS 必须携带 MinIO SDK。

## 目标与非目标

### 目标

- 公共 Starter 不再传递任何远端 provider SDK。
- 业务只声明实际使用的 provider Starter，未使用 provider 的 SDK 不出现在运行时依赖树中。
- 保留统一的 `dinghai.file-storage.provider` 配置和 `FileStorage` 主 Bean 语义。
- provider 未声明或 provider 与依赖不匹配时，在启动阶段返回稳定、明确且不泄露配置值的错误。
- RustFS 不再依赖 MinIO 模块或 `io.minio:minio`，完整保留对象、预签名和 multipart 能力。
- 保留现有 Local、MinIO、OSS、OBS 的业务行为和公共 Web、缓存、迁移能力。

### 非目标

- 不改变 `FileStorage` 公共契约、对象 key 规则、Web API 路径或缓存文件格式。
- 不删除现有 provider 适配器模块；它们继续作为底层实现模块供对应 Starter 使用。
- 不在运行时根据 classpath 自动选择 provider；必须继续由 `dinghai.file-storage.provider` 显式选择。

## 架构决策

### 1. 公共 Starter 与 provider Starter 分层

保留 `dinghai-file-storage-spring-boot-starter` 作为公共 Starter，仅依赖 `core`、`local`、Spring Boot Web 和线程池 Starter。新增并发布四个 provider Starter：

- `dinghai-file-storage-minio-spring-boot-starter`
- `dinghai-file-storage-rustfs-spring-boot-starter`
- `dinghai-file-storage-aliyun-oss-spring-boot-starter`
- `dinghai-file-storage-huaweicloud-obs-spring-boot-starter`

每个 provider Starter 依赖公共 Starter 和自身适配器模块，并注册自身的 Spring Boot 自动配置。公共 Starter 不再直接引用任何远端 provider 类型。

### 2. 使用 provider-factory SPI 保留单一配置入口

公共 Starter 定义 `FileStorageProviderFactory`，包含 provider 标识和根据已校验公共配置创建 `FileStorage` 的方法。公共自动配置只依赖 `ObjectProvider<FileStorageProviderFactory>`：Local factory 内置于公共 Starter，远端 factory 由对应 provider Starter 注册。

公共配置仍负责公共 bucket、Web、multipart、缓存和迁移校验；选中的 provider factory 负责自身配置校验、客户端生命周期和适配器创建。若配置了远端 provider 但没有匹配 factory，公共配置抛出固定的“缺少 provider Starter”错误。

这样可以避免公共自动配置的 JVM 类解析触碰缺失 SDK，同时不改变调用方只配置一个 provider 变量的方式。

### 3. RustFS 使用独立 AWS S3 兼容客户端

RustFS 模块改用 AWS SDK for Java v2 S3 client 和 presigner，配置自定义 endpoint、path-style access、静态凭据和固定的 S3 region。RustFS 适配器直接实现现有 `FileStorage` 与 `MultipartFileStorage`，使用 S3 的 put/get/head/list/delete、presign、create/upload/list/complete/abort multipart API。

所有 SDK 异常在 RustFS 边界映射为 `FileStorageErrorCode` 和固定的 `RustFS operation failed` 安全消息。预签名统一使用 provider 原生 PUT 语义，required headers 只包含签名约束所需的内容类型和缓存策略；不会创建或修改 bucket。

### 4. 自动配置按 classpath 和配置双重隔离

每个 provider 自动配置使用 `@ConditionalOnClass` 保护自身 SDK 类型，并使用 `@ConditionalOnProperty(name = "dinghai.file-storage.provider", havingValue = "...")` 保护 factory Bean。公共 Starter 不使用远端 SDK 的 `Optional<T>` 参数，也不在公共配置中 import provider 实现。

## 风险与取舍

- **[依赖变更]** 现有只声明公共 Starter 的远端应用需要新增对应 provider Starter。→ 在 README、迁移说明和自动配置错误中明确列出依赖；这是有意的安全边界变更。
- **[RustFS SDK 行为差异]** MinIO SDK 与 AWS S3 SDK 的异常、预签名和 multipart 类型不同。→ 先为 RustFS 适配器建立失败测试和契约测试，再通过完整文件存储测试、依赖树验证和必要的 RustFS 集成测试确认行为。
- **[缺少 provider]** 配置 provider 但未声明对应 Starter 时，Spring 容器不能创建 `FileStorage`。→ 在公共 factory 选择处 fail-fast，错误消息只包含 provider 标识和依赖 artifact，不包含 endpoint 或凭据。
- **[同时声明多个 provider]** 多个 provider Starter 可以共存，但只有配置选中的 factory 创建客户端。→ 所有 provider factory 均受配置条件保护，公共 Bean 只选择一个主存储。
- **[SDK 漏洞扫描]** provider-specific Starter 能隔离未使用 SDK，但选中的 SDK 仍需正常纳入漏洞治理。→ 在 CI/发布核验中增加 provider 最小依赖树，分别记录每个支持矩阵的扫描边界。

## 迁移与回滚

1. 先发布公共 Starter 和 provider Starter 的新模块，并更新 README 的 Maven 依赖示例。
2. 使用 Local 的应用只保留公共 Starter；使用远端 provider 的应用在原有 Starter 之外增加对应 provider Starter，然后再移除旧的全量依赖声明。
3. 验证每个 provider 的最小依赖树：RustFS 不得包含 `io.minio:minio`，MinIO 不得通过公共 Starter被动带入 OSS/OBS SDK。
4. 回滚时恢复旧 Starter 依赖和旧自动配置即可；业务配置键和 `FileStorage` 契约不变。

