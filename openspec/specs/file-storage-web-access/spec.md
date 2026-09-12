# file-storage-web-access Specification

## Purpose

文件存储 Web 集成提供受保护的预签名访问、在线预览、HTTP 缓存、Range 响应和受控异步流式传输。

## Requirements

### Requirement: 受限预签名上传与下载
组件 SHALL 为本地和 MinIO 提供统一的预签名上传、下载 URL 模型。预签名结果 MUST 声明 URI、HTTP 方法、过期时间、必需请求头与适用的表单字段；上传签名 MUST 限制 key、过期时间、最大长度和内容类型，下载签名 MUST 限制 key、GET 方法、过期时间与 `inline` / `attachment` 响应模式。预签名 URL MUST 有配置化最大有效期，且在有效期内可重放。

#### Scenario: 生成受限上传地址
- **WHEN** 调用方为允许类型且未超过大小限制的 key 请求预签名上传
- **THEN** 返回的模型包含客户端完成上传所需的 URL、方法和头/表单字段，且服务端拒绝签名范围外的 key、类型或长度

#### Scenario: 拒绝篡改或过期的本地签名
- **WHEN** 客户端以篡改的 key、错误 HTTP 方法或过期签名访问本地预签名端点
- **THEN** 端点拒绝请求，且不读取或写入任何对象内容

### Requirement: 本地预签名采用签名端点
当 provider 为 `local` 时，Starter SHALL 使用 HMAC-SHA-256 签名的 HTTP 端点实现预签名 URL。签名载荷 MUST 包含版本、HTTP 方法、规范化 key、到期时间、响应模式、上传限制与缓存策略摘要，并使用常量时间比较验证。生产模式缺失签名密钥 MUST 使应用启动失败；签名密钥和完整预签名 URL MUST NOT 出现在日志或错误响应中。

#### Scenario: 本地有效预签名下载
- **WHEN** 客户端在有效期内以签名 URL 请求一个存在对象
- **THEN** Starter 从本地适配器流式返回对象，并应用签名中固定的响应模式和缓存策略

### Requirement: 默认拒绝直接 Web 访问
Starter SHALL 默认以 `web.enabled=true`、`web.path-prefix=/files`、`web.direct-access=false` 注册文件端点。Web 上传无有效预签名凭据时 MUST 被拒绝；只有 `direct-access=true` 且应用提供 `FileStorageAccessPolicy` 时，上传端点才能根据宿主应用认证主体允许直接上传。Web 下载、预览及真实 key 读取不要求组件用户凭证或直接访问授权，但仍受 Web 读取并发限制。

#### Scenario: 默认配置拒绝无签名预览
- **WHEN** 客户端在默认配置下无预签名凭据访问 `/files/preview/{key}`
- **THEN** 请求被拒绝，且不会公开对象内容

#### Scenario: 应用策略允许直接下载
- **WHEN** 应用显式启用 direct access、提供 `FileStorageAccessPolicy`，且该策略允许当前认证主体下载对象
- **THEN** 下载端点返回对象；策略拒绝时端点拒绝请求

#### Scenario: 默认配置拒绝无签名上传
- **WHEN** 客户端在默认配置下无预签名凭据访问 `/files/upload/{key}`
- **THEN** 请求被拒绝，且不会公开或写入对象

#### Scenario: 匿名读取真实 object key
- **WHEN** 客户端不携带读取签名或认证主体访问 `/files/read/key/{key}`、预览或下载端点
- **THEN** 端点按 object key 读取对象，并不调用 `FileStorageAccessPolicy`

### Requirement: 在线预览与 HTTP/CDN 缓存响应
所有 `FileStorage` provider 的下载/预览端点 SHALL 设置对象的 Content-Type、ETag、Last-Modified、Cache-Control、Expires 与 Content-Disposition。预览 MUST 使用 `inline`，下载 MUST 使用 `attachment`；默认缓存策略 MUST 为 `private, max-age=0`，公开或 immutable 缓存 MUST 显式指定。端点 MUST 对 `If-None-Match` 与 `If-Modified-Since` 返回 304，并支持单字节范围请求以支持浏览器媒体预览。MinIO 预签名下载和稳定读取 ID 下载 MUST 固定相同的响应模式与允许的缓存响应语义。对于可直接解析的本地内容，端点 SHOULD 使用通道传输；对于对象存储内容，端点 MUST 使用有界缓冲流式转发，且不得将完整对象缓存至 JVM 内存。所有实际对象传输路径 MUST 计入 `web.max-concurrent-reads`。

#### Scenario: 本地文件传输自动回退

- **WHEN** Local 主文件或已校验远程缓存命中，但请求使用 TLS、响应压缩、非 Tomcat 容器或容器拒绝文件传输
- **THEN** 端点自动使用有界流式复制，并保持内容、条件请求和 Range 语义

### Requirement: 零拷贝传输并发边界

流式回退和原生 Tomcat `sendfile` MUST 使用 `web.max-concurrent-reads` permit，并覆盖从传输开始到响应完成或失败的完整生命周期。

#### Scenario: 缓存命中返回 304
- **WHEN** 客户端以当前对象的 ETag 发送 `If-None-Match` 请求
- **THEN** 本地端点返回 304 且不传输对象响应体

#### Scenario: 预览返回 inline 与范围响应
- **WHEN** 客户端通过有效预签名 URL 或稳定读取 ID 预览一个存在的视频或 PDF，并发送单个有效 Range 请求
- **THEN** 响应使用 `Content-Disposition: inline`，并以正确的 206 头和所请求的字节范围返回内容

### Requirement: Web 流式响应使用有界执行器
Starter SHALL 为 `StreamingResponseBody` 配置独立、有界且可优雅关闭的异步执行器。执行器 MUST 具备有限的核心线程数、最大线程数和队列容量，并使用带业务含义的线程名称；不得依赖无界的默认简单异步执行器或无限队列。

#### Scenario: Web 执行器具备明确容量
- **WHEN** local provider 且 Web 集成启用时完成自动装配
- **THEN** MVC 异步支持使用文件存储专用执行器，且其核心线程、最大线程和队列容量均为有限正数

#### Scenario: 读取并发达到上限
- **WHEN** Web 读取许可已全部占用
- **THEN** 新的下载、预览或真实 key 读取返回 HTTP 429 和 `Retry-After`
