## MODIFIED Requirements

### Requirement: 在线预览与 HTTP/CDN 缓存响应
文件存储读取/预览端点 SHALL 为所有 `FileStorage` provider 设置对象的 Content-Type、ETag、Last-Modified、Cache-Control、Expires 与 Content-Disposition。预览 MUST 使用 `inline`，下载 MUST 使用 `attachment`；默认缓存策略 MUST 为 `private, max-age=0`，公开或 immutable 缓存 MUST 显式指定。端点 MUST 对 `If-None-Match` 与 `If-Modified-Since` 返回 304，并支持单字节范围请求以支持浏览器媒体预览。MinIO 预签名下载 MUST 固定相同的响应模式与允许的缓存响应语义。对于可直接解析的本地内容，端点 SHOULD 使用通道传输；对于对象存储内容，端点 MUST 使用有界缓冲流式转发，且不得将完整对象缓存至 JVM 内存。

#### Scenario: 缓存命中返回 304
- **WHEN** 客户端以当前对象的 ETag 发送 `If-None-Match` 请求
- **THEN** 端点返回 304 且不传输对象响应体

#### Scenario: 预览返回 inline 与范围响应
- **WHEN** 客户端通过有效预签名 URL 或稳定读取 ID 预览一个存在的视频或 PDF，并发送单个有效 Range 请求
- **THEN** 响应使用 `Content-Disposition: inline`，并以正确的 206 头和所请求的字节范围返回内容
