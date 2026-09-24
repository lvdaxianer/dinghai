## MODIFIED Requirements

### Requirement: 在线预览与 HTTP/CDN 缓存响应
所有 `FileStorage` provider 的下载/预览端点 SHALL 设置对象的 Content-Type、ETag、Last-Modified、Cache-Control、Expires 与 Content-Disposition。预览 MUST 使用 `inline`，下载 MUST 使用 `attachment`；默认缓存策略 MUST 为 `private, max-age=0`，公开或 immutable 缓存 MUST 显式指定。端点 MUST 对 `If-None-Match` 与 `If-Modified-Since` 返回 304，并支持单字节范围请求以支持浏览器媒体预览。MinIO 预签名下载和稳定读取 ID 下载 MUST 固定相同的响应模式与允许的缓存响应语义。对于可直接解析的本地内容，端点 SHOULD 使用通道传输；对于对象存储内容，端点 MUST 使用有界缓冲流式转发，且不得将完整对象缓存至 JVM 内存。所有实际对象传输路径 MUST 计入 `web.max-concurrent-reads`。

#### Scenario: 本地文件传输自动回退
- **WHEN** Local 主文件或已校验远程缓存命中，但请求使用 TLS、响应压缩、非 Tomcat 容器、容器拒绝文件传输，或最终绝对路径包含非 ASCII 字符
- **THEN** 端点自动使用有界流式复制，并保持内容、内容长度、条件请求和 Range 语义
