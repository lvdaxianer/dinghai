## ADDED Requirements

### Requirement: 宿主限流 key 解析扩展点
Starter SHALL 提供 `WebTransferRateLimitKeyResolver` 公共接口，宿主可基于当前 `HttpServletRequest` 返回限流 key。组件 MUST 不自行从 IP、JWT、Cookie、转发头或 API Key 推导 key，也不得记录或向客户端回显 resolver 返回的 key。

#### Scenario: 宿主按租户提供 key
- **WHEN** 宿主 resolver 根据当前请求返回非空租户 key
- **THEN** 组件使用该 key 施加可选的按 key 并发限制

### Requirement: Key 解析失败安全降级
当未注册 resolver、resolver 返回 `null` 或空白值、或 resolver 抛出运行时异常时，Web 上传与读取 SHALL 保持可用，并只执行全局并发限制。组件 MUST 不因该情况返回认证、授权或内部错误。

#### Scenario: Resolver 发生异常
- **WHEN** 宿主 resolver 在读取或上传请求中抛出运行时异常
- **THEN** 请求继续按全局并发限制执行，且不泄露 resolver 的异常信息

### Requirement: 按 key 的上传和读取公平并发限制
当 `web.max-concurrent-transfers-per-key` 为正数且 resolver 返回非空 key 时，组件 SHALL 在全局上传或读取许可之外，分别限制该 key 的同时上传数和读取数。达到按 key 上限的请求 MUST 返回 HTTP 429 与 `Retry-After`，不得读取上传请求体或打开读取对象流。上传和读取 SHALL 使用互不共享的 key 额度。

#### Scenario: 同一 key 上传达到上限
- **WHEN** 同一非空 key 的 Web 上传数达到 `web.max-concurrent-transfers-per-key`
- **THEN** 新上传返回 HTTP 429 和 `Retry-After`，且不会读取请求体

#### Scenario: 同一 key 读取达到上限
- **WHEN** 同一非空 key 的 Web 读取数达到 `web.max-concurrent-transfers-per-key`
- **THEN** 新读取返回 HTTP 429 和 `Retry-After`，且不打开对象流

#### Scenario: 上传和读取额度独立
- **WHEN** 同一 key 的读取额度已经全部占用
- **THEN** 该 key 的上传仍可在上传全局和每 key 上限内执行

### Requirement: Key 许可生命周期和状态回收
按 key 许可 SHALL 覆盖上传的完整请求体与 provider 写入、读取的流式复制、缓存文件和零拷贝传输。成功、失败、异常、客户端断连及 Tomcat sendfile 响应完成后 MUST 释放许可。无活跃许可的 key 状态 MUST 从内存中移除。SDK `FileStorage.put` 和 `FileStorage.get` 不得消耗按 key 许可。

#### Scenario: 零拷贝读取释放 key 许可
- **WHEN** 使用 Tomcat sendfile 的 key 限流读取响应完成或失败
- **THEN** 该 key 的读取许可被释放，后续同 key 读取可以获得许可

#### Scenario: 短生命周期 key 被回收
- **WHEN** 某 key 的最后一个上传或读取许可被释放
- **THEN** 组件不再保留该 key 的内存状态
