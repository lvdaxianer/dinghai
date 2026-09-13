## MODIFIED Requirements

### Requirement: 有界读取与缓存填充
Web object transfer MUST use native container file transfer when a safe local file source and compatible request are available; all other transfers MUST remain bounded streaming and MUST limit active response copies to the configured maximum. Cache fills MUST use the configured fixed worker count and bounded queue; queue saturation MUST not fail the user’s provider read and MUST not create an unbounded backlog. Native Tomcat `sendfile` MUST acquire the configured application read permit and release it after response completion or failure. A request carrying a compression-capable `Accept-Encoding` header MUST remain sendfile-compatible when the provider-confirmed response Content-Type is an incompressible binary type (images except SVG, video, audio, archives, PDF, fonts, WebAssembly, or `application/octet-stream`); it MUST fall back to bounded streaming when the Content-Type is compressible text (for example `text/*`, `application/json`, `application/javascript`, `application/xml`, `application/xhtml+xml`, `image/svg+xml`) or when the Content-Type is absent or unrecognized.

#### Scenario: 队列满时读取继续直通
- **WHEN** all cache workers and queue slots are occupied
- **THEN** a cache fill is rejected while the current object response continues from the provider

#### Scenario: 二进制类型携带 gzip 接受头仍走 sendfile
- **WHEN** a request for a local-file-backed object whose confirmed Content-Type is an incompressible binary type (such as `application/octet-stream`, `image/png`, `video/mp4`, `audio/mpeg`, `application/zip`, `application/pdf`, or `font/woff2`) carries `Accept-Encoding: gzip`
- **THEN** the transfer registers Tomcat sendfile with the request instead of starting a bounded streaming copy

#### Scenario: 文本类型携带 gzip 接受头回退流式
- **WHEN** a request for a local-file-backed object whose confirmed Content-Type is a compressible text type (such as `text/plain`, `application/json`, `application/javascript`, `application/xml`, `application/xhtml+xml`, or `image/svg+xml`) carries `Accept-Encoding: gzip`
- **THEN** the transfer falls back to the bounded streaming response path

#### Scenario: 未声明或无法识别的内容类型保守回退
- **WHEN** a request for a local-file-backed object whose confirmed Content-Type is absent or cannot be recognized carries `Accept-Encoding: gzip`
- **THEN** the transfer falls back to the bounded streaming response path

#### Scenario: identity 或缺失的 Accept-Encoding 行为不变
- **WHEN** a request carries `Accept-Encoding: identity` or no `Accept-Encoding` header at all
- **THEN** sendfile registration is decided by the remaining preconditions alone, regardless of the response Content-Type

#### Scenario: 非本地 provider 始终流式传输
- **WHEN** the requested object is not backed by a safe local file source
- **THEN** the transfer uses the bounded streaming response path regardless of Content-Type or `Accept-Encoding`
