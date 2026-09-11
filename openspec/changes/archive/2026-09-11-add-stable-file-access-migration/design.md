## Context

当前 `FileStorage#get` 已能返回统一流，但 Web Controller 仅接受
`LocalFileStorageAdapter`；MinIO 私有 bucket 无法走同一浏览器读取路径。现有
预签名 URL 会过期且地址变化，不适合嵌入平台长期保存。迁移还需要在服务器目录
间搬运大文件，不能经 API 请求体。

## Goals / Non-Goals

**Goals:**

- 用确定性认证加密建立可重建、不可解码且无需数据库的稳定读取 ID。
- 用稳定 ID 代理 Local/MinIO 私有对象的匿名、流式预览与下载。
- 为非本地 provider 提供经远程元数据校验的二级磁盘缓存和每日空闲清理。
- 以 manifest + SHA-256 的目录包支持 Local 到 MinIO 的大文件离线迁移。
- 让完整包在成功或失败后分别落入固定终态目录。

**Non-Goals:**

- 不实现 OSS 厂商适配器；其以后通过 `FileStorage` 契约接入。
- 不接受完整 URL、不从远程地址下载、不通过 HTTP 上传迁移包。
- 不承诺对象存储的内核零拷贝；承诺全程流式、常量级缓冲内存。
- 不提供每个固定 ID 的单独吊销或历史密钥读取。

## Decisions

- 使用 Google Tink 的 AES-SIV（RFC 5297）确定性认证加密。明文严格为
  `version + storageAlias + ObjectKey` 的长度前缀 UTF-8 二进制结构，密文以
  Base64URL 编码。AES-SIV 比“加密后加 HMAC”更适合确定性、抗篡改需求；ID
  不含 bucket/provider，故同 alias/key 在本地和 MinIO 环境保持一致。
- `storageAlias`、AES-SIV key、导入/导出/完成/失败根目录均为明确配置。生产
  缺失 active key 或根目录即 fail fast；key 从环境变量或部署密钥注入获得，不能
  记录进日志、异常或 manifest。
- `StableFileAccessService` 负责从 `FileReference` 生成/解析 ID；Web Controller
  仅接受已解析对象 key，且 stable 路由不调用 `FileStorageAccessPolicy`。保留旧
  `/preview` 与 `/download` 的签名/宿主授权语义以兼容已有调用方。
- 二级缓存由 `ProviderFileReadCache` 抽象处理。缓存开关按 provider 选择：仅
  `local`（磁盘）provider 默认关闭，所有非 `local` provider 默认开启；显式
  `cache.enabled` 可覆盖两类默认值。缓存文件名由
  `storageAlias + ObjectKey` 的 SHA-256
  生成，sidecar 记录 ETag、长度、最后修改时间和最后访问时间。缓存命中先调用
  provider `stat`，完全一致时以本地文件通道响应；不一致或对象不存在时删除副本。
- 未命中且对象不超过 `max-object-size` 时，响应线程仍直接读取 provider 流；缓存
  专用有界执行器另行打开第二条 provider 流，将内容写入临时文件、核验 metadata
  后原子发布。两个消费者绝不共享一个输入流，缓存任务失败不影响当前请求。
- 通过 `@Scheduled(cron = "0 0 0 * * *")` 在服务端本地时区每天 00:00 清理
  `lastAccessAt + idleTtl < now` 的完整副本；清理失败保留供下一轮重试。缓存清理
  和填充不输出对象 URL、ID 明文、密钥、签名或内容。
- 在 `FileStorage` 新增可关闭 `Stream<ObjectMetadata>` 枚举方法。Local 使用
  `Files.walk` 过滤 sidecar 目录，MinIO 用 SDK lazy iterator，并在消费每项时
  `stat` 取得内容类型与缓存策略；迁移 service 总是 try-with-resources 关闭枚举
  和对象流。
- 迁移包使用 UTF-8 NDJSON manifest，每条独立记录；对象内容放在由 SHA-256(key)
  产生的包内相对路径，避免文件系统路径穿越和冲突。导出先写临时目录，全部成功
  后原子移动为 package name。
- 导入先验证 manifest 路径、对象 key、长度、摘要和 metadata；目标 key 已存在
  时比较目标流 SHA-256，相同则跳过，不同则报冲突。任何失败立即停止后续写入，
  将包移至 failed root 并追加安全 `errors.ndjson`；全成功移动到 completed root。
- 导入导出入口仅接收严格 package name（ASCII 字母数字、`-`、`_`，长度受限），
  所有目录均在配置根目录内二次规范化检查，防止路径穿越。

## Risks / Trade-offs

- [Risk] 固定匿名地址泄漏即拥有读取能力。 -> Mitigation: ID 不可枚举、对象 key
  不泄露；平台负责地址分发；轮换活动 key 可以整体失效并重新生成地址。
- [Risk] 确定性加密会让相同引用产生相同密文。 -> Mitigation: 此稳定性是业务需求；
  输入域只含非敏感逻辑 alias/key，且地址不会解密暴露 key。
- [Risk] 导入失败可能已写入部分目标对象。 -> Mitigation: 每对象写入前完整校验
  source，并且失败 manifest 留存可重跑；首版不提供跨 provider 事务或自动回滚。
- [Risk] 全量 SHA-256 增加 I/O。 -> Mitigation: 流式计算，优先正确性和迁移包可
  搬运性，不占用整文件内存。
- [Risk] 同盘 move 和跨盘 move 行为不同。 -> Mitigation: 优先原子 move，失败时
  使用受控递归移动，失败保留原包与明确错误记录。
- [Risk] 缓存命中仍需要一次 MinIO `stat` 请求。 -> Mitigation: 该校验是避免删除或
  重建同 key 后读到旧内容所需的正确性成本，且比下载对象内容开销低。
- [Risk] 异步缓存填充增大 provider 并发读取。 -> Mitigation: 设置最大对象大小、
  有界队列、有限线程数和同一 key 单飞锁；队列饱和时跳过填充而不阻断读取。

## Migration Plan

1. 配置相同 `storage-alias` 与 active stable ID key，并为目标环境配置不同的
   provider/bucket 或本地根目录。
2. 在源环境导出 package，运维将整个目录复制到目标 import root。
3. 调用导入接口仅传 package name，等待成功进入 completed root 或检查 failed
   root 的 `errors.ndjson`。
4. 业务保存的稳定地址无需因后端迁移而修改；若轮换 ID key，则由业务根据原
   alias/key 重新生成地址并替换原引用。
5. 回滚 provider 前保留已完成迁移包，以便反向导入；不删除源对象，直到业务
   验收完成。

## Open Questions

None.
