# file-storage-configurable-read Specification

## Purpose
TBD - created by archiving change file-storage-configurable-read-and-key-access. Update Purpose after archive.
## Requirements
### Requirement: 可配置读取与缓存资源上限
Starter SHALL expose positive defaults and configuration bindings for Web read buffer size (`128KB`), maximum concurrent reads (`128`), cache fill buffer size (`128KB`), maximum concurrent fills (`4`), and cache queue capacity (`32`). Runtime components MUST use these values and MUST reject non-positive values during startup.

#### Scenario: 默认配置保护内存和并发
- **WHEN** no resource tuning values are configured
- **THEN** the starter uses `128KB`, `128`, `128KB`, `4`, and `32` respectively

### Requirement: 有界读取与缓存填充
Web object transfer MUST use native container file transfer when a safe local file source and compatible request are available; all other transfers MUST remain bounded streaming and MUST limit active response copies to the configured maximum. Cache fills MUST use the configured fixed worker count and bounded queue; queue saturation MUST not fail the user’s provider read and MUST not create an unbounded backlog. Native Tomcat `sendfile` MUST acquire the configured application read permit and release it after response completion or failure.

#### Scenario: 队列满时读取继续直通
- **WHEN** all cache workers and queue slots are occupied
- **THEN** a cache fill is rejected while the current object response continues from the provider
