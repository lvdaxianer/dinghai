## MODIFIED Requirements

### Requirement: 在线预览与 HTTP/CDN 缓存响应
所有 `FileStorage` provider 的下载/预览端点 SHALL 设置对象的 Content-Type、ETag、Last-Modified、Cache-Control、Expires 与 Content-Disposition。预览 MUST 使用 `inline`，下载 MUST 使用 `attachment`；Content-Disposition 的文件名 MUST 从已校验 object key 提取最后一个路径段，不得包含完整目录路径；文件名包含非 ASCII 字符时 MUST 使用 RFC 兼容的 UTF-8 编码。默认缓存策略 MUST 为 `private, max-age=0`，公开或 immutable 缓存 MUST 显式指定。端点 MUST 对 `If-None-Match` 与 `If-Modified-Since` 返回 304，并支持单字节范围请求以支持浏览器媒体预览。MinIO 预签名下载和稳定读取 ID 下载 MUST 固定相同的响应模式与允许的缓存响应语义。对于可直接解析的本地内容，端点 SHOULD 使用通道传输；对于对象存储内容，端点 MUST 使用有界缓冲流式转发，且不得将完整对象缓存至 JVM 内存。所有实际对象传输路径 MUST 计入 `web.max-concurrent-reads`。

#### Scenario: 本地文件传输自动回退

- **WHEN** Local 主文件或已校验远程缓存命中，但请求使用 TLS、响应压缩、非 Tomcat 容器、容器拒绝文件传输，或最终绝对路径包含非 ASCII 字符
- **THEN** 端点自动使用有界流式复制，并保持内容、内容长度、条件请求和 Range 语义

#### Scenario: 多级 object key 只生成 basename

- **WHEN** 客户端读取 `model-version/2109461736481492992/V0/index.gltf` 或 `origin/test/model.json`
- **THEN** 响应的 `Content-Disposition` 只包含 `index.gltf` 或 `model.json`，不得包含前级目录

#### Scenario: inline 和 attachment 使用正确文件名

- **WHEN** 客户端分别访问 inline 预览和 attachment 下载端点
- **THEN** 两个响应分别保持 `inline` 与 `attachment`，且都使用对应 object key 的最后文件名

#### Scenario: 中文文件名使用 UTF-8 响应头

- **WHEN** object key 的最后一段包含中文文件名
- **THEN** 请求成功，响应头使用 RFC 兼容的 UTF-8 文件名参数，且不会抛出非法 header 字符异常

#### Scenario: 路径穿越不能绕过 object key 校验

- **WHEN** 客户端使用 `../`、编码后的 `..` 或反斜杠构造 object key
- **THEN** 请求在对象读取前被拒绝，且不得通过 basename 提取逻辑访问其他路径

### Requirement: 零拷贝传输并发边界

流式回退和原生 Tomcat `sendfile` MUST 使用 `web.max-concurrent-reads` permit，并覆盖从传输开始到响应完成或失败的完整生命周期。

#### Scenario: 缓存命中返回 304

- **WHEN** 客户端以当前对象的 ETag 发送 `If-None-Match` 请求
- **THEN** 本地端点返回 304 且不传输对象响应体

#### Scenario: 预览返回 inline 与范围响应

- **WHEN** 客户端通过有效预签名 URL 或稳定读取 ID 预览一个存在的视频或 PDF，并发送单个有效 Range 请求
- **THEN** 响应使用 `Content-Disposition: inline`，并以正确的 206 头和所请求的字节范围返回内容
