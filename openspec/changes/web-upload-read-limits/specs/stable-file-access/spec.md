## MODIFIED Requirements

### Requirement: 无组件登录鉴权的私有对象流式读取
Starter SHALL 提供以稳定文件 ID 为路径参数的读取、预览和下载端点。端点 MUST 在不调用组件用户鉴权策略的前提下解密 ID、从当前 `FileStorage` provider 流式读取私有对象，并且不得暴露 bucket 地址、对象 key 或存储密钥。读取端点 MUST 支持 ETag、Last-Modified、Cache-Control、304 和单 Range 响应；预览 MUST 为 `inline`，下载 MUST 为 `attachment`。所有稳定 ID 读取 MUST 计入可配置的 `web.max-concurrent-reads` 并发限制。

#### Scenario: 稳定读取并发达到上限
- **WHEN** 同时执行的稳定 ID 读取数达到 `web.max-concurrent-reads`
- **THEN** 新请求返回 HTTP 429 和 `Retry-After`，且不打开 provider 对象流
