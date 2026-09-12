## MODIFIED Requirements

### Requirement: Web 对象读取、预览与缓存

Web 读取、在线预览和下载 SHALL 保持现有授权、稳定 ID、真实 key、条件请求、缓存头和单区间 Range 行为。对象来源为 Local 主文件或已校验的远程缓存文件时 MUST 优先尝试零拷贝或等价本地文件传输；远程首次读取、缓存未完成或零拷贝不可用时 MUST 使用有界流式响应。Local 主存储不得因远程缓存清理策略被删除。

#### Scenario: 缓存命中优先本地文件传输

- **WHEN** MinIO/OSS 的缓存副本完整且 sidecar 元数据匹配
- **THEN** Web 读取优先从缓存文件传输，并保留对象元数据和条件响应

#### Scenario: 远程首次访问保持流式

- **WHEN** MinIO/OSS 对象没有完整缓存副本
- **THEN** 客户端立即获得远程流式响应，缓存填充异步执行且不阻塞当前响应

#### Scenario: 零拷贝失败保持回退

- **WHEN** 本地文件传输能力不可用
- **THEN** 请求回退到有界流式响应，不改变状态码、响应头和内容
