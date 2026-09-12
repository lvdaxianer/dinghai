## MODIFIED Requirements

### Requirement: 默认拒绝直接 Web 访问
Starter SHALL 默认以 `web.enabled=true`、`web.path-prefix=/files`、`web.direct-access=false` 注册文件端点。Web 上传无有效预签名凭据时 MUST 被拒绝；只有 `direct-access=true` 且应用提供 `FileStorageAccessPolicy` 时，上传端点才能根据宿主应用认证主体允许直接上传。Web 下载、预览及真实 key 读取不要求组件用户凭证或直接访问授权，但仍受 Web 读取并发限制。

#### Scenario: 默认配置拒绝无签名上传
- **WHEN** 客户端在默认配置下无预签名凭据访问 `/files/upload/{key}`
- **THEN** 请求被拒绝，且不会公开或写入对象

#### Scenario: 匿名读取真实 object key
- **WHEN** 客户端不携带读取签名或认证主体访问 `/files/read/key/{key}`、预览或下载端点
- **THEN** 端点按 object key 读取对象，并不调用 `FileStorageAccessPolicy`

### Requirement: 在线预览与 HTTP/CDN 缓存响应
所有 `FileStorage` provider 的下载/预览端点 SHALL 设置对象的 Content-Type、ETag、Last-Modified、Cache-Control、Expires 与 Content-Disposition。预览 MUST 使用 `inline`，下载 MUST 使用 `attachment`；默认缓存策略 MUST 为 `private, max-age=0`，公开或 immutable 缓存 MUST 显式指定。端点 MUST 对 `If-None-Match` 与 `If-Modified-Since` 返回 304，并支持单字节范围请求以支持浏览器媒体预览。MinIO 预签名下载和稳定读取 ID 下载 MUST 固定相同的响应模式与允许的缓存响应语义。对于可直接解析的本地内容，端点 SHOULD 使用通道传输；对于对象存储内容，端点 MUST 使用有界缓冲流式转发，且不得将完整对象缓存至 JVM 内存。所有实际对象传输路径 MUST 计入 `web.max-concurrent-reads`。

#### Scenario: 读取并发达到上限
- **WHEN** Web 读取许可已全部占用
- **THEN** 新的下载、预览或真实 key 读取返回 HTTP 429 和 `Retry-After`
