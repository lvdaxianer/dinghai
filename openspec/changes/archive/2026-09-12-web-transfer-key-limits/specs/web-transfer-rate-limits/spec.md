## MODIFIED Requirements

### Requirement: Web 上传并发限流
Starter SHALL 为 Web 上传提供独立的可配置最大并发数，默认值为 16。上传请求在读取请求体或写入 provider 前必须尝试获取上传许可；达到全局上限时 MUST 立即返回 HTTP 429，并设置 `Retry-After`，不得在组件内部排队。许可在成功、失败、异常和客户端断连后 MUST 释放。SDK 直接调用 `FileStorage.put` 不得消耗该 Web 上传许可。当宿主注册 `WebTransferRateLimitKeyResolver`、其返回非空 key 且 `web.max-concurrent-transfers-per-key` 为正数时，上传还 MUST 获取该 key 的独立上传许可。

#### Scenario: 上传达到全局上限
- **WHEN** 同时执行的 Web 上传数达到 `web.max-concurrent-uploads`
- **THEN** 新上传返回 HTTP 429 和 `Retry-After`，且请求体不会被读取

#### Scenario: 上传达到每 key 上限
- **WHEN** 同一 resolver key 的 Web 上传数达到 `web.max-concurrent-transfers-per-key`
- **THEN** 新上传返回 HTTP 429 和 `Retry-After`，且全局上传许可被立即归还

### Requirement: Web 读取并发限流覆盖全部传输路径
Starter SHALL 为 Web 读取提供可配置最大并发数，默认值为 128。读取请求在选择流式、缓存文件或零拷贝传输路径前必须获取读取许可；达到上限时 MUST 返回 HTTP 429 和 `Retry-After`。同一许可 MUST 覆盖完整对象、单 Range、Local 文件、远程缓存文件和流式回退，并在响应完成、异常或客户端断连后释放。SDK 直接调用 `FileStorage.get` 不得消耗该 Web 读取许可。当宿主注册 `WebTransferRateLimitKeyResolver`、其返回非空 key 且 `web.max-concurrent-transfers-per-key` 为正数时，读取还 MUST 获取该 key 的独立读取许可。

#### Scenario: 读取达到全局上限
- **WHEN** 同时执行的 Web 读取数达到 `web.max-concurrent-reads`
- **THEN** 新读取返回 HTTP 429 和 `Retry-After`，且不打开 provider 对象流

#### Scenario: 读取达到每 key 上限
- **WHEN** 同一 resolver key 的 Web 读取数达到 `web.max-concurrent-transfers-per-key`
- **THEN** 新读取返回 HTTP 429 和 `Retry-After`，且全局读取许可被立即归还
