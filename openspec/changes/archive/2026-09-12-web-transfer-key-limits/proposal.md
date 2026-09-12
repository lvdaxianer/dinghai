## Why

全局上传和读取并发上限能保护组件资源，但单个调用方仍可占满全部许可。宿主应用拥有用户、租户、IP 与 API Key 等身份上下文，需要一个不绑定具体身份模型的公平隔离扩展点。

## What Changes

- 新增宿主提供的 Web 传输限流 key 解析接口。
- 在既有全局上传和读取并发上限之外，按非空 key 施加独立的上传、读取并发上限。
- 未配置解析器、解析为空白 key 或解析异常时，安全降级为仅使用既有全局限流。
- 回收无活跃传输的 key 状态，避免高基数调用方造成常驻内存增长。

## Capabilities

### New Capabilities
- `web-transfer-key-limits`: 宿主定义的 key 驱动 Web 上传和读取公平并发隔离。

### Modified Capabilities
- `web-transfer-rate-limits`: 全局 Web 传输限流扩展为可选的按 key 并发限制。

## Impact

- 新增 starter 公共扩展接口与 Web 配置属性。
- 修改 `WebTransferLimiter`、`FileStorageWebController`、自动装配及其测试。
- 更新中英文 README 和限流规范；不增加第三方依赖，也不改变 SDK 调用路径。
