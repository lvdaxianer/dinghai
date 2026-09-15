## Why

当前文件存储仅支持单请求整对象上传，大文件会受到 HTTP 请求时长、网络抖动、应用实例带宽和当前 100 MB 上传上限的限制。需要为 Local、MinIO、RustFS 和阿里云 OSS 提供统一的可恢复分片上传能力，让客户端直传分片并支持断点续传、重试和显式完成或取消。

## What Changes

- 在 `core` 增加后端中立的 multipart upload 会话、分片、初始化、签发、查询、完成和取消契约。
- 为 Local 实现受限临时目录分片存储，完成时校验分片顺序、长度和总大小，并原子移动为正式对象。
- 为 MinIO/RustFS 使用 S3 Multipart Upload；为阿里云 OSS 使用 OSS Multipart Upload；分片上传优先返回 provider 原生预签名地址。
- 在 Spring Boot Starter 增加初始化、分片预签名、已上传分片查询、完成和取消接口，并沿用现有访问策略、稳定错误和限流边界。
- 增加上传会话过期清理、幂等完成/取消、分片大小与总大小校验、跨租户/对象 key 归属校验及安全错误映射。
- 保持现有 `FileStorage.put`、普通预签名上传和下载契约向后兼容；不把大文件内容缓存到应用内存。

## Capabilities

### New Capabilities

- `multipart-upload`: 统一的分片上传生命周期、客户端直传协议、Local 临时分片实现和 Spring Web 接口。

### Modified Capabilities

- `file-storage`: 扩展四个 provider 的上传能力和 Starter 资源限制，但保持现有整对象操作兼容。

## Impact

- 受影响模块：`dinghai-file-storage/core`、`local`、`minio`、`rustfs`、`aliyun-oss`、`spring-boot-starter`、测试和中英文文档。
- 新增 provider SDK 调用：MinIO SDK 的 `createMultipartUpload/uploadPart/completeMultipartUpload/abortMultipartUpload/listParts`，OSS V2 SDK 对应 Multipart API。
- 新增配置：分片大小、最大分片数、multipart 会话 TTL、并发上传限制和 Local 临时根目录；生产环境不引入默认密钥或自动创建 bucket。
- 不新增数据库表；远端会话状态以对象存储为准，Local 仅保存临时分片和受校验的本地会话元数据。
