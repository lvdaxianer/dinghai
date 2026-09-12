# dinghai-file-storage

[English](README_EN.md) | 中文

统一文件存储适配层，业务代码只依赖 `FileStorage` 契约即可切换本地磁盘与 MinIO 后端。Local 支持 `1/1/1/1.png`、中文目录等对象 key，并通过临时文件和流式读写避免把文件整体载入内存。

要求：Java 21+，Spring Boot 3.x。

## 模块构成

| 模块 | 说明 |
| --- | --- |
| `dinghai-file-storage-core` | 后端无关的 `FileStorage` 契约、对象模型、预签名模型与稳定错误码 |
| `dinghai-file-storage-local` | 本地磁盘适配器，支持中文及多级对象 key、HMAC 预签名和流式读写 |
| `dinghai-file-storage-minio` | 固定 bucket 的 MinIO 适配器与原生预签名能力 |
| `dinghai-file-storage-spring-boot-starter` | 按 provider 自动装配文件存储，并提供本地 Web 预签名端点 |

`benchmark` 模块仅用于开发验证，不发布（见文末「基准测试」）。

## 快速开始

引入 Starter：

```xml
<dependency>
    <groupId>io.github.lvdaxianer</groupId>
    <artifactId>dinghai-file-storage-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

最小配置（Local 后端，敏感值用环境变量注入）：

```yaml
dinghai:
  file-storage:
    provider: local
    bucket: documents
    storage-alias: documents
    local:
      root: /var/lib/app/files
    security:
      signing-key: ${DINGHAI_FILE_STORAGE_SIGNING_KEY}
      stable-id-key: ${DINGHAI_FILE_STORAGE_STABLE_ID_KEY}
```

引入后自动装配完成即可注入 `FileStorage` 使用；`web.enabled` 默认开启，自动注册 `/files/**` 系列端点。所有配置项都在启动时校验，非法值直接以 `INVALID_CONFIGURATION` 启动失败，不存在静默兜底。

## 配置项

配置前缀为 `dinghai.file-storage`。所有校验都是代码逻辑（启动期执行），不依赖 Bean Validation 注解。

### 顶层参数

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `provider` | `local` / `minio` | 无（必填） | 存储后端，二选一。缺失或非法直接启动失败，**不做任何自动探测或回退** |
| `bucket` | `String` | 无（必填） | 所有后端共用的逻辑桶名。不允许 `.`、`..`、`/`、`\` 和控制字符；Local 下映射为 `root/<bucket>/` 单层目录 |
| `storage-alias` | `String` | 无（必填） | 不随物理后端变动的逻辑存储别名，参与稳定文件 ID 计算和缓存文件名摘要。从 Local 迁移到 MinIO 时保持别名不变，已签发的稳定 ID 才能继续有效 |

### local（Local 后端）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `local.root` | `Path` | 无（provider=local 时必填） | 本地对象根目录，缺失时启动失败 |

### minio（MinIO 后端）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `minio.endpoint` | `String` | 无（provider=minio 时必填） | MinIO 服务端地址 |
| `minio.access-key` | `String` | 无（必填） | 访问密钥 |
| `minio.secret-key` | `String` | 无（必填） | 私密密钥 |
| `minio.connect-timeout` | `Duration` | `10s` | 连接超时（OkHttp 客户端），必须为正 |
| `minio.read-timeout` | `Duration` | `10s` | 读取超时，必须为正 |
| `minio.write-timeout` | `Duration` | `10s` | 写入超时，必须为正 |

MinIO 适配器**不会自动创建或修改 bucket**，需事先自行建好。

### security（安全）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `security.signing-key` | `String` | 无（仅 provider=local 时必填） | 本地预签名 URL 的 HMAC-SHA-256 密钥，按 UTF-8 字节直接使用，缺失时启动失败。MinIO 后端不使用（走原生预签名） |
| `security.stable-id-key` | `String` | 无（**所有后端必填**） | 稳定文件 ID 的密钥材料，UTF-8 编码后至少 32 字节；经 SHA-512 派生为 64 字节 AES-SIV 密钥。缺失或过短时启动失败。轮换该密钥会使全部旧稳定 ID 失效 |
| `security.maximum-presign-expiry` | `Duration` | `1h` | 本地预签名 URL 的最大有效期，签发与校验都强制执行，必须为正 |

### web（Web 端点）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `web.enabled` | `boolean` | `true` | 是否注册文件 Web 端点、异步执行器和 MVC 配置 |
| `web.path-prefix` | `String` | `/files` | 文件端点路径前缀，同时作为本地预签名 URL 的前缀。必须非空、以 `/` 开头、不含 `?`、`#`、`\r`、`\n`。注意：迁移管理端点固定为 `/files/migration/*`，**不随该前缀变化** |
| `web.direct-access` | `boolean` | `false` | 是否允许无预签名凭据的 Web 上传。即使开启，仍需宿主提供 `FileStorageAccessPolicy` Bean 且其返回允许；Web 读取不要求组件凭证，但受读取并发上限保护 |
| `web.read-buffer-size` | `DataSize` | `128KB` | 单个响应流的复制缓冲区大小，是单次复制缓冲而非整文件内存；必须为正且不超过 `Integer.MAX_VALUE` |
| `web.max-concurrent-reads` | `int` | `128` | 同时执行的 Web 响应读取上限；耗尽时返回 HTTP 429，不排队等待。覆盖流式、缓存文件和零拷贝传输 |
| `web.max-concurrent-uploads` | `int` | `16` | 同时执行的 Web 上传上限；耗尽时返回 HTTP 429，不读取被拒绝请求体。SDK 上传不受此限制 |

### cache（二级磁盘读取缓存）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `cache.enabled` | `boolean` | 见说明 | MinIO 后端缺省开启，显式 `false` 关闭；Local 后端不使用二级缓存，显式 `true` 会在启动时失败（请删除旧配置） |
| `cache.root` | `Path` | `${java.io.tmpdir}/dinghai-file-storage-cache` | 缓存根目录，为 null 或创建失败时启动失败。**生产环境应显式配置持久目录**，默认位置在系统临时目录下，可能被操作系统清理 |
| `cache.max-object-size` | `long`（字节） | `50MB` | 超过该大小的对象不做异步落盘，读请求直接直通远端 |
| `cache.idle-ttl` | `Duration` | `7d` | 空闲副本保留时长，按每个副本 sidecar 文件记录的最近访问时间判定 |
| `cache.buffer-size` | `DataSize` | `128KB` | 缓存落盘的单次复制缓冲区，必须为正且不超过 `Integer.MAX_VALUE` |
| `cache.max-concurrent-fills` | `int` | `4` | 异步落盘固定线程池大小，必须为正 |
| `cache.queue-capacity` | `int` | `32` | 填充任务等待队列容量；队列满时放弃本次填充（不影响当前读请求），不阻塞业务 |

### upload（上传）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `upload.max-size` | `long`（字节） | `100MB` | 普通上传与预签名上传共用的体积上限，超限抛 `OBJECT_TOO_LARGE` |
| `upload.content-type-whitelist` | `Set<String>` | 空集（不限制） | 内容类型白名单；非空时不在名单内的上传抛 `CONTENT_TYPE_NOT_ALLOWED` |

### migration（离线迁移）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `migration.enabled` | `boolean` | `false` | 是否启用迁移服务与迁移管理端点。端点注册还要求宿主提供 `FileStorageMigrationAccessPolicy` Bean，缺策略时端点不注册 |
| `migration.export-root` | `Path` | 无（enabled=true 时必填） | 导出包写入根目录 |
| `migration.import-root` | `Path` | 无（必填） | 待导入包的扫描根目录 |
| `migration.completed-root` | `Path` | 无（必填） | 导入成功后包的归档目录 |
| `migration.failed-root` | `Path` | 无（必填） | 导入失败包及 `errors.ndjson` 的归档目录 |

完整配置示例：

```yaml
dinghai:
  file-storage:
    provider: local
    bucket: documents
    storage-alias: documents
    local:
      root: /var/lib/app/files
    security:
      signing-key: ${DINGHAI_FILE_STORAGE_SIGNING_KEY}
      stable-id-key: ${DINGHAI_FILE_STORAGE_STABLE_ID_KEY}
    web:
      enabled: true
      path-prefix: /files
      direct-access: false
      read-buffer-size: 128KB
      max-concurrent-reads: 128
    cache:
      max-object-size: 50MB
      buffer-size: 128KB
      max-concurrent-fills: 4
      queue-capacity: 32
    upload:
      max-size: 100MB
```

## Web 端点

`web.enabled=true` 时注册以下端点（前缀默认为 `/files`，随 `web.path-prefix` 变化）：

| 端点 | 说明 |
| --- | --- |
| `PUT /upload/{*key}` | 本地预签名上传（HMAC）；无签名时走直接访问授权 |
| `GET /download/{*key}` | 预签名下载，强制 `attachment` 响应模式 |
| `GET /preview/{*key}` | 预签名预览，强制 `inline` 响应模式 |
| `GET /read/key/{*key}` 及 `/read/key/preview/{*key}`、`/read/key/download/{*key}` | 按真实 object key 匿名直读，不要求组件读取凭证；受 `web.max-concurrent-reads` 限制 |
| `GET /read/v1/{id}` 及 `/read/v1/preview/{id}`、`/read/v1/download/{id}` | 按稳定文件 ID 匿名读取，不做用户鉴权也不需要签名（见下节） |

迁移管理端点固定为 `POST /files/migration/export`、`POST /files/migration/import`（参数 `packageName`），不随 `web.path-prefix` 变化。

预览/下载支持 `Content-Type`、ETag、Last-Modified、Cache-Control、Expires、304（If-None-Match / If-Modified-Since）和单区间 Range 206/416。Local 主文件和 MinIO 已校验的热缓存文件在明文、未压缩的嵌入 Tomcat 中优先注册原生 `sendfile`；TLS、压缩、其他容器或容器不支持时自动回退到有界流式复制，调用方无需切换接口。

## 预签名与访问授权

- **Local 后端**：组件自实现的 HMAC-SHA-256 预签名。签名 URL 携带 `expires`（Unix 秒）、`signature`（Base64URL）等查询参数，上传签名还可绑定 `contentType`、`maxSize`、`cacheControl`；下载签名绑定 `inline` 或 `attachment` 响应模式。有效期受 `security.maximum-presign-expiry` 限制。
- **MinIO 后端**：使用 MinIO 原生预签名（PostPolicy 上传、`getPresignedObjectUrl` 下载）。本地签名校验仅 Local 适配器支持，对非 Local 后端走本地签名 URL 会抛 `INVALID_PRESIGN`。
- **直接上传**：无签名上传默认拒绝；必须同时满足 `web.direct-access=true` 且宿主提供 `FileStorageAccessPolicy` Bean 并对 UPLOAD 返回允许。Web 下载、预览和读取不要求组件凭证，但受 `web.max-concurrent-reads` 限制；宿主应在网关/WAF层负责身份和速率策略。
- 所有 Bean 均带 `@ConditionalOnMissingBean`，宿主可替换默认实现（包括 `FileStorage` 本身）。

## 稳定文件 ID

稳定 ID 是私有对象的持久读取地址（`/files/read/v1/{id}` 系列）。ID 由 AES-SIV（Tink）对长度前缀的 `storage-alias + object key` 做确定性认证加密得到，输出 `v1.` 前缀的 Base64URL 字符串。因此：

- 同一对象跨 Local / MinIO 迁移后 ID 不变（前提：`storage-alias` 不变）；
- 轮换 `security.stable-id-key` 会使全部旧 ID 失效；
- 该路由**不执行组件内用户鉴权**，嵌入方负责浏览器前的权限控制。

## 读取缓存行为

非 Local 后端默认启用经远端 `stat` 校验的二级磁盘读取缓存；Local 不创建二级缓存或清理器：

1. 每次读先 `stat` 远端元数据；本地副本的 ETag / 长度 / 修改时间与远端一致才命中，命中后刷新最近访问时间；
2. 未命中且对象不超过 `cache.max-object-size` 时，当前请求直接流式读取远端，另开第二条流异步落盘（临时文件 + 原子移动发布）；
3. `put` / `delete` 立即失效对应副本；
4. 每天 00:00（服务器本地时区）清理超过 `cache.idle-ttl` 未访问的副本，清理失败静默留待下轮；
5. 缓存文件名是 `SHA-256(alias + key)` 的十六进制摘要，不泄露原始对象 key。

## 零拷贝与并发

- Local 主文件和远程热缓存命中时，Tomcat 明文且未压缩请求优先使用原生 `sendfile`，不按文件大小分配 JVM 堆内存。
- TLS、响应压缩、非 Tomcat 容器、文件暂不可用或容器拒绝注册时自动降级为有界流式复制，响应内容、条件请求和 Range 语义保持不变。
- `web.max-concurrent-reads` 只限制流式回退，并覆盖异步复制任务的完整生命周期；Tomcat `sendfile` 不获取该 permit，由容器连接和执行器配置负责并发边界。
- Local 升级时必须删除 `dinghai.file-storage.cache.enabled=true`；该配置已移除，继续保留会使应用启动失败。

## 离线迁移

离线迁移只处理服务器目录包，不通过 HTTP 上传大文件。导出产生 `manifest.ndjson`（含每个对象的 SHA-256 与长度）和 `objects/` 目录；导入前对清单做全量校验，避免部分导入。导入成功后包移动到 completed，失败包及 `errors.ndjson` 移动到 failed。管理端点仅在 `migration.enabled=true` 且宿主提供 `FileStorageMigrationAccessPolicy` 时注册，策略必须分别授权 `EXPORT` 与 `IMPORT` 操作。

## 错误码

统一错误码定义在 `FileStorageErrorCode`：`INVALID_REQUEST`、`OBJECT_NOT_FOUND`、`INVALID_OBJECT_KEY`、`OBJECT_TOO_LARGE`、`CONTENT_TYPE_NOT_ALLOWED`、`AUTHENTICATION_FAILED`、`STORAGE_UNAVAILABLE`、`INVALID_PRESIGN`、`INVALID_CONFIGURATION`、`INTERNAL_ERROR`。

Web 层 HTTP 映射：`OBJECT_NOT_FOUND` → 404，`OBJECT_TOO_LARGE` → 413，`INVALID_PRESIGN` / `AUTHENTICATION_FAILED` → 403，`STORAGE_UNAVAILABLE` → 503，其余 → 400；响应体为 `{code, message}`。

## 设计要点

- **无日志脱敏问题**：组件代码不输出任何日志，异常消息全部是固定安全文案，不回显密钥、签名 URL 或配置值。
- **流式读写**：Local 通过临时文件 + 原子移动写入；可用时 Web 读取优先交给 Tomcat `sendfile`，其他场景使用有界流式复制，不把文件整体载入内存；`web.read-buffer-size` 与 `cache.buffer-size` 只是单次复制缓冲区。
- **专用异步执行器**：流式响应使用文件存储专用的有界执行器（core=4 / max=16 / queue=64，线程前缀 `file-storage-web-`），避免依赖无界默认执行器。注意它注册为 Spring MVC 的全局异步执行器，会影响宿主其他 `StreamingResponseBody`，宿主可按需覆盖。
- **显式优于隐式**：不自动创建 bucket、不在后端之间自动回退、无签名访问默认拒绝、配置非法一律启动失败。

## 基准测试

`benchmark` 模块仅用于开发验证，可对真实文件执行并发上传、冷读、MinIO 缓存填充/热读和 SHA-256 校验：

```bash
# Local：并发上传 4 份、每份读取 2 轮
mvn -pl dinghai-file-storage/benchmark -am -DskipTests install
mvn -f dinghai-file-storage/benchmark/pom.xml exec:java \
  -Dexec.mainClass=io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageBenchmark \
  -Dexec.args="--provider=local --file=$HOME/Downloads/982-合0820.FBX --concurrency=4 --read-rounds=2"

# MinIO：运行时从环境变量读取 endpoint、access key、secret key 和 bucket
export DINGHAI_FILE_STORAGE_MINIO_ENDPOINT=http://localhost:9000
export DINGHAI_FILE_STORAGE_MINIO_ACCESS_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_MINIO_SECRET_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_MINIO_BUCKET=dinghai-benchmark
mvn -f dinghai-file-storage/benchmark/pom.xml exec:java \
  -Dexec.mainClass=io.github.lvdaxianer.dinghai.filestorage.spring.FileStorageBenchmark \
  -Dexec.args="--provider=minio --file=$HOME/Downloads/doc.zip --concurrency=4 --read-rounds=2"
```

压测使用独立随机对象 key，完成后尽力删除对象；输出包含阶段耗时、吞吐量、平均/P95/P99 延迟和错误数，不输出密钥。Local 已用 `982-合0820.FBX`（412,202,320 bytes）完成上述参数的实测：4 并发上传共 1.53 GiB，平均 1298 ms、P95 1406 ms、吞吐 1117 MiB/s；随后 8 次并发冷读共 3.07 GiB，平均 200 ms、P95 230 ms、吞吐 7765 MiB/s。Docker MinIO（映射端口 9100）已用 `doc.zip`（约 61 MiB）完成 4 并发实测：上传 48 MiB/s、冷读 371 MiB/s、缓存填充 180 MiB/s、热读 6079 MiB/s，所有阶段错误数均为 0。所有 SHA-256 校验通过。该读数会受操作系统和 MinIO 缓存影响，不能视为纯物理磁盘吞吐。不要将凭据或完整预签名 URL 提交到仓库。

## 测试

```bash
mvn test
```
