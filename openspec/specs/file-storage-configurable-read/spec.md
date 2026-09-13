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

When `dinghai.file-storage.web.sendfile-binary-content-types-enabled` is true
(the default), a compression-capable request for a local-file-backed object MAY
use native Tomcat `sendfile` only for a provider-confirmed, strictly recognized
standard binary Content-Type. Compressible text, SVG, absent, malformed, or
unrecognized Content-Types MUST use bounded streaming. When the setting is
false, every compression-capable request MUST use bounded streaming. Requests
without compression capability retain the existing sendfile behavior.

#### Scenario: Binary type with gzip remains native-transfer compatible

- **WHEN** the setting is enabled and a local-file-backed object's confirmed
  Content-Type is `application/octet-stream`, `image/png`, `video/mp4`,
  `audio/mpeg`, `application/zip`, `application/pdf`, or `font/woff2`, and the
  request carries `Accept-Encoding: gzip`
- **THEN** the transfer registers Tomcat sendfile instead of starting a bounded
  streaming copy

#### Scenario: Text and SVG with gzip stream safely

- **WHEN** a local-file-backed object's confirmed Content-Type is `text/plain`,
  `application/json`, `application/javascript`, `application/xml`,
  `application/xhtml+xml`, or `image/svg+xml`, and the request carries
  `Accept-Encoding: gzip`
- **THEN** the transfer uses the bounded streaming response path

#### Scenario: Invalid or unknown type with gzip streams safely

- **WHEN** a local-file-backed object's confirmed Content-Type is absent,
  malformed, or not explicitly recognized, and the request carries
  `Accept-Encoding: gzip`
- **THEN** the transfer uses the bounded streaming response path

#### Scenario: Switch disabled restores conservative behavior

- **WHEN** `sendfile-binary-content-types-enabled` is false and a local binary
  object request carries `Accept-Encoding: gzip`
- **THEN** the transfer uses bounded streaming

#### Scenario: No compression capability preserves existing behavior

- **WHEN** a request carries `Accept-Encoding: identity` or no
  `Accept-Encoding` header
- **THEN** sendfile registration is decided by the remaining preconditions,
  regardless of the setting or response Content-Type

#### Scenario: 队列满时读取继续直通
- **WHEN** all cache workers and queue slots are occupied
- **THEN** a cache fill is rejected while the current object response continues from the provider
