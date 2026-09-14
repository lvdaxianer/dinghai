## Context

文件存储目前由后端中立的 `FileStorage` 契约和 Local、MinIO、RustFS 实现组成。OSS 不是项目当前 MinIO 客户端依赖的 S3 兼容目标；将它伪装成 S3 会使 POST policy、响应覆盖头和服务端错误映射不可控。因此需要一个独立模块，同时保留固定 bucket、流式 I/O 和安全错误边界。

## Goals / Non-Goals

**Goals:**

- 用 OSS Java SDK 实现完整 `FileStorage` 生命周期和原生预签名。
- 在 Starter 中按 `provider=oss` 装配唯一主 Bean，并复用远端读取缓存。
- 以 endpoint、AK/SK 和三个超时完成配置；缺失配置 fail fast，不回显密钥。
- 使用单元测试验证 SDK 请求构造、配置校验、错误映射和自动装配。

**Non-Goals:**

- 不申请、刷新或接受 STS 临时凭证。
- 不创建 bucket、修改 ACL、CORS 或 bucket policy。
- 不增加 Web API 或改变核心 `FileStorage` 契约。
- 不执行真实云账号集成测试；该测试需由部署环境另行显式启用。

## Decisions

### 使用独立 OSS Java SDK 模块

`oss` 模块依赖 `com.aliyun.oss:aliyun-sdk-oss`，并直接适配 `OSS`。这保留 OSS 原生 POST policy 和预签名 URL 语义。没有选择 MinIO/S3 兼容客户端，因为 OSS 的签名表单和错误码并非项目可承诺的 S3 兼容面。

### 固定 bucket 与完整 endpoint

`OssStorageConfiguration` 接受 endpoint、bucket、AccessKey、AccessKeySecret 和 connect/read/write timeout。SDK `ClientConfiguration` 使用连接、套接字和整体请求超时；adapter 不调用任何 bucket 创建或配置 API。POST action 对 `*.aliyuncs.com` 服务 endpoint 按 OSS 虚拟主机规则构造为 `<scheme>://<bucket>.<endpoint-host>`；已含 bucket 前缀或非阿里云自定义域名则原样使用。未知上传长度不写入负的 OSS 内容长度元数据。

### 流式对象及元数据策略

`put` 将请求流和内容长度、类型、缓存头交给 SDK，再 `stat` 读取服务端确认后的元数据。`get` 先 `stat` 处理缺失，再取得可关闭的 OSS 流。`list` 以 marker 分页惰性提取 summary，并对每个 key 复用 `stat`，确保迁移获得内容类型和缓存控制而非不完整摘要。

### 原生预签名

上传使用 `PolicyConditions` 绑定 key、`Content-Type`、`Cache-Control` 和 `content-length-range`，再以 SDK 生成 policy 和签名，返回 OSS 需要的 `OSSAccessKeyId`、policy、signature 与固定字段。下载使用 `GeneratePresignedUrlRequest(GET)` 与响应缓存、处置覆盖头。

### 错误和生命周期

`OssExceptionMapper` 只公开固定消息和稳定错误码。`NoSuchKey` 映射为空结果；认证/签名类错误映射为 `AUTHENTICATION_FAILED`，其余远端失败映射为 `STORAGE_UNAVAILABLE`。由配置构建的 SDK client 在 Spring 容器生命周期中关闭；直接 adapter 构造的 client 由调用方管理。

## Risks / Trade-offs

- [OSS SDK API 依赖较旧且传递依赖较多] → 在父 POM 集中锁定版本，并用模块单元测试验证实际 API。
- [POST action 需要虚拟主机 endpoint] → 约定 `oss.endpoint` 为 OSS 服务 endpoint；已含 bucket 前缀的 endpoint 原样使用。
- [list 为每个对象请求元数据] → 保持现有 MinIO/RustFS 迁移语义，优先确保缓存控制与内容类型不丢失；流本身仍惰性分页。
- [本地没有 OSS 凭证] → 覆盖 SDK 请求构造和异常映射的单元测试，真实账号验证不进入默认构建。

## Migration Plan

1. 发布增加 `dinghai-file-storage-oss` 的版本。
2. 使用者添加 Starter 后配置 `provider: oss` 与 `oss.endpoint`、`oss.access-key`、`oss.secret-key`，保持既有逻辑 bucket 和 storage alias。
3. 先在预先创建的私有 bucket 验证读写和预签名；出现问题时将 `provider` 回退为原后端，数据不由组件自动迁移或删除。

## Open Questions

- 无。STS 将在独立变更中确定凭证刷新、最小权限和生命周期模型。
