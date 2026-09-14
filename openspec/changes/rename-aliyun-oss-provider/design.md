## Context

文件存储通过公共 `FileStorage` 契约支持 Local、MinIO、RustFS 和阿里云 OSS。阿里云实现最初使用 `oss` 作为所有项目层命名，但 OSS 是对象存储产品类别而非唯一厂商标识；未来接入华为云对象存储时会导致模块、配置和 provider 名称歧义。用户同时指定采用官方 OSS Java SDK V2；本变更因此修正公开命名并替换 SDK，但不改变核心存储契约。

## Goals / Non-Goals

**Goals:**

- 在 Maven、Java、Spring Boot、测试、文档和 OpenSpec 中统一使用 `aliyun-oss` 作为阿里云实现标识。
- 让 provider 路由只接受 `aliyun-oss`，让属性绑定使用 `dinghai.file-storage.aliyun-oss.*`。
- 以 `com.aliyun:alibabacloud-oss-v2:0.5.1` 实现固定 bucket、流式 I/O、原生 PUT/GET 预签名、稳定错误和远端缓存。
- 凭证只从 `OSS_ACCESS_KEY_ID` 和 `OSS_ACCESS_KEY_SECRET` 环境变量读取，且绝不写入配置或日志。
- 通过全量引用搜索和 Starter/模块测试确认旧阿里云专属命名已清除。

**Non-Goals:**

- 不实现华为云或其他厂商 provider。
- 不保留 `oss` provider、配置前缀、旧 Maven artifact 或 Java 类型的兼容别名。
- 不引入 STS、凭证刷新、数据迁移或默认构建中的真实云账号测试。

## Decisions

### 使用 `aliyun-oss` 作为唯一公开 provider 名

将 provider 值、模块目录、artifact、配置前缀和文档标题统一到 `aliyun-oss`。保留 SDK 的 OSS 协议术语，因为它们描述外部依赖而非项目 provider。相比继续使用 `oss` 或同时接受两个值，单一明确名称能避免未来 provider 路由歧义，并使错误配置尽早失败。

### Java 命名采用 `aliyunoss` 与 `AliyunOss...`

将项目包从 `filestorage.oss` 改为 `filestorage.aliyunoss`，类型改为 `AliyunOssStorageConfiguration`、`AliyunOssClientFactory`、`AliyunOssFileStorageAdapter` 和 `AliyunOssExceptionMapper`。这种命名保留 Java 包的小写约定，同时在类型中明确厂商归属。外部 V2 SDK 的 `OSSClient` 和模型类型不重命名。

### 使用官方 OSS Java SDK V2 与预签名 PUT

依赖替换为 `com.aliyun:alibabacloud-oss-v2:0.5.1`，使用 `OSSClient`、V2 请求模型和 `PresignOptions`。V2 支持对象读写、查询、删除、列表，以及 `PutObjectRequest` / `GetObjectRequest` 预签名，但不提供旧 SDK 的 POST policy 表单 API，因此阿里云上传预签名改为 `PUT`：返回 URL、方法、到期时间和 V2 签名要求的请求头，`formFields` 为空。请求中签入 `Content-Type` 和 `Cache-Control`，但 V2 预签名无法表达旧 POST policy 的最大长度条件；服务端普通上传仍按既有上传策略校验。

### 凭证只从环境变量注入

`AliyunOssClientFactory` 在创建 client 时读取 `OSS_ACCESS_KEY_ID` 与 `OSS_ACCESS_KEY_SECRET`，并以 `StaticCredentialsProvider` 传入 V2 SDK。`bucket` 和 `region` 是非敏感的应用属性；AK/SK 不再出现在 `FileStorageProperties`、YAML 属性或异常消息中。测试环境按相同变量注入，缺失时受标记的真实 OSS 集成测试跳过，默认单元测试只使用 mock client。

### 采用原子全仓迁移，不做双写兼容层

由于 Maven 聚合、源码包路径、Starter 条件、测试和文档互相引用，迁移在一个任务中完成并保持构建可用。旧 `oss` provider 不再被枚举或条件匹配；不设置别名，避免错误配置被静默接受。发布说明和 README 提供迁移映射。

### 先以行为测试锁定命名契约

先更新/新增 RED 测试，验证 `aliyun-oss` 自动装配、旧 `oss` 拒绝、新属性绑定、V2 请求映射、环境凭证校验、重命名后的类型可用以及远端缓存仍创建，再迁移实现和文档。真实 OSS 测试使用独立随机 key，且仅在凭证被注入时执行。

## Risks / Trade-offs

- [破坏性配置变更导致升级启动失败] → 在中英文 README 与 `.env.example` 明确列出旧到新的映射，并让错误信息指出当前仅支持 `aliyun-oss`。
- [V2 无 POST policy API] → 以 V2 原生 `PUT` 预签名替换 POST 表单，明确返回必要请求头并更新文档与测试。
- [字符串替换误改 SDK 的通用 OSS 术语] → 只迁移项目 provider/module/package/type/config 标识，保留 SDK 字段和协议术语。
- [目录和包重命名遗漏导致编译失败] → 使用全仓 `rg` 搜索、Maven 模块测试和文件存储全量测试验证。
- [凭证未注入测试进程] → 默认只运行无凭证 mock 单元测试；真实 OSS 测试显式跳过且不把 bucket 中的已有对象作为断言输入。

## Migration Plan

1. 发布包含新 artifact `dinghai-file-storage-aliyun-oss` 和 OSS V2 的版本，并同步 Starter。
2. 使用者将依赖、provider 和 `dinghai.file-storage.oss.*` 配置迁移到对应 `aliyun-oss` 命名，删除应用属性中的 AK/SK，并在部署环境注入 `OSS_ACCESS_KEY_ID` 与 `OSS_ACCESS_KEY_SECRET`。
3. 调用方将阿里云预签名上传从 `POST` multipart 表单改为包含 `requiredHeaders` 的 `PUT` 请求。
4. 在预先创建的阿里云 OSS bucket 上使用随机测试 key 验证读写和预签名；对象 key、bucket 数据和逻辑 storage alias 不变。
5. 若升级前需要回滚，回退整个组件版本和配置；本变更不提供同版本旧命名兼容。

## Open Questions

无。华为云 provider 的命名和配置将在独立变更中定义。
