## Context

`dinghai-file-storage` 已通过 `FileStorage` 和可选的 `MultipartFileStorage` 提供后端无关契约，并分别以独立模块承载 Local、MinIO、RustFS 和阿里云 OSS。Spring Boot starter 根据 `dinghai.file-storage.provider` 创建一个具体实现。华为云 OBS 需要加入相同的 provider 体系，同时支持长期 AK/SK、STS 临时凭证、原生预签名 URL 和 multipart 大文件上传。

约束包括：bucket 必须由运维预先创建；公共 core 契约不扩展；凭证不得进入日志或异常；远端请求必须有明确超时；Web multipart 流程需要继续复用现有 `MultipartFileStorage` 接口。

## Goals / Non-Goals

**Goals:**

- 新增独立 `dinghai-file-storage-huaweicloud-obs` 模块，隔离 OBS SDK 依赖。
- 使用华为云官方 `com.huaweicloud:esdk-obs-java` SDK 实现对象读写、元数据、列举、删除、预签名和 multipart 生命周期。
- 支持长期 AK/SK、固定 STS 三元组以及可刷新凭证 Provider。
- 将 provider 接入 starter 的自动装配、配置校验、文档和测试。
- 保持异常语义、object key 校验、bucket 管理边界和安全默认值与现有 provider 一致。

**Non-Goals:**

- 不使用 Huawei Cloud SDK Bundle，不引入通用华为云服务依赖。
- 不修改 bucket、ACL、CORS、生命周期、对象标签或 bucket policy。
- 不新增 core 公共接口，不实现跨 provider 回退或迁移工具。
- 不把 OBS SDK 自带的文件级断点续传 API 暴露为新的公共契约；服务端和浏览器统一走现有 multipart 生命周期。

## Decisions

### 1. 使用 OBS 专用 SDK

采用 `com.huaweicloud:esdk-obs-java`，版本由根 POM 统一管理，初始固定为当前验证版本 `3.26.6`。该 SDK 直接提供 `ObsClient`、对象 API、签名 URL、分片上传和 STS Security Token 支持。

备选方案是 `huaweicloud-sdk-bundle` 或复用 MinIO S3 客户端。Bundle 会引入所有华为云服务和更大的依赖面；MinIO 客户端无法自然覆盖 OBS Security Token、签名参数和 provider-specific 错误语义，因此不选。

### 2. 独立适配器与固定 bucket

新增 `HuaweiObsFileStorageAdapter` 实现 `FileStorage, MultipartFileStorage`。构造函数接收已校验的连接配置和 SDK client；adapter 内部只使用公共 bucket，不执行 bucket 创建或变更。所有 SDK 异常统一由 `HuaweiObsExceptionMapper` 转换为 core 稳定异常。

### 3. 凭证模型

`HuaweiObsStorageConfiguration` 提供 endpoint、region、bucket、超时和静态凭证字段。凭证分为：

- static：AK/SK；
- sts：AK/SK/Security Token；
- provider：由宿主提供可刷新凭证实现。

client factory 优先接收显式 Provider；配置模式用于部署环境注入的固定值。不会把 secret、token 或完整签名 URL 写入日志。STS provider 的刷新失败直接向调用方返回稳定认证/远端错误，不静默降级到过期凭证。

### 4. Multipart 与预签名

适配器将 OBS 的 initiate/uploadPart/listParts/complete/abort 映射到现有 `MultipartFileStorage`。`presignPart` 生成绑定 bucket、object key、uploadId 和 partNumber 的 PUT URL，并返回 SDK 要求的签名 headers。普通预签名上传使用 PUT，下载使用 GET，并保留响应处置和缓存控制参数。

这使浏览器可以绕过业务服务直传大文件；服务端仍可通过 `uploadPart` 使用流式 InputStream。分片 ETag 由上传响应返回，并且 complete 只提交调用方确认过的分片清单。

### 5. Starter 接入

根聚合 POM 增加模块；starter 增加 OBS 模块依赖，并在 provider 分支中创建 adapter。provider 为 `huaweicloud-obs` 时，endpoint、region、bucket 和对应凭证模式必须在启动期校验；不做自动探测、默认回退或旧 provider 别名兼容。

### 6. 测试策略

单元测试覆盖配置校验、凭证选择、异常映射和 adapter 的 SDK 请求/响应映射；真实集成测试通过环境变量显式启用，覆盖对象生命周期和 multipart 生命周期。没有 OBS 凭证时，默认 Maven 测试不得访问外部服务。

## Risks / Trade-offs

- **[SDK 依赖与现有 OkHttp/Jackson 冲突]** → 只引入 OBS 专用 artifact，在根 POM 锁定版本，并执行完整 Maven reactor 测试。
- **[STS 固定三元组过期]** → 支持可刷新 Provider；固定配置模式在启动和请求失败时不伪造长期凭证，也不自动回退。
- **[multipart 分片遗留产生存储费用]** → 保留显式 abort；服务端现有会话清理器负责业务会话，运维侧应配置 OBS incomplete multipart lifecycle。
- **[预签名 URL 泄露敏感查询参数]** → 只在返回值中交给调用方，禁止日志记录；异常只保留 provider 错误码和 request id。
- **[OBS endpoint 与 region 不匹配]** → 启动期要求二者同时配置，并通过可选真实集成测试验证部署环境映射。

## Migration Plan

1. 发布包含新模块和 starter provider 的版本；现有 provider 配置不变。
2. 运维预先创建目标 OBS bucket，并配置最小对象读写权限。
3. 注入 endpoint、region、bucket 和 AK/SK 或 STS/provider 配置后，将 provider 切换为 `huaweicloud-obs`。
4. 先用小文件和 multipart 测试对象完成、下载和删除，再承载业务流量。
5. 回滚时将 provider 改回原 provider；本次不自动迁移历史对象，历史对象继续留在原存储。

## Open Questions

- 生产环境是否提供宿主级动态 STS Provider；若没有，先使用固定 STS 三元组配置并明确其过期运维责任。
- OBS 集成测试环境是否可提供专用 bucket；没有时仅运行单元测试和编译验证。
