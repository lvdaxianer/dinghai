# multipart-upload Specification

## Purpose

为 Local、MinIO、RustFS 和阿里云 OSS 提供可恢复、受授权且资源受限的大文件分片上传生命周期。

## Requirements

### Requirement: 统一 multipart 上传生命周期
`FileStorage` SHALL 提供后端中立的 multipart 生命周期：初始化会话、生成指定分片的上传授权、查询已上传分片、完成会话和取消会话。请求 MUST 包含对象 key、内容类型、缓存策略、总大小、分片大小和过期时间等可验证信息；服务端 MUST 校验 key、分片号范围、分片大小、总大小、会话归属和过期时间。

#### Scenario: 初始化分片会话
- **WHEN** 调用方提交合法对象 key、总大小和分片大小
- **THEN** provider 创建一个未完成会话并返回不含密钥的会话标识、对象 key、分片大小、分片总数和过期时间

#### Scenario: 拒绝非法分片参数
- **WHEN** 分片号小于 1、超过最大分片数、分片大小不符合策略或总大小超过上传上限
- **THEN** 服务端返回稳定的参数错误且不调用对象存储写入接口

### Requirement: 客户端直传分片与断点续传
远端 provider SHALL 为每个合法分片签发绑定会话、对象 key、分片号和有效期的原生预签名 URL；客户端 SHALL 能通过查询接口获得已上传分片及 ETag，并可安全重试同一分片。Local provider SHALL 提供等价的组件分片上传 URL，数据写入受限临时目录而不进入应用内存。

#### Scenario: 签发远端分片 URL
- **WHEN** 调用方为未过期会话请求某个分片的上传授权
- **THEN** 返回只允许该会话和分片号的短期 URL、方法和必要请求头

#### Scenario: 查询已上传分片
- **WHEN** 调用方查询仍有效的会话
- **THEN** 返回对象存储当前确认的分片号、长度和 ETag，结果按分片号升序且不泄漏凭据或完整签名 URL

#### Scenario: 重试同一分片
- **WHEN** 客户端使用同一会话和分片号重复上传
- **THEN** provider 以对象存储的最新分片为准，不产生重复完成片段或额外正式对象

### Requirement: 完成、取消与幂等
完成操作 MUST 要求客户端提交完整分片清单或使用服务端查询结果，校验分片连续性、总长度和最后分片规则后才提交 provider complete；同一会话的重复完成 SHALL 返回同一最终对象结果。取消操作 SHALL 幂等并清理未完成会话；过期会话 MUST 被拒绝并可由清理任务回收。

#### Scenario: 完成合法会话
- **WHEN** 所有必需分片已存在且顺序、长度和总大小校验通过
- **THEN** provider 合并对象并返回服务端确认的统一对象元数据

#### Scenario: 拒绝缺片完成
- **WHEN** 完成请求缺少任一必需分片或分片清单不连续
- **THEN** 返回稳定冲突错误且正式对象不会被创建或覆盖

#### Scenario: 重复完成
- **WHEN** 同一会话在完成成功后再次提交相同完成请求
- **THEN** 返回相同对象元数据，不重复创建对象

#### Scenario: 取消或过期回收
- **WHEN** 调用方取消会话或清理任务发现会话超过 TTL
- **THEN** 未完成分片被删除，后续分片上传和完成请求被拒绝，已存在正式对象不受影响

### Requirement: Provider 语义与资源边界
MinIO 和 RustFS SHALL 使用 S3 Multipart API；阿里云 OSS SHALL 使用 OSS Multipart API；Local SHALL 使用临时会话目录并在完成时原子移动。所有 provider MUST 继续使用固定 bucket、不自动创建 bucket，并使用有界并发、连接/读取/写入/整体超时和稳定错误映射。multipart 操作不得把完整文件载入内存。

#### Scenario: provider 选择保持隔离
- **WHEN** Starter 选择 local、minio、rustfs 或 aliyun-oss
- **THEN** 只有选中的 provider 处理 multipart 会话，其他 provider 不创建客户端或临时对象

#### Scenario: 上游失败可恢复
- **WHEN** 分片上传、列举、完成或取消遇到超时、鉴权失败或服务端错误
- **THEN** 返回可分类稳定错误，保留会话可重试状态或提供明确取消/清理路径，不暴露 SDK 原始响应、密钥或完整签名

### Requirement: Starter HTTP 接口与授权
Starter SHALL 提供 RESTful multipart 资源接口：创建会话、签发分片 URL、查询分片、完成和取消；所有数据变更接口 MUST 经过宿主授权策略、服务端输入校验、会话对象 key 绑定和租户/主体边界检查。接口 MUST 使用稳定错误结构和适当 HTTP 状态码，并在限流耗尽时返回 429 而不读取被拒绝的分片请求体。

#### Scenario: 未授权创建会话
- **WHEN** 当前主体未被宿主策略授予上传权限
- **THEN** 请求返回 403 且不创建 provider 会话

#### Scenario: 分片接口限流
- **WHEN** 分片上传并发达到配置上限
- **THEN** 请求在读取请求体前返回 429，并保留已有会话及已上传分片
