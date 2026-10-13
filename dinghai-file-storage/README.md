# dinghai-file-storage

[English](README_EN.md) | 中文

统一文件存储适配层，业务代码只依赖 `FileStorage` 契约即可切换本地磁盘、MinIO、RustFS、阿里云 OSS 与华为云 OBS 后端。Local 支持 `1/1/1/1.png`、中文目录等对象 key，并通过临时文件和流式读写避免把文件整体载入内存。

要求：Java 21+，Spring Boot 3.x。

## 模块构成

| 模块 | 说明 |
| --- | --- |
| `dinghai-file-storage-core` | 后端无关的 `FileStorage` 契约、对象模型、预签名模型与稳定错误码 |
| `dinghai-file-storage-local` | 本地磁盘适配器，支持中文及多级对象 key、HMAC 预签名和流式读写 |
| `dinghai-file-storage-minio` | 固定 bucket 的 MinIO 适配器与原生预签名能力 |
| `dinghai-file-storage-rustfs` | 固定 bucket 的 RustFS S3 兼容适配器与原生预签名能力 |
| `dinghai-file-storage-aliyun-oss` | 固定 bucket 的阿里云 OSS V2 SDK 适配器与原生预签名能力 |
| `dinghai-file-storage-huaweicloud-obs` | 固定 bucket 的华为云 OBS 专用 SDK 适配器、原生预签名与 multipart 能力 |
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
| `provider` | `local` / `minio` / `rustfs` / `aliyun-oss` / `huaweicloud-obs` | 无（必填） | 存储后端，五选一。缺失、非法或旧值 `oss` 直接启动失败，**不做任何自动探测、回退或兼容别名** |
| `bucket` | `String` | 无（必填） | 所有后端共用的逻辑桶名。不允许 `.`、`..`、`/`、`\` 和控制字符；Local 下映射为 `root/<bucket>/` 单层目录 |
| `storage-alias` | `String` | 无（必填） | 不随物理后端变动的逻辑存储别名，参与稳定文件 ID 计算和缓存文件名摘要。从 Local 迁移到 MinIO、RustFS 或阿里云 OSS 时保持别名不变，已签发的稳定 ID 才能继续有效 |

### local（Local 后端）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `local.root` | `Path` | 无（provider=local 时必填） | 本地对象根目录，缺失时启动失败 |
| `local.cleanup-empty-directories` | `boolean` | `true` | 删除本地对象后是否回收 root 内的空父目录；仅影响 Local，不改变远程对象存储行为 |

Local 适配器额外提供 `deletePrefix(String prefix)`，用于一次删除某个对象前缀下的全部本地对象，
例如删除 `model-version/123/V0` 下的模型文件和元数据。它不是 `FileStorage` 通用契约的一部分，
不会改变 MinIO、RustFS、OSS 或 OBS 的行为；删除完成后仅回收存储 root 内的空目录。

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

### rustfs（RustFS 后端）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `rustfs.endpoint` | `String` | 无（provider=rustfs 时必填） | RustFS S3 API 服务端地址，例如 `http://localhost:9100` |
| `rustfs.access-key` | `String` | 无（必填） | 访问密钥，应由部署环境注入 |
| `rustfs.secret-key` | `String` | 无（必填） | 私密访问密钥，应由部署环境注入 |
| `rustfs.connect-timeout` | `Duration` | `10s` | 连接超时（OkHttp 客户端），必须为正 |
| `rustfs.read-timeout` | `Duration` | `10s` | 读取超时，必须为正 |
| `rustfs.write-timeout` | `Duration` | `10s` | 写入超时，必须为正 |

RustFS 使用 S3 兼容 API；其 bucket 应保持私有并由运维预先创建。适配器**不会自动创建或修改 bucket 策略**，上传、下载预签名由 RustFS 原生签发。

### aliyun-oss（阿里云 OSS 后端）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `aliyun-oss.region` | `String` | 无（provider=aliyun-oss 时必填） | OSS 地域 ID，例如 `oss-cn-beijing` |
| `aliyun-oss.connect-timeout` | `Duration` | `10s` | SDK 连接超时，必须为正 |
| `aliyun-oss.read-timeout` | `Duration` | `10s` | SDK 读写超时，必须为正 |
| `aliyun-oss.write-timeout` | `Duration` | `10s` | SDK 写入超时，必须为正 |

阿里云 OSS 使用 `com.aliyun:alibabacloud-oss-v2:0.5.1`；bucket 应保持私有并由运维预先创建。适配器**不会创建 bucket，也不会修改 ACL、CORS 或 bucket policy**；上传使用原生 `PUT` 预签名，下载使用原生 `GET` 预签名。AccessKey 仅从 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET` 环境变量读取；本次**不支持 STS 临时凭证**。

### huaweicloud-obs（华为云 OBS 后端）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `huaweicloud-obs.endpoint` | `String` | 无（必填） | OBS 服务 endpoint，例如 `https://obs.cn-north-4.myhuaweicloud.com` |
| `huaweicloud-obs.region` | `String` | 无（必填） | OBS 地域，例如 `cn-north-4` |
| `huaweicloud-obs.credential-mode` | `static` / `sts` / `provider` | `static` | 凭证来源；`provider` 要求宿主提供 `IObsCredentialsProvider` Bean |
| `huaweicloud-obs.access-key` | `String` | 无（static/sts 必填） | AK，建议由部署环境注入 |
| `huaweicloud-obs.secret-key` | `String` | 无（static/sts 必填） | SK，建议由部署环境注入 |
| `huaweicloud-obs.security-token` | `String` | 无（sts 必填） | STS Security Token；仅 `credential-mode=sts` 使用 |
| `huaweicloud-obs.connect-timeout` | `Duration` | `10s` | SDK 连接超时，必须为正 |
| `huaweicloud-obs.read-timeout` | `Duration` | `10s` | SDK 读取超时，必须为正 |
| `huaweicloud-obs.write-timeout` | `Duration` | `10s` | SDK 写入超时，必须为正 |

OBS 使用 `com.huaweicloud:esdk-obs-java:3.26.6`，不使用 Huawei Cloud SDK Bundle。bucket 必须由运维预先创建；适配器不会创建或修改 bucket、ACL、CORS、生命周期和 policy。普通上传/下载使用原生 `PUT`/`GET` 预签名，multipart 使用 OBS 原生 initiate、分片上传、列举、complete、abort 生命周期，适合大文件直传和断点续传。

### security（安全）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `security.signing-key` | `String` | 无（仅 provider=local 时必填） | 本地预签名 URL 的 HMAC-SHA-256 密钥，按 UTF-8 字节直接使用，缺失时启动失败。远端 provider 不使用（走原生预签名） |
| `security.stable-id-key` | `String` | 无（**所有后端必填**） | 稳定文件 ID 的密钥材料，UTF-8 编码后至少 32 字节；经 SHA-512 派生为 64 字节 AES-SIV 密钥。缺失或过短时启动失败。轮换该密钥会使全部旧稳定 ID 失效 |
| `security.maximum-presign-expiry` | `Duration` | `1h` | 本地预签名 URL 的最大有效期，签发与校验都强制执行，必须为正 |

### web（Web 端点）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `web.enabled` | `boolean` | `true` | 是否注册文件 Web 端点、异步执行器和 MVC 配置 |
| `web.path-prefix` | `String` | `/files` | 文件端点路径前缀，同时作为本地预签名 URL 的前缀。允许省略前导 `/`、重复 `/`、尾随 `/` 或使用根路径；启动时统一规范化。不允许 `?`、`#`、`\\`、`\r`、`\n`。注意：迁移管理端点固定为 `/files/migration/*`，**不随该前缀变化** |
| `web.upload-path` | `String` | `/upload` | 上传端点相对于 `path-prefix` 的路径；本地预签名上传 URL 同步使用该路径 |
| `web.download-path` | `String` | `/download` | 下载端点相对于 `path-prefix` 的路径；本地预签名下载 URL 同步使用该路径 |
| `web.preview-path` | `String` | `/preview` | 预览端点相对于 `path-prefix` 的路径；本地预签名预览 URL 同步使用该路径 |
| `web.read-path` | `String` | `/read/key` | 真实 object key 读取、预览和下载端点相对于 `path-prefix` 的基础路径 |
| `web.stable-read-path` | `String` | `/read` | 稳定文件 ID 读取、预览和下载端点相对于 `path-prefix` 的基础路径 |
| `web.legacy-read-enabled` | `boolean` | `false` | 是否额外注册历史真实 object key 直读路由 |
| `web.legacy-read-path` | `String` | `/` | 历史直读路由相对于 `path-prefix` 的基础路径；仅在 `legacy-read-enabled=true` 时生效 |
| `web.direct-access` | `boolean` | `false` | 是否允许无预签名凭据的 Web 上传。即使开启，仍需宿主提供 `FileStorageAccessPolicy` Bean 且其返回允许；Web 读取不要求组件凭证，但受读取并发上限保护 |
| `web.sendfile-binary-content-types-enabled` | `boolean` | `true` | gzip 等压缩协商存在时，是否允许已确认的标准二进制 MIME 类型继续使用 Tomcat `sendfile`。文本、SVG、未知或非法 MIME 始终流式传输；宿主对二进制内容配置了自定义压缩时设为 `false` |
| `web.read-buffer-size` | `DataSize` | `128KB` | 单个响应流的复制缓冲区大小，是单次复制缓冲而非整文件内存；必须为正且不超过 `Integer.MAX_VALUE` |
| `web.max-concurrent-reads` | `int` | `128` | 同时执行的 Web 响应读取上限；耗尽时返回 HTTP 429，不排队等待。覆盖流式、缓存文件和零拷贝传输 |
| `web.max-concurrent-uploads` | `int` | `16` | 同时执行的 Web 上传上限；耗尽时返回 HTTP 429，不读取被拒绝请求体。SDK 上传不受此限制 |
| `web.max-concurrent-transfers-per-key` | `int` | `0`（关闭） | 宿主 resolver 返回非空 key 时该 key 的上传或读取并发上限；必须不小于 0。上传和读取分别计数，达到上限返回 HTTP 429；无活动传输的 key 状态立即回收 |

### cache（二级磁盘读取缓存）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `cache.enabled` | `boolean` | 见说明 | MinIO、RustFS 与阿里云 OSS 后端缺省开启；OBS 当前不启用该应用层二级缓存；显式 `false` 可关闭已有远端缓存；Local 后端不使用二级缓存，显式 `true` 会在启动时失败（请删除旧配置） |
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

### multipart（大文件分片上传）

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `multipart.part-size` | `DataSize` | `10MB` | 目标分片大小；非末分片必须等于该值，末片可更小。该值必须不小于 `5MB`，以满足 S3 和 OSS 的限制 |
| `multipart.max-parts` | `int` | `10000` | 单个对象最多分片数，取值范围为 `1` 到 `10000` |
| `multipart.session-ttl` | `Duration` | `24h` | 未完成会话的最长存活时间；客户端 `expiresIn` 不得超过该值 |
| `multipart.local-root` | `Path` | `${java.io.tmpdir}/dinghai-file-storage-multipart` | Local 未完成分片与会话元数据根目录；生产环境应显式配置持久目录 |
| `multipart.max-concurrent-uploads` | `int` | `8` | Local 分片经应用写入时的并发上限；耗尽立即返回 `429`，且不读取被拒绝请求体 |
| `multipart.cleanup-interval` | `Duration` | `PT5M` | 定时清理过期会话的 fixed-delay 间隔 |

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
      upload-path: /upload
      download-path: /download
      preview-path: /preview
      read-path: /read/key
      stable-read-path: /read
      legacy-read-enabled: false
      legacy-read-path: /
      direct-access: false
      sendfile-binary-content-types-enabled: true
      read-buffer-size: 128KB
      max-concurrent-reads: 128
      max-concurrent-uploads: 16
      max-concurrent-transfers-per-key: 4
    cache:
      max-object-size: 50MB
      buffer-size: 128KB
      max-concurrent-fills: 4
      queue-capacity: 32
    upload:
      max-size: 100MB
    multipart:
      part-size: 10MB
      max-parts: 10000
      session-ttl: 24h
      local-root: /var/lib/app/multipart
      max-concurrent-uploads: 8
      cleanup-interval: PT5M
```

## Web 端点

`web.enabled=true` 时注册以下端点。所有路径均由启动期已规范化的 `web.path-prefix` 与对应操作路径组合；默认组合保持原有 `/files/**` 地址不变：

| 端点 | 说明 |
| --- | --- |
| `PUT {prefix}{upload-path}/{*key}` | 默认 `PUT /files/upload/{*key}`。本地预签名上传（HMAC）；无签名时走直接上传授权，并受 `web.max-concurrent-uploads` 限制 |
| `GET {prefix}{download-path}/{*key}` | 默认 `GET /files/download/{*key}`。下载，强制 `attachment` 响应模式 |
| `GET {prefix}{preview-path}/{*key}` | 默认 `GET /files/preview/{*key}`。预览，强制 `inline` 响应模式 |
| `GET {prefix}{read-path}/{*key}` 及 `/preview/{*key}`、`/download/{*key}` | 默认 `GET /files/read/key/{*key}` 及其预览、下载变体；按真实 object key 匿名读取 |
| `GET {prefix}{stable-read-path}/{id}` 及 `/v1/{id}`、预览、下载变体 | 默认 `/files/read/{id}` 和 `/files/read/v1/{id}` 系列；按稳定文件 ID 匿名读取（见下节） |
| `GET {prefix}{legacy-read-path}/{*key}` | 仅 `web.legacy-read-enabled=true` 时注册；默认即 `GET /files/{*key}`，用于兼容历史真实 key 地址 |

例如，下列配置将上传和读取地址迁移到 `/rsm` 下：

```yaml
dinghai:
  file-storage:
    web:
      path-prefix: rsm
      upload-path: upload-file
      download-path: download-file
      preview-path: preview-file
      read-path: read
      stable-read-path: read
      legacy-read-enabled: true
      legacy-read-path: /
```

它会生成 `/rsm/upload-file/{*key}`、`/rsm/download-file/{*key}`、`/rsm/preview-file/{*key}`、`/rsm/read/{*key}`、`/rsm/read/preview/{*key}` 与 `/rsm/{*key}`。当 `stable-read-path` 与 `read-path` 相同或位于其下方时，为避免与真实 key catch-all 产生歧义，不注册无版本稳定 ID 路由；显式版本路由 `/v1/{id}` 及其预览、下载变体仍会注册。不同对象 key 操作路径重合、legacy 路径精确重合或路径包含非法字符会使应用以 `INVALID_CONFIGURATION` 启动失败。

### 大文件分片上传

分片端点也受 `web.enabled=true` 和 `web.path-prefix` 控制，但只有宿主注册 `FileStorageAccessPolicy` Bean 时才会注册。每个请求仍由该策略按当前主体和对象 key 判定 `MULTIPART_UPLOAD`；没有该 Bean 时不会注册这些端点。这与普通 `web.direct-access` 授权无关。

| 端点 | 说明 |
| --- | --- |
| `POST /multipart` | 创建会话，响应 `201 Created`，返回 `uploadId`、分片大小、分片数、过期时间和已确认分片快照 |
| `POST /multipart/{uploadId}/parts/{partNumber}/presign` | 返回该片的上传方式、过期时间和必需请求头 |
| `GET /multipart/{uploadId}/parts` | 返回 provider 已确认的分片清单，按 `partNumber` 排序，是断点续传的权威状态 |
| `PUT /multipart/{uploadId}/parts/{partNumber}` | **仅 Local**：应用流式接收这个分片；远端 provider 调用此路由返回 `MULTIPART_INVALID` |
| `POST /multipart/{uploadId}/complete` | 提交分片清单并完成，成功返回确认后的 `ObjectMetadata` |
| `DELETE /multipart/{uploadId}` | 取消会话，成功返回 `204 No Content` |

下面以 20 MiB 文件和 10 MiB 固定分片为例。创建请求的 `partSize` 必须等于 `multipart.part-size`，`size` 不得超过 `upload.max-size`，`expiresIn` 不得超过 `multipart.session-ttl`：

```bash
curl --request POST https://app.example/files/multipart \
  --header 'Content-Type: application/json' \
  --data '{
    "key":"uploads/video.mp4",
    "size":20971520,
    "partSize":10485760,
    "contentType":"video/mp4",
    "expiresIn":"PT30M"
  }'
```

典型创建响应包含以下字段（`uploadId` 为不透明会话标识）：

```json
{
  "uploadId": "opaque-upload-id",
  "key": {"value": "uploads/video.mp4"},
  "totalSize": 20971520,
  "partSize": 10485760,
  "partCount": 2,
  "expiresAt": "2026-09-15T12:30:00Z",
  "parts": []
}
```

- **Local**：可先调用 `POST /multipart/opaque-upload-id/parts/1/presign` 获取由应用声明的 `PUT` 方法、过期时间与必需头，再向 `PUT /files/multipart/opaque-upload-id/parts/1` 流式发送该片；每片必须恰好等于期望长度。应用以 `multipart.max-concurrent-uploads` 限制此类写入，额度耗尽时在读取请求体前返回 `429` 和 `Retry-After: 1`。
- **MinIO / RustFS / 阿里云 OSS / 华为云 OBS**：调用同一 `presign` 端点后，客户端必须按响应的 `url`、`method`、`requiredHeaders` 直接执行 provider 原生 `PUT`；分片内容不经过应用。不得记录、缓存或提交完整预签名 URL。
- 分片失败或网络中断时，可对**同一 partNumber** 重新上传，后一次会替换前一次。恢复前先 `GET /multipart/{uploadId}/parts`，仅按该清单中的 `partNumber`、`size`、`etag` 组装完成请求；不要猜测本地状态。

完成请求必须提交服务端已确认的所有分片，`idempotencyKey` 可由客户端携带用于调用侧追踪：

```bash
curl --request POST https://app.example/files/multipart/opaque-upload-id/complete \
  --header 'Content-Type: application/json' \
  --data '{
    "parts":[
      {"partNumber":1,"size":10485760,"etag":"provider-etag-1"},
      {"partNumber":2,"size":10485760,"etag":"provider-etag-2"}
    ],
    "idempotencyKey":"client-completion-token"
  }'
```

在会话 TTL 内，同一会话的重复完成返回首次确认的对象元数据；重复取消也保持 `204` 成功语义。未完成会话会在过期后由清理任务回收，过期会话不能恢复，需重新创建。

#### 前端接入示例

下面是一个零依赖的浏览器上传函数，覆盖创建会话、并发分片上传、断点续传（以服务端清单为准）和完成，复制即用：

```js
/**
 * 大文件分片上传（dinghai-file-storage）。
 * 前提：页面已通过 Cookie 等方式携带应用登录凭证。
 *
 * @param {File} file 输入框选择的文件
 * @param {string} key 对象 key，如 "uploads/video.mp4"
 * @returns {Promise<object>} 完成后的对象元数据
 */
async function uploadLargeFile(file, key, {
  baseUrl = '/files',                // 等于服务端 web.path-prefix
  partSize = 10 * 1024 * 1024,       // 必须等于服务端 multipart.part-size
  expiresIn = 'PT30M',               // 不得超过服务端 multipart.session-ttl
  concurrency = 3,
  resume = null,                     // 断点续传：{ uploadId, partSize, partCount }，见下方说明
  onProgress,
} = {}) {
  async function request(path, init = {}) {
    const response = await fetch(baseUrl + path, { credentials: 'same-origin', ...init });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(`${response.status} ${body.code ?? ''} ${body.message ?? ''}`);
    }
    return response;
  }
  const post = (path, body) => request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  // 1. 创建会话；传入 resume 时改为拉取服务端已确认分片继续上传
  const session = resume
    ? {
        uploadId: resume.uploadId,
        partSize: resume.partSize,
        partCount: resume.partCount,
        parts: await (await request(`/multipart/${resume.uploadId}/parts`)).json(),
      }
    : await (await post('/multipart', {
        key,
        size: file.size,
        partSize,
        contentType: file.type || 'application/octet-stream',
        expiresIn,
      })).json();
  const confirmed = new Map((session.parts ?? []).map((p) => [p.partNumber, p]));
  let uploadedBytes = [...confirmed.values()].reduce((sum, p) => sum + p.size, 0);

  // 2. 逐片 presign 后上传
  async function uploadPart(partNumber) {
    if (confirmed.has(partNumber)) return;
    const start = (partNumber - 1) * session.partSize;
    const blob = file.slice(start, Math.min(start + session.partSize, file.size));
    const presign = await (await post(
      `/multipart/${session.uploadId}/parts/${partNumber}/presign`)).json();

    // presign.requiredHeaders 必须原样带回
    const response = await fetch(presign.url, {
      method: presign.method,
      headers: presign.requiredHeaders ?? {},
      body: blob,
      credentials: 'same-origin',
    });
    if (!response.ok) throw new Error(`part ${partNumber} failed: ${response.status}`);

    // Local 后端的 etag 在响应 JSON 中；MinIO/RustFS/OSS 的 etag 在 ETag 响应头中
    const etagHeader = response.headers.get('ETag');
    const part = etagHeader
      ? { partNumber, size: blob.size, etag: etagHeader.replaceAll('"', '') }
      : await response.json();
    confirmed.set(partNumber, part);
    uploadedBytes += part.size;
    onProgress?.(uploadedBytes, file.size);
  }
  const queue = Array.from({ length: session.partCount }, (_, i) => i + 1);
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (queue.length > 0) await uploadPart(queue.shift());
  }));

  // 3. 提交服务端已确认的全部分片并完成
  const parts = [...confirmed.values()].sort((a, b) => a.partNumber - b.partNumber);
  return (await post(`/multipart/${session.uploadId}/complete`, { parts })).json();
}
```

使用方式：

```js
const metadata = await uploadLargeFile(fileInput.files[0], 'uploads/video.mp4', {
  onProgress: (done, total) => console.log(`${((done / total) * 100).toFixed(1)}%`),
});
console.log('上传完成', metadata.key.value, metadata.etag);
```

注意事项：

- 分片失败时直接重试该 `partNumber` 即可（后一次覆盖前一次）。
- 跨页面刷新断点续传：首次上传时在创建会话后把 `{ uploadId, partSize, partCount }` 存入 `localStorage`，下次以 `resume` 传入即可自动跳过已确认分片；以服务端 `GET /multipart/{uploadId}/parts` 清单为准，不要凭本地状态猜测。
- 使用 MinIO / RustFS / 阿里云 OSS / 华为云 OBS 时，分片由浏览器直传 provider，必须在 bucket 上配置 CORS：允许页面来源、`PUT` 方法和 `presign.requiredHeaders` 中出现的请求头，并将 `ETag` 加入暴露响应头（Expose-Headers），否则浏览器读不到 etag。
- 任何一步返回 `429` 时按 `Retry-After` 提示等待后重试；`MULTIPART_EXPIRED`（410）说明会话已过期，需丢弃 `resume` 信息重新创建。

迁移管理端点固定为 `POST /files/migration/export`、`POST /files/migration/import`（参数 `packageName`），不随 `web.path-prefix` 变化。

`PUT /upload/{*key}` 成功后响应体与 SDK `FileStorage.put` 的 `ObjectMetadata` 字段保持一致，并额外返回 `previewUrl`：

```json
{
  "key": {"value": "1/1/1/1.png"},
  "contentLength": 5,
  "contentType": "image/png",
  "etag": "provider-confirmed-etag",
  "lastModified": "2026-09-12T15:37:23Z",
  "cacheControl": {"value": "private, max-age=0"},
  "previewUrl": "/files/read/key/preview/1/1/1/1.png"
}
```

响应状态为 `201 Created`，`Location` 等于 `previewUrl`。`key`、长度、类型、ETag、修改时间和缓存策略都取自 provider 持久化后的确认结果，而不是客户端声明值。`previewUrl` 是配置 `web.path-prefix` 下的根相对地址，使用真实 object key（保留 `/` 层级并对路径字符编码）；浏览器可直接访问组件的匿名真实 key 预览端点。它不是本地磁盘路径、MinIO/RustFS/OSS 原始 URL，也不包含部署主机名，反向代理或应用边界可按需补充公开域名。

预览/下载支持 `Content-Type`、ETag、Last-Modified、Cache-Control、Expires、304（If-None-Match / If-Modified-Since）和单区间 Range 206/416。Local 主文件和已校验远端热缓存文件在明文嵌入 Tomcat 中优先注册原生 `sendfile`：默认开启时，常见图片、音视频、压缩包、PDF 和字体等已确认标准二进制 MIME 即使请求接受 gzip 也可保留该路径；文本、SVG、未知或非法 MIME 继续使用有界流式复制。TLS、其他容器或容器不支持时自动回退，调用方无需切换接口。

## 预签名与访问授权

- **Local 后端**：组件自实现的 HMAC-SHA-256 预签名。签名 URL 携带 `expires`（Unix 秒）、`signature`（Base64URL）等查询参数，上传签名还可绑定 `contentType`、`maxSize`、`cacheControl`；下载签名绑定 `inline` 或 `attachment` 响应模式。有效期受 `security.maximum-presign-expiry` 限制。
- **MinIO / RustFS / 阿里云 OSS / 华为云 OBS 后端**：使用各自服务原生预签名。Local 签名校验仅 Local 适配器支持，对非 Local 后端走本地签名 URL 会抛 `INVALID_PRESIGN`。
- **阿里云 OSS 后端**：使用 V2 原生预签名（带 `requiredHeaders` 的 `PUT` 上传、`GET` 预签名下载）；仅支持由 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET` 注入的固定 AccessKey，不支持 STS 临时凭证。本地签名校验仅 Local 适配器支持，对阿里云 OSS 走本地签名 URL 会抛 `INVALID_PRESIGN`。
- **直接上传**：无签名上传默认拒绝；必须同时满足 `web.direct-access=true` 且宿主提供 `FileStorageAccessPolicy` Bean 并对 UPLOAD 返回允许。Web 下载、预览和读取不要求组件凭证，但受读取并发限制保护。
- 所有 Bean 均带 `@ConditionalOnMissingBean`，宿主可替换默认实现（包括 `FileStorage` 本身）。

### 宿主按 key 隔离传输并发

设置正数 `web.max-concurrent-transfers-per-key` 后，宿主可注册 `WebTransferRateLimitKeyResolver` Bean，以已验证的用户、租户或 API 客户端身份返回 key：

```java
@Bean
WebTransferRateLimitKeyResolver webTransferRateLimitKeyResolver() {
    return request -> currentTenantId();
}
```

组件不从 IP、JWT、Cookie、转发头或 API Key 推导 key，也不会记录或回显该值。未注册 resolver、resolver 返回 `null`/空白值或抛出运行时异常时，请求仅受全局上传/读取上限限制。每 key 上限与全局上限叠加；同一 key 的上传与读取使用独立额度，且 SDK 的 `FileStorage.put/get` 不受 Web 限流影响。

## 稳定文件 ID

稳定 ID 是私有对象的持久读取地址（`/files/read/v1/{id}` 系列）。ID 由 AES-SIV（Tink）对长度前缀的 `storage-alias + object key` 做确定性认证加密得到，输出 `v1.` 前缀的 Base64URL 字符串。因此：

- 同一对象跨 Local / MinIO / RustFS / 阿里云 OSS / 华为云 OBS 迁移后 ID 不变（前提：`storage-alias` 不变）；
- 轮换 `security.stable-id-key` 会使全部旧 ID 失效；
- 该路由**不执行组件内用户鉴权**，嵌入方负责浏览器前的权限控制。

## 读取缓存行为

MinIO、RustFS 与阿里云 OSS 默认启用经远端 `stat` 校验的二级磁盘读取缓存；华为云 OBS 当前不创建二级缓存；Local 也不创建二级缓存或清理器：

1. 每次读先 `stat` 远端元数据；本地副本的 ETag / 长度 / 修改时间与远端一致才命中，命中后刷新最近访问时间；
2. 未命中且对象不超过 `cache.max-object-size` 时，当前请求直接流式读取远端，另开第二条流异步落盘（临时文件 + 原子移动发布）；
3. `put` / `delete` 立即失效对应副本；
4. 每天 00:00（服务器本地时区）清理超过 `cache.idle-ttl` 未访问的副本，清理失败静默留待下轮；
5. 缓存文件名是 `SHA-256(alias + key)` 的十六进制摘要，不泄露原始对象 key。

## 零拷贝与并发

- core 提供可选的 `LocalFileSource` 接口（`Optional<Path> localFile(ObjectKey)`）：Local 适配器和远程读取缓存在命中已校验副本时实现它，向 Web 层暴露经过根目录校验的本地文件路径；不实现该接口的 provider 只走流式路径。
- Local 主文件和远程热缓存命中时，Tomcat 明文请求优先使用原生 `sendfile`，不按文件大小分配 JVM 堆内存。默认 `web.sendfile-binary-content-types-enabled=true` 时，已确认的标准二进制 MIME 类型可在 gzip 等压缩协商下继续走该路径。
- 文本、SVG、未知或非法 MIME 类型在压缩协商下始终降级为有界流式复制；将该开关设为 `false` 可让全部压缩协商请求恢复保守的流式路径，适用于宿主对二进制内容启用了自定义压缩的场景。
- TLS、非 Tomcat 容器、文件暂不可用或容器拒绝注册时自动降级为有界流式复制，响应内容、条件请求和 Range 语义保持不变。
- 全局读取上限和可选的每 key 读取上限均覆盖流式回退、缓存文件和 Tomcat `sendfile`，读取许可保持到响应完成或失败；并发达到上限时立即返回 HTTP 429。
- Local 升级时必须删除 `dinghai.file-storage.cache.enabled=true`；该配置已移除，继续保留会使应用启动失败。

## 离线迁移

离线迁移只处理服务器目录包，不通过 HTTP 上传大文件。导出产生 `manifest.ndjson`（含每个对象的 SHA-256 与长度）和 `objects/` 目录；导入前对清单做全量校验，避免部分导入。导入成功后包移动到 completed，失败包及 `errors.ndjson` 移动到 failed。管理端点仅在 `migration.enabled=true` 且宿主提供 `FileStorageMigrationAccessPolicy` 时注册，策略必须分别授权 `EXPORT` 与 `IMPORT` 操作。

## 错误码

统一错误码定义在 `FileStorageErrorCode`：`INVALID_REQUEST`、`OBJECT_NOT_FOUND`、`INVALID_OBJECT_KEY`、`OBJECT_TOO_LARGE`、`CONTENT_TYPE_NOT_ALLOWED`、`AUTHENTICATION_FAILED`、`STORAGE_UNAVAILABLE`、`INVALID_PRESIGN`、`INVALID_CONFIGURATION`、`RATE_LIMITED`、`MULTIPART_INVALID`、`MULTIPART_NOT_FOUND`、`MULTIPART_EXPIRED`、`MULTIPART_CONFLICT`、`INTERNAL_ERROR`。

Web 层 HTTP 映射：`OBJECT_NOT_FOUND` / `MULTIPART_NOT_FOUND` → 404，`MULTIPART_EXPIRED` → 410，`MULTIPART_CONFLICT` → 409，`OBJECT_TOO_LARGE` → 413，`INVALID_PRESIGN` / `AUTHENTICATION_FAILED` → 403，`STORAGE_UNAVAILABLE` → 503，`RATE_LIMITED` → 429（携带 `Retry-After: 1`），`MULTIPART_INVALID` 及其余参数错误 → 400；响应体为 `{code, message}`。

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

分片生命周期真实验收默认跳过，只有显式注入临时测试凭据时执行。MinIO 测试创建临时 bucket，RustFS 测试也创建并删除临时 bucket：

```bash
export DINGHAI_FILE_STORAGE_MINIO_IT=true
export DINGHAI_FILE_STORAGE_MINIO_ENDPOINT=http://localhost:9000
export DINGHAI_FILE_STORAGE_MINIO_ACCESS_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_MINIO_SECRET_KEY=CHANGE_ME
mvn -pl minio -am -Dtest=MinioMultipartLifecycleIntegrationTest test

export DINGHAI_FILE_STORAGE_RUSTFS_IT=true
export DINGHAI_FILE_STORAGE_RUSTFS_ENDPOINT=http://localhost:9100
export DINGHAI_FILE_STORAGE_RUSTFS_ACCESS_KEY=CHANGE_ME
export DINGHAI_FILE_STORAGE_RUSTFS_SECRET_KEY=CHANGE_ME
mvn -pl rustfs -am -Dtest=RustfsLifecycleIntegrationTest test
```

阿里云 OSS 真实验收在已注入凭证时执行，使用 `dinghai` bucket 的随机 `__dinghai_it__/` 对象并在结束时删除：

```bash
export OSS_ACCESS_KEY_ID=CHANGE_ME
export OSS_ACCESS_KEY_SECRET=CHANGE_ME
mvn -pl aliyun-oss -am -Dtest=AliyunOssLifecycleIntegrationTest test
```

华为云 OBS 默认只执行无外部网络的单元和自动装配测试；需要真实验收时，应在专用测试账号、预创建 bucket 和临时凭证下增加受控集成测试，不要把 AK/SK、Security Token 或完整预签名 URL 写入日志或仓库。
