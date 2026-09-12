# web-transfer-rate-limits Specification

## Purpose

为 Web 文件传输提供独立、可配置且不阻塞排队的上传和读取并发保护，隔离 SDK 调用并覆盖流式、缓存文件和零拷贝等完整传输生命周期。

## Requirements

### Requirement: Web 上传并发限流
Starter SHALL 为 Web 上传提供独立的可配置最大并发数，默认值为 16。上传请求在读取请求体或写入 provider 前必须尝试获取上传许可；达到上限时 MUST 立即返回 HTTP 429，并设置 `Retry-After`，不得在组件内部排队。许可在成功、失败、异常和客户端断连后 MUST 释放。SDK 直接调用 `FileStorage.put` 不得消耗该 Web 上传许可。

#### Scenario: 上传达到上限
- **WHEN** 同时执行的 Web 上传数达到 `web.max-concurrent-uploads`
- **THEN** 新上传返回 HTTP 429 和 `Retry-After`，且请求体不会被读取

### Requirement: Web 读取并发限流覆盖全部传输路径
Starter SHALL 为 Web 读取提供可配置最大并发数，默认值为 128。读取请求在选择流式、缓存文件或零拷贝传输路径前必须获取读取许可；达到上限时 MUST 返回 HTTP 429 和 `Retry-After`。同一许可 MUST 覆盖完整对象、单 Range、Local 文件、远程缓存文件和流式回退，并在响应完成、异常或客户端断连后释放。SDK 直接调用 `FileStorage.get` 不得消耗该 Web 读取许可。

#### Scenario: 读取达到上限
- **WHEN** 同时执行的 Web 读取数达到 `web.max-concurrent-reads`
- **THEN** 新读取返回 HTTP 429 和 `Retry-After`，且不打开 provider 对象流

### Requirement: Web 读取免组件用户凭证
Starter SHALL 对稳定 ID、真实 object key、下载和预览 Web 读取端点不调用 `FileStorageAccessPolicy` 或要求 Web 预签名凭证；读取仍 MUST 校验路径/稳定 ID格式并遵守 ETag、条件请求、单 Range、缓存头和对象不存在语义。Web 上传 MUST 继续执行预签名或直接访问授权。

#### Scenario: 匿名读取
- **WHEN** 请求携带有效稳定 ID 或真实 object key 且没有读取凭证
- **THEN** 组件返回标准 Web 响应，不调用用户授权策略

### Requirement: 限流配置校验与拒绝响应
Starter SHALL 暴露 `web.max-concurrent-reads` 和 `web.max-concurrent-uploads` 配置，两个值 MUST 为正整数。限流拒绝响应 MUST 使用 HTTP 429、`Retry-After` 和不包含凭证、签名 URL、对象内容或本地路径的稳定错误信息。

#### Scenario: 拒绝非法配置
- **WHEN** 任一 Web 并发配置为零或负数
- **THEN** 应用启动失败并报告 `INVALID_CONFIGURATION`
