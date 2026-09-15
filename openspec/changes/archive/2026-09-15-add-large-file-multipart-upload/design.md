## Context

现有 `FileStorage` 将 Local、MinIO、RustFS 和阿里云 OSS 统一为整对象读写契约，但没有未完成上传的生命周期模型。大文件上传需要让客户端直传分片，应用只负责授权和最终提交，从而避免应用实例承载完整文件流；同时 Local 没有 multipart 服务端 API，需要一个可回收、可原子完成的临时文件实现。

## Goals / Non-Goals

**Goals:**

- 增加不泄漏 provider 类型的 multipart 核心模型与接口。
- 为 S3 兼容 provider 和 OSS 映射原生 multipart API。
- 为 Local 提供等价的临时分片会话和受控 Web 上传端点。
- 支持断点查询、分片重试、幂等完成/取消、TTL 清理和稳定错误。
- 保持现有整对象 `put`、普通 presign 和读缓存行为兼容。

**Non-Goals:**

- 不引入数据库表或 Redis 作为远端分片状态源。
- 不让应用服务代理远端大文件内容，不实现通用断点下载。
- 不自动创建或修改任何 bucket、ACL、CORS 或策略。
- 不在本次变更中实现跨 provider 的自动迁移。

## Architecture

`core` 增加 `MultipartFileStorage` 能力接口和请求/结果值对象。接口操作为 `initiate`、`presignPart`、`listParts`、`complete`、`abort`；会话引用包含 opaque `uploadId`、key、分片策略和过期时间，分片结果只暴露 part number、size、etag。`FileStorage` 保持原接口不变，Starter 注入可选 multipart 能力并在不支持时返回稳定错误。

MinIO 与 RustFS 在各自 adapter 内调用 MinIO SDK 的 `newMultipartUpload`、`uploadPart`、`listParts`、`completeMultipartUpload` 和 `abortMultipartUpload`。分片预签名使用 S3 的 `uploadPart` query 签名；完成前再次列举并校验服务端分片。RustFS 继续复用 S3 适配代码，但通过独立配置与错误映射暴露 provider 名称。

OSS adapter 调用 OSS V2 的 Initiate/UploadPart/ListParts/Complete/Abort API；如果当前 SDK 版本缺少预签名的 UploadPart builder，则由 adapter 生成绑定 `uploadId`、`partNumber` 的原生签名请求，不能退化为整对象 PUT。OSS 的 multipart 状态完全以 OSS 返回为准。

Local 以 `multipart-root/<opaque-upload-id>/parts/<part-number>` 保存分片，以受保护的会话元数据文件保存 key、总大小、分片大小、内容类型、缓存策略和 expiry。每片先写临时文件、限制长度并原子移动；完成时只允许连续分片，使用流式合并到最终临时文件并原子移动，同时写入正式元数据。清理任务删除过期会话目录；正式对象删除不受清理任务影响。

## HTTP Contract

- `POST /files/multipart`: 创建会话，body 只包含 key、size、partSize、contentType、cacheControl、expiresIn。
- `POST /files/multipart/{uploadId}/parts/{partNumber}/presign`: 返回分片上传 URL；`partNumber` 使用 path 资源标识。
- `GET /files/multipart/{uploadId}/parts`: 从 provider 查询已确认分片。
- `POST /files/multipart/{uploadId}/complete`: body 包含客户端分片清单和可选幂等 token。
- `DELETE /files/multipart/{uploadId}`: 取消会话，重复调用返回成功语义。
- Local 分片数据由 `PUT /files/multipart/{uploadId}/parts/{partNumber}` 接收；远端只暴露 presigned URL，不经过该 Controller。

所有接口绑定当前主体和对象 key。错误使用现有 `FileStorageExceptionHandler` 的稳定 error code；不返回 access key、secret key、完整预签名 URL 到日志或错误正文。

## Reliability / Security

- 初始化、签发、完成和取消具备会话级幂等；完成状态转换只允许 `OPEN -> COMPLETING -> COMPLETED` 或 `OPEN -> ABORTED`。
- 同一会话的完成与取消使用 per-upload 锁/原子状态文件保护；分片写入允许并发但同一 part 使用原子替换。
- 所有输入进行服务端校验：key 安全、size 非负且不超过上传上限、partSize 在配置范围内、partNumber 有界、清单无重复且 ETag 格式安全。
- 远程 SDK 使用现有隔离 HTTP 客户端及配置超时；分片 URL 有短 TTL，不记录 URL。清理任务使用有界扫描和删除批次。
- 指标记录初始化、分片签发、列举、完成、取消成功/失败和耗时；日志只包含 uploadId 的哈希或截断标识、对象 key 的安全摘要和 traceId。

## Testing

- Core contract tests cover validation, duplicate parts, missing parts, completion idempotency and expiry.
- Local tests cover atomic part writes, restart-readable metadata, merge hash/length, traversal rejection and cleanup.
- MinIO/RustFS tests cover native upload-part signing and lifecycle against an ephemeral S3-compatible service.
- OSS tests cover SDK request construction, uploadId/partNumber binding, error mapping and lifecycle using mocks; real cloud integration remains opt-in.
- Starter MockMvc tests cover authorization, REST paths, 403/404/409/429 errors, body-not-read on limit rejection and provider isolation.
