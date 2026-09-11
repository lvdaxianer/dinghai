## Context

仓库当前只包含 `dinghai-shared-state`，没有文件存储抽象。业务若直接使用本地 `Files` API、MinIO SDK 或后续厂商 SDK，会把后端模型、异常和 URL 规则扩散到业务层，也难以在部署环境间替换后端。

该变更是 Java 21 / Spring Boot 3 的跨模块基础能力。它承载用户可控的对象 key、文件流、对象存储凭据和对外 URL，因此路径安全、大小限制、鉴权边界、缓存语义、超时和密钥泄漏防护是核心设计约束。

## Goals / Non-Goals

**Goals:**

- 建立不暴露供应商 SDK 类型的 `FileStorage` 公共契约，并为本地磁盘与 MinIO 提供等价实现。
- 以四个 Maven 模块隔离核心 API、后端适配器与 Spring Boot HTTP 集成，避免本地使用者传递引入 MinIO SDK。
- 支持 UTF-8 中文、多级相对路径对象 key，普通上传/下载/元数据/删除、预签名上传/下载、浏览器 inline 预览和 HTTP/CDN 缓存验证。
- 使用最小权限安全默认值：直接 Web 访问关闭，预签名 URL 限时且绑定对象和操作，敏感配置 fail-fast 且不进入日志或仓库。
- 支持通过配置在本地磁盘和固定 MinIO bucket 之间选择一个后端；后续适配器可以独立加入。

**Non-Goals:**

- 不支持阿里云 OSS、华为云 OBS、多 bucket / 多租户动态路由、自动后端故障切换或跨后端复制。
- 不支持分片上传、文件列表、批量复制、Office/PDF 转换、缩略图、视频转码和应用内文件内容缓存。
- 不保证预签名 URL 单次使用；在有效期内它是可重放的 bearer capability。
- 不提供用户、租户或文件归属模型；这些由宿主业务通过授权扩展点定义。

## Decisions

### D1: 使用 core + adapter + starter 四模块结构

新增 `dinghai-file-storage-core`、`dinghai-file-storage-local`、`dinghai-file-storage-minio` 与 `dinghai-file-storage-spring-boot-starter`。core 仅依赖 JDK，定义 `FileStorage`、请求/响应模型、缓存策略、稳定异常和对象 key 校验；local 仅依赖 core；minio 仅在自己的模块引入 MinIO SDK；starter 负责属性绑定、后端选择、Spring MVC 端点和授权扩展点。

备选的单 JAR 方案会把 MinIO 与 Spring 依赖带给只用本地磁盘的应用；纯 SPI 方案虽灵活，但不能提供已要求的开箱即用预签名和 HTTP 行为，因此均拒绝。

### D2: 公共 API 以流和后端中立模型表达

`FileStorage` 提供 `put`、`get`、`stat`、`exists`、`delete`、`presignUpload`、`presignDownload`。`UploadRequest` 包含 `ObjectKey`、调用方拥有的输入流、已声明的长度、内容类型和缓存策略；`DownloadObject` 实现 `AutoCloseable`，由读取者关闭响应流；`ObjectMetadata` 统一返回 key、大小、内容类型、ETag、最后修改时间和缓存策略。

预签名上传返回 `PresignedUpload`（URI、HTTP 方法、必须请求头和可选表单字段），以兼容本地的受签名 PUT 与 MinIO 的带大小/类型约束 POST policy；预签名下载返回 `PresignedDownload`（URI、过期时间、`inline` 或 `attachment` 响应意图）。业务代码不接触 `MinioClient` 或本地 `Path`。

`put` 覆盖同 key 对象，`get`/`stat` 对不存在对象返回 `Optional.empty()`，`delete` 对不存在对象返回 `false`。后端错误转换为带错误码、可安全展示消息和原因的 `FileStorageException`，不泄漏 endpoint、凭据或文件系统路径。

### D3: 在 core 统一限制对象 key 与上传输入

`ObjectKey` 将 Unicode NFC 规范化后校验：key 非空；以 `/` 分隔的每段都非空且不为 `.` / `..`；不得含反斜杠、NUL 或其他控制字符；不得为绝对路径；UTF-8 全 key 不超过 1024 bytes，任一段不超过 255 bytes。有效 key 可包含中文、空格、字母、数字、`-`、`_`、`.` 与其他非控制 Unicode 字符。URL 组件按 RFC 3986 逐段 UTF-8 编码，再以已解码 key 重新校验，避免双编码绕过。

上传默认最大 100 MB，`dinghai.file-storage.upload.max-size` 可调整；可选内容类型白名单为空时不限制类型，非空时普通上传和预签名上传均必须匹配。内容类型缺失统一为 `application/octet-stream`。声明长度不可信：本地 Web 写入、普通流式上传和 MinIO policy 都在适用位置实行上限。

### D4: 本地磁盘以根目录封闭和原子写入保证安全

本地对象路径由 `root.resolve(key).normalize()` 得到，并再次要求其以标准化 root 开头。写入使用同一文件系统内的临时文件、流式计数与 SHA-256 ETag 计算；完成校验后使用 `ATOMIC_MOVE` 替换目标，不支持原子移动的文件系统使用受控替代并明确失败。父目录按需创建。

内容类型和缓存策略使用 root 下不可直接访问的元数据目录按 key 摘要保存，元数据文件同样临时写入并原子移动。发生内容/元数据双文件部分失败时，操作报告失败并保留可恢复状态；`stat` 对可读内容缺少元数据时回退到安全默认内容类型和 `private, max-age=0`，不把它当作公开缓存。

### D5: MinIO 使用固定 bucket 与原生对象语义

`dinghai.file-storage.minio.bucket` 是单应用固定 bucket。启动时验证 endpoint、bucket、access key、secret key、超时和 provider 配置完整性；不自动创建 bucket，避免库在错误环境写入意外资源。适配器通过配置的 OkHttp 客户端设置连接、读取和写入超时，并把 SDK 服务端失败、认证失败、超时和不存在对象映射为统一异常/空结果。

上传用对象元数据保存内容类型、缓存控制和 ETag 相关信息；下载预签名使用 MinIO GET URL 并在响应查询参数中固定 `inline`/`attachment` 和允许的缓存响应头。上传预签名使用 POST policy，将 key、过期时间、长度范围与内容类型约束交给 MinIO。

### D6: 本地预签名依赖 Starter 受保护端点，安全默认关闭直接访问

Starter 默认 `web.enabled=true`、`web.path-prefix=/files`、`web.direct-access=false`。它注册本地预签名所需的 HTTP 端点：`PUT /files/upload/{*key}`、`GET /files/download/{*key}` 与 `GET /files/preview/{*key}`。带有效签名参数的请求可访问；直接访问只有在 `direct-access=true` 且应用提供 `FileStorageAccessPolicy` 时才允许，否则拒绝。`FileStorageAccessPolicy` 由宿主应用以认证主体、对象 key 和操作类型判断权限，组件不臆测用户或租户模型。

本地 HMAC-SHA-256 签名载荷包含协议版本、HTTP 方法、规范化 key、到期时间、响应模式、内容类型/大小限制与缓存策略摘要。验签使用常量时间比较，拒绝过期、篡改、错误方法和错误 key。签名密钥必须从部署注入，生产模式缺失即启动失败；URL 不写入日志。签名 URL 在到期前可重放，业务若要求撤销或单次消费应使用自定义授权端点或后续带状态的能力。

### D7: Web 层统一 HTTP 缓存与预览响应

本地下载/预览端点根据 `ObjectMetadata` 设置 ETag、Last-Modified、Cache-Control、Expires、Content-Type 和 Content-Disposition；下载强制 `attachment`，预览强制 `inline`。默认缓存策略为 `private, max-age=0`；`public` 和 `immutable` 必须由上传/预签名请求显式声明。端点正确处理 `If-None-Match` 和 `If-Modified-Since` 并返回 `304`，实现单范围 `Range` 请求以支持本地音视频和浏览器预览。MinIO 预签名 URL 由对象服务处理等价的 HTTP 范围与缓存响应。

不在进程内缓存文件字节。HTTP/CDN 缓存一致性由 ETag 和写入后更新元数据控制；写入覆盖同 key 后获得新的 ETag，旧 URL 在未到期前仍可能读取新内容，这是对象 key 覆盖语义的自然结果。

### D8: 配置、可观测性和文档遵守最小暴露原则

所有属性以 `dinghai.file-storage.*` 绑定。`local.root`、`minio.endpoint/bucket/access-key/secret-key`、`security.signing-key` 使用 provider 分支验证；禁止无配置静默回退到开发目录或公共 bucket。`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`DINGHAI_FILE_STORAGE_SIGNING_KEY` 仅以占位符出现在 `.env.example`。

HTTP Controller 不打业务日志；宿主应用的入口层负责 access log 和 trace ID。适配器不记录完整对象 URL、签名、请求体、文件名之外的敏感元数据或密钥。后续若增加指标，以计数/时延指标而不是每次文件操作 INFO 日志表达高频数据。

## Risks / Trade-offs

- [本地元数据与内容不是跨文件事务] → 内容与元数据均原子替换，失败向调用方报告；读取缺失元数据时使用私有、不可缓存的安全默认值，提供清理/修复工具作为后续能力。
- [预签名 URL 泄露后在到期前可使用] → 短有效期、HTTPS、最小权限、URL 不入日志；一次性与撤销设计留给独立变更。
- [本地预签名需要 Starter Web 端点] → 没有激活 Web 端点时，local `presign*` 以明确配置异常失败，业务可使用 SPI 自建端点。
- [对象覆盖使旧 URL 读取新内容] → 文档明确 key 覆盖语义；不可变文件由业务使用内容寻址或版本化 key。
- [MinIO 集成测试依赖外部服务] → 纯单元测试覆盖参数与异常映射，真实 MinIO 契约测试通过显式环境配置或 Testcontainers 运行，默认构建不接触生产 endpoint。
- [文件类型伪造] → 首版校验声明类型与大小，不承担病毒扫描或内容嗅探；高风险业务在上传前增加扫描流水线。

## Migration Plan

1. 发布四个新模块，不修改既有 shared-state API。
2. 应用引入 `dinghai-file-storage-spring-boot-starter`，选择 `local` 或 `minio` 并提供对应部署配置与签名密钥。
3. 预发布环境先以独立测试 bucket 或本地根目录执行上传、预签名和缓存验证，确认 endpoint 不指向生产资源。
4. 生产环境以 `direct-access=false` 启动；业务仅通过预签名 URL 或实现 `FileStorageAccessPolicy` 后再开启直接访问。
5. 回滚时移除 starter 或切回原有业务存储实现；该组件不迁移或删除已有对象，需保留对象数据与环境变量密钥。

## Open Questions

（无）
