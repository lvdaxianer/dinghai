# file-storage Specification

## Purpose

统一文件存储适配层为本地磁盘、MinIO、RustFS 和阿里云 OSS 提供后端无关的对象契约、持久化安全边界与 Spring Boot provider 路由。

## Requirements

### Requirement: 后端中立的文件存储契约
组件 SHALL 在 `dinghai-file-storage-core` 提供不含供应商 SDK 类型的 `FileStorage` 契约，支持对象覆盖写入、流式读取、元数据查询、存在判断、幂等删除、预签名上传和预签名下载，以及可关闭的惰性对象枚举。读取和元数据查询不存在对象时 MUST 返回 `Optional.empty()`；删除不存在对象时 MUST 返回 `false`；写入相同 key 时 MUST 覆盖既有对象。MinIO 预签名 POST 上传 MUST 在 `PresignedUpload.formFields` 返回服务端 policy 所要求的固定 bucket、对象 key、内容类型、缓存策略及 SDK 生成的签名字段；调用方提交全部表单字段时，服务端 MUST 能验证该 policy。逻辑 storage alias MUST 与 provider、bucket 和磁盘根目录分离，使后端迁移不改变逻辑对象引用。

#### Scenario: 通过统一契约覆盖并读取对象
- **WHEN** 调用方通过 `FileStorage` 将对象写入一个 key 后再读取该 key
- **THEN** 读取结果包含可关闭的流和与写入匹配的统一元数据，且不需要使用本地或 MinIO SDK 类型

#### Scenario: 删除缺失对象
- **WHEN** 调用方删除一个不存在的对象 key
- **THEN** 操作返回 `false`，且不会将对象不存在作为后端故障抛出

#### Scenario: 提交 MinIO 预签名 POST 表单
- **WHEN** 调用方使用 MinIO 适配器为对象生成预签名 POST 上传并提交返回的全部表单字段和文件内容
- **THEN** 表单包含 bucket、key、内容类型、缓存策略与 SDK 签名字段，且 MinIO 接受上传并保存匹配的对象元数据

#### Scenario: 惰性枚举对象
- **WHEN** 迁移服务遍历包含大量对象的 provider
- **THEN** 枚举逐项提供统一元数据且在关闭前不需要将全部 key 或元数据加载到内存

### Requirement: 安全且 Unicode 兼容的对象 key
组件 SHALL 允许 UTF-8 中文和多级相对对象 key，并在任何存储、读取、删除和 URL 签名操作前用同一规则规范化和验证。key MUST 拒绝空值、绝对路径、空路径段、`.`、`..`、反斜杠、NUL 与控制字符；完整 key UTF-8 长度 MUST 不超过 1024 bytes，单路径段 MUST 不超过 255 bytes。URL 解码后的 key MUST 再次验证。

#### Scenario: 写入中文多级对象 key
- **WHEN** 调用方写入 key `用户资料/头像/1.png`
- **THEN** 本地和 MinIO 适配器均接受该 key，并将其视为同一个逻辑对象标识

#### Scenario: 拒绝路径穿越 key
- **WHEN** 调用方传入 `../secret.txt`、`tenant/../../secret.txt` 或 URL 编码后可解析为这些路径的 key
- **THEN** 调用在接触后端前以稳定的 key 校验错误失败

### Requirement: 配置化上传限制
组件 SHALL 对普通上传和预签名上传应用相同的最大文件大小及内容类型策略。`dinghai.file-storage.upload.max-size` 默认 MUST 为 100 MB；空内容类型白名单 MUST 表示不限制类型，非空白名单 MUST 拒绝不匹配内容类型；缺失内容类型 MUST 规范化为 `application/octet-stream`。

#### Scenario: 上传超过配置上限
- **WHEN** 普通上传或预签名上传的内容长度超过配置的最大大小
- **THEN** 上传被拒绝，且适配器不得持久化超过上限的完整对象

#### Scenario: 白名单限制内容类型
- **WHEN** 配置了允许的内容类型且上传声明的类型不在白名单中
- **THEN** 普通上传和预签名上传均以同一稳定校验错误失败

### Requirement: 本地磁盘适配器安全持久化
本地适配器 SHALL 仅在配置的根目录下读写对象。Starter 使用公共
`dinghai.file-storage.bucket` 将适配器根目录映射为 `local.root / bucket`；它 MUST 将规范化
key 以 UTF-8 文件系统字节解析后再次检查目标路径仍位于该 bucket 根目录，且不得依赖
运行时的 `native.encoding` 或 `sun.jnu.encoding` 表示该 key。写入 MUST 通过同文件系统临时文件完成，校验完成后原子替换目标；需要时 MUST 创建父目录。元数据持久化失败 MUST 使写入报告失败，读取缺失元数据的既有内容时 MUST 使用私有不可缓存的安全默认值。

#### Scenario: 本地对象映射到嵌套目录
- **WHEN** 本地目录根为 `/data/files`、公共 bucket 为 `documents` 且写入 key
  `1/1/1/1.png`
- **THEN** 内容保存到 `/data/files/documents/1/1/1/1.png`，并在需要时创建父目录

#### Scenario: 非 ASCII 本地对象 key
- **WHEN** 配置根目录中包含 UTF-8 字节名 `中文目录/板卡.gltf` 且 JDK 本机编码无法表示中文
- **THEN** 本地适配器以同一 Unicode 或 UTF-8 百分号编码 key 读取、写入和查询该对象

#### Scenario: 写入失败不暴露半文件为成功对象
- **WHEN** 本地上传流在写入完成前失败或长度校验失败
- **THEN** 调用报告失败，且目标 key 不会被新的半文件成功替换

### Requirement: MinIO 固定 bucket 适配器
MinIO 适配器 SHALL 使用公共 `dinghai.file-storage.bucket` 指定的固定 bucket 执行对象操作，
并通过可配置连接、读取与写入超时调用 MinIO。它 MUST NOT 自动创建 bucket；缺失 endpoint、公共
bucket、凭据或超时配置时 MUST 在启动时失败。SDK 的认证、服务端、超时和通用错误 MUST 映射为稳定的
文件存储异常，不得暴露密钥或内部 endpoint。RustFS 适配器 SHALL 以相同约束使用公共
`dinghai.file-storage.bucket` 和可配置超时调用 RustFS 的 S3 兼容 API，且 MUST NOT 自动创建或变更 bucket。

#### Scenario: 完整 MinIO 配置时使用固定 bucket
- **WHEN** provider 配置为 `minio` 且 endpoint、公共 bucket、凭据和超时均有效
- **THEN** 所有对象操作仅针对该公共 bucket 执行

#### Scenario: MinIO bucket 不存在
- **WHEN** 配置的 bucket 不存在
- **THEN** 操作返回可分类的存储失败，组件不会自动创建 bucket

#### Scenario: 完整 RustFS 配置时使用固定 bucket
- **WHEN** provider 配置为 `rustfs` 且 endpoint、公共 bucket、凭据和超时均有效
- **THEN** 所有对象操作仅针对该公共 bucket 执行

#### Scenario: RustFS bucket 不存在
- **WHEN** RustFS 配置的 bucket 不存在
- **THEN** 操作返回可分类的存储失败，组件不会自动创建 bucket

### Requirement: 可扩展的后端选择
Spring Boot Starter SHALL 根据 `dinghai.file-storage.provider` 在 `local`、`minio`、`rustfs` 与 `aliyun-oss` 中选择且仅选择一个主 `FileStorage` Bean。每个 provider MUST 配置公共 bucket；旧值 `oss`、未知 provider、公共 bucket
不安全或所选 provider 的配置不完整时 MUST fail fast；组件 MUST NOT 在远程 provider 失败时自动写入本地磁盘。

#### Scenario: 选择本地后端
- **WHEN** provider 为 `local`、本地根目录有效且公共 bucket 有效
- **THEN** 容器中注册以 `local.root / bucket` 为对象根的本地 `FileStorage` 主 Bean，且远程客户端不参与装配

#### Scenario: 选择 RustFS 后端
- **WHEN** provider 为 `rustfs`、RustFS endpoint、凭据、超时和公共 bucket 有效
- **THEN** 容器中注册 RustFS `FileStorage` 主 Bean，且 Local 和 MinIO 适配器不参与装配

#### Scenario: 选择阿里云 OSS 后端
- **WHEN** provider 为 `aliyun-oss`、OSS region、环境凭据、超时和公共 bucket 有效
- **THEN** 容器中注册阿里云 OSS `FileStorage` 主 Bean，且其他 provider 的适配器不参与装配

#### Scenario: 拒绝未配置 provider
- **WHEN** provider 值未知、公共 bucket 缺失/不安全，或所选 provider 的必需属性缺失
- **THEN** 应用在启动期间以明确配置错误失败，而不是使用隐式开发默认值

### Requirement: Remote provider read cache
The Spring Boot starter SHALL enable the bounded disk read cache by default for every non-Local provider, currently MinIO, RustFS, and Alibaba Cloud OSS, unless `dinghai.file-storage.cache.enabled=false`. It SHALL reject a Local configuration that explicitly enables the second-level cache and SHALL require a cache root for every enabled remote-provider cache.

#### Scenario: RustFS uses the default cache
- **WHEN** `provider: rustfs` is configured and `cache.enabled` is omitted
- **THEN** the starter SHALL create the same remote read-cache layer used by MinIO

#### Scenario: Alibaba Cloud OSS uses the default cache
- **WHEN** `provider: aliyun-oss` is configured and `cache.enabled` is omitted
- **THEN** the starter SHALL create the same remote read-cache layer used by MinIO

#### Scenario: Local cache is explicitly enabled
- **WHEN** `provider: local` is configured with `cache.enabled=true`
- **THEN** startup SHALL fail with the established invalid-configuration error
