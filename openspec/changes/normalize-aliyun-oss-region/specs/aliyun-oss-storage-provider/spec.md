## MODIFIED Requirements

### Requirement: 阿里云 OSS 固定 bucket 适配器

组件 SHALL 在独立的 `dinghai-file-storage-aliyun-oss` 模块中使用
`com.aliyun:alibabacloud-oss-v2:0.5.1`，对公共
`dinghai.file-storage.bucket` 指定的固定 bucket 实现既有 `FileStorage` 契约。适配器 MUST
支持流式 put、get、stat、惰性 list、幂等 delete、原生 PUT 预签名上传和原生 GET 预签名下载，
且不得自动创建 bucket 或改变 bucket 策略。项目层模块、包和类型名称 MUST 明确包含
`aliyun-oss` / `aliyunoss`，不得使用泛化的项目 provider 标识 `oss`。首次 V2 对象列举请求
MUST 不传递 continuation token；仅在 OSS 返回下一页 token 后传递该字段。

#### Scenario: 首次列举对象

- **WHEN** 调用方首次列举固定 bucket 中的对象
- **THEN** 适配器 SHALL 使用不含 continuation token 的 V2 请求，避免向 SDK builder 传递空值

### Requirement: OSS 配置与稳定错误边界

OSS 连接配置 SHALL 接受阿里云控制台地域 ID（例如 `oss-cn-beijing`），并在创建 OSS V2 SDK client 的边界将其规范化为 SDK 所需的地域标识（例如 `cn-beijing`）。配置仍 SHALL 仅包含 region、固定 bucket 及连接、读取、写入超时；AccessKey MUST 仅从 `OSS_ACCESS_KEY_ID` 环境变量读取，AccessKeySecret MUST 仅从 `OSS_ACCESS_KEY_SECRET` 环境变量读取。region、bucket、凭据和每个超时 MUST 在构造配置时校验为非空或正值，错误消息不得回显敏感值。

#### Scenario: 使用控制台地域 ID 创建客户端

- **WHEN** 调用方配置 region 为 `oss-cn-beijing` 并创建阿里云 OSS V2 client
- **THEN** SDK SHALL 使用 `cn-beijing` 作为地域标识并访问 `dinghai.oss-cn-beijing.aliyuncs.com`，不得生成 `oss-oss-cn-beijing` endpoint

#### Scenario: SDK 地域格式保持兼容

- **WHEN** 调用方配置 region 为不带 `oss-` 前缀的 SDK 地域标识
- **THEN** 客户端 SHALL 原样使用该地域标识，不得重复删除或添加前缀
