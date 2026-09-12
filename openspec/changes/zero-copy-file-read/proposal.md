## Why

当前 Local 文件和远程缓存命中都通过 `InputStream` 与响应缓冲区复制，无法充分利用本地文件传输能力。远程 provider 首次读取需要保持低延迟流式返回，同时把小文件异步落地，后续读取再使用本地文件快速传输；Local 主存储则必须与可清理的临时缓存严格分离。

## What Changes

- 为本地文件和已完成的远程缓存副本增加零拷贝优先的 Web 传输路径。
- 保留 HTTPS、压缩、复杂 Range 或容器不支持时的流式回退路径。
- Local provider 不再创建二级缓存或定时清理器，主存储文件永不被缓存清理任务删除。
- MinIO/OSS provider 首次访问继续流式返回并异步落盘，缓存命中后可零拷贝读取。
- 为缓存目录增加来源隔离、元数据校验、清理边界和并发资源保护。
- 增加 Local、远程冷读/热读、Range、回退、并发和压测验证。

## Capabilities

### New Capabilities

- `zero-copy-file-transfer`: 本地文件和二级缓存副本的零拷贝优先传输与安全回退。
- `provider-cache-lifecycle`: Local 主存储与 MinIO/OSS 临时缓存的生命周期隔离。

### Modified Capabilities

- `file-storage-configurable-read`: 读取资源配置需要明确零拷贝优先和流式回退的边界。
- `file-storage-web-access`: Web 完整文件和单区间读取增加本地文件传输路径，同时保持现有条件请求和授权行为。
- `stable-file-access`: Local 二级缓存不再允许由显式配置开启，避免主存储与可清理缓存混淆。

## Impact

- 影响 `dinghai-file-storage-core` 的下载来源抽象、Local 适配器、Starter Web Controller、远程读取缓存和自动配置。
- 可能增加 Servlet 容器文件传输适配代码，但不引入必须的外部存储 SDK。
- 不改变 `FileStorage` 的现有流式调用方兼容性；零拷贝为可选优化路径。
