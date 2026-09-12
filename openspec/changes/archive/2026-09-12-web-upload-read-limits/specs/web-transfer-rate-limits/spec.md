## ADDED Requirements

### Requirement: Web 上传并发限流
Starter SHALL 为 Web 上传提供独立的可配置最大并发数，默认值为 16。上传请求在读取请求体或写入 provider 前必须尝试获取上传许可；达到上限时 MUST 立即返回 HTTP 429，并设置 `Retry-After`，不得在组件内部排队。许可在成功、失败、异常和客户端断连后 MUST 释放。SDK 直接调用 `FileStorage.put` 不得消耗该 Web 上传许可。

#### Scenario: 上传并发达到上限
- **WHEN** 同时执行的 Web 上传数已达到 `web.max-concurrent-uploads`
- **THEN** 新上传立即返回 429 和 `Retry-After`，且请求体不会被组件读取

#### Scenario: 上传完成后释放许可
- **WHEN** Web 上传成功或因校验、provider、网络异常结束
- **THEN** 上传许可被释放，后续上传可以获得许可

#### Scenario: SDK 上传不受 Web 限流影响
- **WHEN** Web 上传许可全部占用时调用 SDK `FileStorage.put`
- **THEN** SDK 上传仍按 provider 和核心上传策略执行，不返回 Web 限流错误

### Requirement: Web 读取并发限流覆盖全部传输路径
Starter SHALL 为 Web 读取提供可配置最大并发数，默认值为 128。读取请求在选择流式、缓存文件或零拷贝传输路径前必须获取读取许可；达到上限时 MUST 返回 HTTP 429 和 `Retry-After`。同一许可 MUST 覆盖完整对象、单 Range、Local 文件、远程缓存文件和流式回退，并在响应完成、异常或客户端断连后释放。SDK 直接调用 `FileStorage.get` 不得消耗该 Web 读取许可。

#### Scenario: 读取并发达到上限
- **WHEN** 同时执行的 Web 读取数已达到 `web.max-concurrent-reads`
- **THEN** 新读取返回 429 和 `Retry-After`，且不打开 provider 对象流

#### Scenario: 零拷贝读取计入许可
- **WHEN** Local 文件或完整远程缓存文件使用容器零拷贝传输
- **THEN** 该传输持有读取许可，直到响应完成或失败后才释放

#### Scenario: 流式回退释放许可
- **WHEN** 零拷贝不可用并使用有界流式回退，且响应正常结束或客户端断连
- **THEN** 读取许可被释放，后续读取可以获得许可

### Requirement: Web 读取免组件用户凭证
Starter SHALL 对稳定 ID、真实 object key、下载和预览 Web 读取端点不调用 `FileStorageAccessPolicy` 或要求 Web 预签名凭证；读取仍 MUST 校验路径/稳定 ID格式并遵守 ETag、条件请求、单 Range、缓存头和对象不存在语义。Web 上传 MUST 继续执行预签名或直接访问授权。

#### Scenario: 稳定 ID 匿名读取
- **WHEN** 请求携带有效稳定 ID 但没有认证主体或读取签名
- **THEN** 组件读取对象并返回标准 Web 响应，不调用用户授权策略

#### Scenario: 真实 key 匿名读取
- **WHEN** 请求 `/read/key/**`、`/preview/**` 或 `/download/**` 且没有读取凭证
- **THEN** 组件按 object key 读取并返回标准 Web 响应，不因缺少读取凭证拒绝

#### Scenario: 上传仍需凭证
- **WHEN** Web 上传没有有效预签名且宿主直接访问授权拒绝
- **THEN** 上传被拒绝且不会写入对象

### Requirement: 限流配置校验与拒绝响应
Starter SHALL 暴露 `web.max-concurrent-reads` 和 `web.max-concurrent-uploads` 配置，两个值 MUST 为正整数。限流拒绝响应 MUST 使用 HTTP 429、`Retry-After` 和不包含凭证、签名 URL、对象内容或本地路径的稳定错误信息。

#### Scenario: 拒绝非法限流配置
- **WHEN** 任一 Web 并发配置为零或负数
- **THEN** 应用启动失败并报告 `INVALID_CONFIGURATION`

#### Scenario: 限流错误不泄露敏感信息
- **WHEN** Web 上传或读取因限流被拒绝
- **THEN** 错误响应和生产日志不包含签名、密钥、完整 URL、对象内容或本地路径
