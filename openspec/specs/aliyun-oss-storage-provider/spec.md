# aliyun-oss-storage-provider Specification

## Purpose

为 `dinghai-file-storage` 提供使用阿里云 OSS 原生 Java SDK 的固定 bucket
文件存储适配器。

## Requirements

### Requirement: 阿里云 OSS 固定 bucket 适配器

组件 SHALL 在独立的 `dinghai-file-storage-oss` 模块中使用阿里云 OSS Java SDK，对公共
`dinghai.file-storage.bucket` 指定的固定 bucket 实现既有 `FileStorage` 契约。适配器 MUST
支持流式 put、get、stat、惰性 list、幂等 delete、原生 POST 预签名上传和原生 GET 预签名下载，
且不得自动创建 bucket 或改变 bucket 策略。

#### Scenario: 对象生命周期使用配置 bucket

- **WHEN** 调用方使用预先存在的私有 OSS bucket 写入、查询、读取、枚举和删除对象
- **THEN** 所有 SDK 请求 SHALL 针对配置的公共 bucket 执行，并返回不含 OSS SDK 类型的统一对象和元数据

#### Scenario: 删除缺失 OSS 对象

- **WHEN** 调用方删除一个不存在的对象 key
- **THEN** 操作 SHALL 返回 `false`，而不是将对象不存在暴露为后端故障

#### Scenario: bucket 不存在

- **WHEN** 配置的 OSS bucket 不存在或调用方没有其访问权限
- **THEN** 适配器 SHALL 返回稳定的文件存储异常，且不得创建 bucket 或修改其策略

### Requirement: OSS 配置与稳定错误边界

OSS 连接配置 SHALL 仅包含完整 endpoint、固定 bucket、AccessKey、AccessKeySecret 及连接、读取、
写入超时。endpoint、bucket、凭据和每个超时 MUST 在构造配置时校验为非空或正值；OSS SDK 的认证、
权限、对象缺失、超时、服务端和其他错误 MUST 映射为 `FileStorageErrorCode`，且异常消息不得回显
endpoint、AccessKey、AccessKeySecret、签名或 SDK 原始响应。

#### Scenario: 必填 OSS 连接配置缺失

- **WHEN** endpoint、bucket、AccessKey、AccessKeySecret 或任一超时缺失或无效
- **THEN** 创建配置 SHALL 在任何远程调用前失败，且失败信息不包含敏感值

#### Scenario: 认证失败

- **WHEN** OSS 拒绝配置凭据
- **THEN** 调用 SHALL 抛出 `AUTHENTICATION_FAILED` 或 `AUTHORIZATION_DENIED` 的统一异常，且消息不包含原始 SDK 错误正文

### Requirement: OSS 原生预签名

OSS 适配器 SHALL 用 OSS 原生签名生成 POST 上传表单和 GET 下载 URL。上传签名 MUST 绑定 bucket、
对象 key、内容类型、缓存策略、最大内容长度和到期时间；下载签名 MUST 绑定对象 key、到期时间、响应
缓存控制和 `inline` 或 `attachment` 的内容处置策略。

#### Scenario: 提交 OSS 预签名上传表单

- **WHEN** 调用方为指定 key、内容类型、缓存策略、最大长度和有效期请求预签名上传
- **THEN** 返回值 SHALL 包含 OSS 接受的 POST URL、`POST` 方法、到期时间及全部必需表单字段，提交该表单时 OSS 能验证约束

#### Scenario: 使用 OSS 预签名下载

- **WHEN** 调用方请求带 `inline` 或 `attachment` 响应模式的预签名下载
- **THEN** 返回值 SHALL 包含原生 `GET` URL、到期时间、请求的处置模式和缓存控制响应头
