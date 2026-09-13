# sendfile-binary-content-types

## Why

Web 下载的零拷贝优化（Tomcat sendfile）目前对浏览器流量完全失效：`TomcatSendfileTransfer` 只要看到请求携带 `Accept-Encoding: gzip` 就回退到 JVM 流式拷贝，而浏览器无条件携带该头且 JS 无法移除——即使下载的是 JPEG/MP4/zip 这类任何服务端都不会压缩的二进制资源。本机实测（500MB 文件）：零拷贝吞吐约为流式回退的 4~5 倍（单流 9.2 GB/s vs 2.1 GB/s；8 并发聚合 9.22 GB/s vs 1.78 GB/s），每 GB 传输的 JVM CPU 消耗降低约 13.6 倍（0.106s vs 1.443s）。收窄回退条件后，大文件下载场景的 CPU 开销与并发容量可显著改善。

## What Changes

- 收窄 sendfile 回退判断：响应 Content-Type 属于"不会被压缩中间件处理"的二进制类型（图片、视频、音频、压缩包、PDF、字体等内置清单）时，即使请求携带 `Accept-Encoding: gzip` 也照常注册 Tomcat sendfile。
- 文本类/可压缩类型（`text/*`、`application/json`、`application/javascript`、`application/xml`、SVG 等）维持现状：携带 `Accept-Encoding: gzip` 时回退为 JVM 有界流式拷贝。
- 不可识别或未声明 Content-Type 的对象维持现状回退（保守默认）。
- 其余 sendfile 前置条件不变：仅本地 provider（`LocalFileSource`）、非安全连接、范围合法、普通文件等。
- 无配置项变更，无 API 变更，行为变更仅体现为二进制资源下载在浏览器场景下重新命中零拷贝。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `file-storage-configurable-read`："有界读取与缓存填充"需求中"compatible request"（兼容请求）的判定发生变化——`Accept-Encoding: gzip` 不再无条件使请求不兼容，仅当响应 Content-Type 属于可压缩文本类型时才回退为流式传输。

## Impact

- 代码：`dinghai-file-storage/spring-boot-starter` 模块的 `TomcatSendfileTransfer`（回退判断收窄）与 `FileStorageWebController.stream(...)`（向注册逻辑传入响应 Content-Type）。
- 测试：新增/调整 `FileStorageWebControllerTest` 用例，覆盖"二进制类型 + gzip 头仍走 sendfile"、"文本类型 + gzip 头回退流式"、"无 Content-Type 回退流式"。
- 文档：`dinghai-file-storage/README.md` / `README_EN.md` 的下载/设计要点段落补充零拷贝生效条件。
- 兼容性：无 BREAKING；端点、配置键、SDK 契约均不变。
- 风险：若宿主应用自定义了会压缩二进制响应的中间件，sendfile 直出可能与压缩头冲突——内置清单只收录通用"永不压缩"类型，且本组件自身不注册压缩过滤器，风险可控。
