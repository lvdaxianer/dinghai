## Context

Local 文件是持久主数据，MinIO/OSS 的 `cache.root` 是可淘汰的二级副本。当前 Web 层统一从 `DownloadObject.stream()` 复制到 `StreamingResponseBody`，无法表达“这个对象已经是本地文件”的事实。Servlet 容器是否能使用内核 `sendfile` 取决于响应类型、连接协议和容器，因此实现必须提供零拷贝尝试和流式回退。

## Goals / Non-Goals

**Goals:**

- Local 主文件和远程缓存命中支持本地文件传输优先。
- 远程首次读取保持流式响应，并异步、有界地写入独立缓存目录。
- Local 不受缓存清理任务影响；远程缓存按 TTL 和并发边界清理。
- 保持授权、ETag、条件请求、单区间 Range 和既有 `FileStorage` 流式契约。

**Non-Goals:**

- 不承诺每个 Servlet 容器、HTTPS 或响应包装场景都能使用内核 sendfile。
- 不把整个远程对象缓存在 JVM 堆中。
- 不改变 OSS/MinIO 的对象一致性、权限或预签名协议。

## Decisions

1. **可选本地文件来源**：在不破坏 `FileStorage.get()` 的前提下增加可选能力，让 Local 和缓存实现返回经过根目录校验的 `Path`；没有该能力的 provider 继续只返回流。
2. **传输策略**：完整对象和单区间本地文件优先交给容器文件传输/`FileChannel.transferTo` 适配器；无法获得安全文件传输通道时回退现有有界 `InputStream` 复制。
3. **缓存生命周期**：仅 MinIO/OSS 装配 `ProviderFileReadCache` 和 `ProviderFileReadCacheCleaner`。Local 不创建二级缓存 Bean，且启动时拒绝 `local.root` 与 `cache.root` 重叠。
4. **远程冷读**：首次请求继续从 provider 获取流并立即响应；独立有界任务再次获取 provider 流写入临时文件，校验长度、ETag、修改时间后原子发布。热读只使用完整且匹配的缓存文件。
5. **清理与并发**：缓存填充使用固定线程数、有界队列、对象大小阈值；清理只扫描缓存 sidecar，并跳过填充中的临时文件。清理失败不影响读取请求，但需保留可诊断上下文。

## Risks / Trade-offs

- [容器不支持零拷贝] → 自动回退流式复制，并以集成测试验证响应内容不变。
- [远程对象首次访问产生双读] → 使用对象大小阈值和有界填充并发，后续可单独评估共享流复制。
- [缓存目录误指向主存储] → 启动时规范化并拒绝目录相同或互相包含的配置。
- [缓存副本过期或元数据变化] → 读取前校验 sidecar，失效时删除副本并回源。

## Migration Plan

1. 先增加抽象和测试，不改变默认流式回退行为。
2. 再启用 Local/缓存的零拷贝优先路径和 provider 条件装配。
3. 通过单元、Web 集成、MinIO 集成和并发压测验证后发布。
4. 若容器兼容性出现问题，关闭零拷贝开关或回退流式传输，不影响对象数据。

## Open Questions

- 需要针对项目实际部署的 Servlet 容器确认 sendfile 的具体 API 和 HTTPS 行为。
- 是否在后续版本增加按缓存总容量淘汰，当前首版只定义 TTL 清理。
