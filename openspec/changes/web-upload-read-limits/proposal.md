## Why

当前 Web 上传与读取共享部分访问模型，无法清晰表达两者不同的安全边界：上传必须由调用方持有凭证并受到组件资源保护，而读取可以作为匿名能力提供给浏览器，同时仍需防止并发请求耗尽服务资源。零拷贝传输合并后，读取限流还必须覆盖本地文件和 sendfile 路径，不能只限制流式回退。

## What Changes

- Web 上传继续要求预签名或宿主直接访问授权，并新增可配置的最大并发上传数。
- Web 读取端点不再要求组件用户凭证或直接访问授权，但保留稳定 ID/对象 key 解析和现有 HTTP 条件、Range、缓存语义。
- Web 读取保留可配置最大并发读取数，并确保流式传输、缓存文件传输和零拷贝传输统一受限。
- 限流拒绝统一返回 HTTP 429，并提供 `Retry-After` 响应头；SDK 直接调用不经过 Web 限流。
- 增加上传/读取限流、许可释放、凭证边界和零拷贝路径的测试与配置文档。

## Capabilities

### New Capabilities
- `web-transfer-rate-limits`: Web 上传和读取的可配置并发限流、拒绝响应及资源释放契约。

### Modified Capabilities
- `file-storage-web-access`: 上传凭证边界和 Web 读取授权行为调整，同时保留 HTTP 传输语义。
- `stable-file-access`: 稳定 ID 读取继续免组件用户鉴权，并纳入统一读取并发限制。

## Impact

- 影响 `dinghai-file-storage-spring-boot-starter` 的 Web Controller、自动配置、属性绑定和异常处理。
- 影响 Web 端点配置文档及单元、MVC 集成测试。
- 不改变 `FileStorage` SDK 接口、provider 凭证、对象数据格式或远程缓存协议。
- 依赖零拷贝 worktree 合并后的读取传输实现；本变更不直接修改该 worktree。
