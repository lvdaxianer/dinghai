## MODIFIED Requirements

### Requirement: 无组件登录鉴权的私有对象流式读取
Starter SHALL 在 `{path-prefix}{stable-read-path}/{identifier}` 及配置稳定路径下的预览和下载端点提供以稳定文件 ID 为路径参数的读取。默认路径 MUST 为 `/files/read/{identifier}`。端点 MUST 在不调用组件用户鉴权策略的前提下解密 ID、从当前 `FileStorage` provider 流式读取私有对象，并且不得暴露 bucket 地址、对象 key 或存储密钥。读取端点 MUST 支持 ETag、Last-Modified、Cache-Control、304 和单 Range 响应；预览 MUST 为 `inline`，下载 MUST 为 `attachment`。所有稳定 ID 读取 MUST 计入可配置的 `web.max-concurrent-reads` 并发限制。

#### Scenario: 私有 MinIO 对象通过稳定 ID 预览
- **WHEN** 浏览器请求有效稳定 ID 对应的配置预览地址
- **THEN** Starter 以 `inline` 流式返回内容和缓存/Range 响应头，浏览器不直接访问 bucket

#### Scenario: 稳定读取地址不使用组件用户授权
- **WHEN** 嵌入平台将有效稳定读取地址交给浏览器且请求不带组件认证主体
- **THEN** Starter 仅校验稳定 ID 后读取对象，不调用 `FileStorageAccessPolicy`

#### Scenario: 稳定读取并发达到上限
- **WHEN** 同时执行的稳定 ID 读取数达到 `web.max-concurrent-reads`
- **THEN** 新请求返回 HTTP 429 和 `Retry-After`，且不打开 provider 对象流
