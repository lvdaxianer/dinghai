## MODIFIED Requirements

### Requirement: 本地磁盘适配器安全持久化
本地适配器 SHALL 仅在配置的根目录下读写对象。Starter 使用公共
`dinghai.file-storage.bucket` 将适配器根目录映射为 `local.root / bucket`；它 MUST 将规范化
key 解析后再次检查目标路径仍位于该 bucket 根目录；写入 MUST 通过同文件系统临时文件完成，
校验完成后原子替换目标；需要时 MUST 创建父目录。元数据持久化失败 MUST 使写入报告失败，
读取缺失元数据的既有内容时 MUST 使用私有不可缓存的安全默认值。

#### Scenario: 本地对象映射到 bucket 下的嵌套目录
- **WHEN** 本地目录根为 `/data/files`、公共 bucket 为 `documents` 且写入 key
  `1/1/1/1.png`
- **THEN** 内容保存到 `/data/files/documents/1/1/1/1.png`，并在需要时创建父目录

#### Scenario: 写入失败不暴露半文件为成功对象
- **WHEN** 本地上传流在写入完成前失败或长度校验失败
- **THEN** 调用报告失败，且目标 key 不会被新的半文件成功替换

### Requirement: MinIO 固定 bucket 适配器
MinIO 适配器 SHALL 使用公共 `dinghai.file-storage.bucket` 指定的固定 bucket 执行对象操作，
并通过可配置连接、读取与写入超时调用 MinIO。它 MUST NOT 自动创建 bucket；缺失 endpoint、
公共 bucket、凭据或超时配置时 MUST 在启动时失败。SDK 的认证、服务端、超时和通用错误 MUST
映射为稳定的文件存储异常，不得暴露密钥或内部 endpoint。

#### Scenario: 完整 MinIO 配置时使用公共 bucket
- **WHEN** provider 配置为 `minio` 且 endpoint、公共 bucket、凭据和超时均有效
- **THEN** 所有对象操作仅针对该公共 bucket 执行

#### Scenario: MinIO bucket 不存在
- **WHEN** 公共 bucket 不存在
- **THEN** 操作返回可分类的存储失败，组件不会自动创建 bucket

### Requirement: 可扩展的后端选择
Spring Boot Starter SHALL 根据 `dinghai.file-storage.provider` 在 `local` 与 `minio` 中选择且
仅选择一个主 `FileStorage` Bean。每个 provider MUST 配置公共 bucket；未知 provider、公共 bucket
不安全或所选 provider 的配置不完整时 MUST fail fast；组件 MUST NOT 在 MinIO 失败时自动写入本地
磁盘。

#### Scenario: 选择本地后端
- **WHEN** provider 为 `local`、本地根目录有效且公共 bucket 有效
- **THEN** 容器中注册以 `local.root / bucket` 为对象根的本地 `FileStorage` 主 Bean，且 MinIO
  客户端不参与装配

#### Scenario: 拒绝未配置 provider 或 bucket
- **WHEN** provider 值未知、公共 bucket 缺失/不安全，或所选 provider 的必需属性缺失
- **THEN** 应用在启动期间以明确配置错误失败，而不是使用隐式开发默认值
