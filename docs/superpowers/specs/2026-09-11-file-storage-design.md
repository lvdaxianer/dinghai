# 文件存储适配层设计

## 背景

业务系统需要在本地磁盘、MinIO 与后续云对象存储之间切换，业务代码不应依赖任何厂商 SDK。组件还需要覆盖预签名上传/下载、在线预览和 HTTP/CDN 缓存，同时保证对象 key、文件流、凭据和访问 URL 的安全边界。

## 设计结论

新增四个模块：

```text
dinghai-file-storage-core
dinghai-file-storage-local
dinghai-file-storage-minio
dinghai-file-storage-spring-boot-starter
```

core 定义 `FileStorage` 与后端中立模型；local 实现根目录封闭、中文多级 key、原子写入和 HMAC 签名；minio 使用固定 bucket、原生 GET 签名和 POST policy；starter 负责配置、provider 路由、可选 `/files` HTTP 端点和 `FileStorageAccessPolicy`。

公共操作为 put、get、stat、exists、delete、presignUpload、presignDownload。读取/元数据不存在返回 `Optional.empty()`，删除不存在返回 `false`，put 覆盖同 key。对象 key 允许 UTF-8 中文与多级路径，但拒绝绝对路径、`.`、`..`、反斜杠、控制字符和超长 key；路径 URL 解码后再次校验。

预签名 URL 是限时 bearer capability，在有效期内可重放。签名绑定规范化 key、HTTP 方法、过期时间、大小/类型限制、预览模式和缓存策略；签名密钥从环境注入，不能写日志。普通下载/预览默认关闭，默认路径为 `/files`，可配置路径前缀和授权策略。

预览只输出原始文件流，使用 `Content-Disposition: inline`；下载使用 `attachment`。HTTP 层提供 ETag、Last-Modified、Cache-Control、Expires、304 条件请求和单范围流式响应，不实现应用内文件字节缓存、Office 转码、缩略图或视频转码。

上传默认上限 100 MB，可配置内容类型白名单。MinIO 配置缺失、bucket 不存在或生产签名密钥缺失时快速失败，不自动创建 bucket、不自动回退本地。适配器异常统一分类，日志只保留安全摘要，Controller 不打业务日志。

## 验证策略

core 和 local 使用契约测试；MinIO 使用 mock SDK/HTTP 与可选 Testcontainers；starter 使用 Spring Boot context 与 MockMvc 验证条件装配、签名端点、缓存头和授权策略。全量构建必须通过；真实 MinIO 测试仅在显式开关下运行。
