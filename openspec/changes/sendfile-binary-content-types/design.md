# sendfile-binary-content-types 设计

## Context

`dinghai-file-storage/spring-boot-starter` 模块中，`TomcatSendfileTransfer.register(HttpServletRequest, Path, long, long)` 负责把符合条件的本地文件注册给 Tomcat 内核 sendfile（零拷贝）。当前实现只要请求携带非空的 `Accept-Encoding`（除 `identity` 外）就回退为 JVM 有界流式拷贝——因为 sendfile 直出绕过了容器层的 gzip 压缩过滤器，若压缩中间件仍然处理该响应，会出现声明压缩却未压缩的冲突。

问题在于浏览器无条件携带 `Accept-Encoding: gzip`，前端 JS 无法移除该头，导致 JPEG/MP4/zip 这类任何压缩中间件都不会处理的二进制资源也被迫回退。本机实测（500MB 文件）：零拷贝单流约 9.2 GB/s vs 流式回退约 2.1 GB/s，每 GB 传输的 JVM CPU 消耗降低约 13.6 倍。

`FileStorageWebController.stream(...)`（`FileStorageWebController.java:585`）在调用 `register(...)` 时，上游 `rangedResponse(...)` 持有 provider 确认的 `ObjectMetadata`（含 `contentType`），响应头中的 Content-Type 也来自该字段（`responseHeaders(...)` → `mediaType(...)`）。

约束：无配置项变更、无 API 变更；其余 sendfile 前置条件（仅本地 provider、非安全连接、范围合法、普通文件）保持不变。

## Goals / Non-Goals

**Goals:**

- 响应 Content-Type 属于不可压缩二进制类型时，即使请求携带 `Accept-Encoding: gzip` 也照常注册 sendfile。
- 可压缩文本类型（`text/*`、`application/json`、`application/javascript`、`application/xml`、`application/xhtml+xml`、`image/svg+xml` 等）维持现有回退行为。
- 未声明或无法识别的 Content-Type 维持保守回退。
- `Accept-Encoding: identity` 或缺失时的行为完全不变。

**Non-Goals:**

- 不新增配置项或公开 API。
- 不改动 sendfile 的其他前置条件与安全校验。
- 不处理宿主自定义压缩中间件与 sendfile 的交互（本组件自身不注册压缩过滤器）。

## Decisions

### 1. 分类方式：内置精确集合 + 少量通配规则，大小写不敏感、忽略参数

新增包私有辅助类 `SendfileContentTypes`（与 `TomcatSendfileTransfer` 同包），暴露静态方法 `isIncompressible(String contentType)`：

- 解析：取 `;` 之前的 type/subtype 部分，`trim` 后转小写。无法解析出合法 `type/subtype` 结构（空串、无 `/`、解析异常）一律返回 `false`（保守）。
- 精确匹配集合（不可压缩）：`application/octet-stream`、`application/pdf`、`application/zip`、`application/gzip`、`application/x-tar`、`application/x-7z-compressed`、`application/x-rar-compressed`、`application/x-bzip2`、`application/x-xz`、`application/zstd`、`application/vnd.rar`、`application/epub+zip`、`font/woff`、`font/woff2`、`font/ttf`、`font/otf`、`application/font-woff`、`application/x-font-ttf`、`application/x-font-opentype` 等常见字体与压缩包类型。
- 通配规则：`image/*`、`video/*`、`audio/*` 整体视为不可压缩，但显式排除 `image/svg+xml`（SVG 是 XML 文本，压缩收益高）。字体相关 `font/*` 亦整体不可压缩。
- 可压缩清单不单独维护——默认判定即"不在不可压缩集合/规则内则视为可能压缩"，`text/*`、`application/json`、`application/javascript`、`application/xml`、`application/xhtml+xml` 等自然落入回退路径。

备选方案：维护"可压缩清单"并默认放行——被否决。新出现的未知类型会被默认放行走 sendfile，违背保守默认原则，也与提案"不可识别类型维持回退"冲突。

备选方案：使用 Spring `MediaType` 解析——被否决。`metadata.contentType()` 可能是非法字符串，控制器现有 `mediaType(...)` 兜底会把它映射成 `application/octet-stream`，若基于该兜底结果分类会把"未知类型"误判为"不可压缩二进制"，违背保守回退要求；分类必须基于原始字符串自行做宽松解析。

### 2. 分类代码位置：新增包私有 `SendfileContentTypes` 类

放在 `TomcatSendfileTransfer` 同包，独立成类而非内联进 `TomcatSendfileTransfer`：

- 清单 + 通配 + 排除规则的判定逻辑有一定体量，独立类便于单测和后续扩充清单；
- `TomcatSendfileTransfer` 保持单一职责（前置条件校验 + 属性注册）。

### 3. 内容类型传递：扩展 `register` 签名，传入原始 `metadata.contentType()`

- `TomcatSendfileTransfer.register(HttpServletRequest request, Path file, long start, long length, String contentType)`：回退判断由 `acceptsCompression(request)` 改为 `acceptsCompression(request) && !SendfileContentTypes.isIncompressible(contentType)`。
- `FileStorageWebController.stream(...)` 增加 `String contentType` 参数，由 `rangedResponse(...)` 传入 `metadata.contentType()`。
- 数据源必须是 provider 确认的 `ObjectMetadata.contentType()`，而非请求头：请求方无法伪造（上传时由宿主/签名约束），且响应头 Content-Type 本就来自该字段，二者天然一致；若用请求头（如 `Accept`）则客户端可以自选类型绕过回退，语义错误。
- 不用 `headers.getContentType()`（`mediaType(...)` 的解析结果）：非法 contentType 会被兜底成 `application/octet-stream` 而误判为不可压缩（见决策 1）。

### 4. 回退语义不变的部分

- `Accept-Encoding` 缺失、空白、或 `identity`：`acceptsCompression` 为 `false`，无论类型如何均走 sendfile——行为不变。
- 非本地 provider（无本地缓存文件）、安全连接、非法范围、非常规文件：维持回退——行为不变。

## Risks / Trade-offs

- [宿主应用自定义了会压缩二进制响应的中间件，sendfile 直出可能与压缩头冲突] → 内置清单只收录通用"永不压缩"类型（图片/视频/音频/压缩包/PDF/字体/octet-stream），且本组件自身不注册压缩过滤器；提案中已声明该风险可控。文档中补充零拷贝生效条件以便宿主自查。
- [不可压缩清单遗漏某些二进制类型（如 `application/wasm`、新型字体）] → 遗漏的后果仅是保守回退为流式传输，不产生正确性问题；清单可按需扩充。可将 `application/wasm` 等常见项一并收录以降低遗漏概率。
- [通配 `image/*` 覆盖个别可压缩 image 子类型] → 目前唯一主流例外是 `image/svg+xml`，已显式排除；其余 image 子类型（png/jpeg/webp/avif/heic…）均为压缩编码的二进制格式。

## Migration Plan

纯行为收窄，无部署/迁移步骤。发布后经浏览器下载大二进制文件即可观察到吞吐提升；回滚方式为还原本变更的代码（无数据、配置或状态迁移）。
