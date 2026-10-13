## MODIFIED Requirements

### Requirement: 本地磁盘适配器安全持久化
本地适配器 SHALL 仅在配置的根目录下读写对象。Starter 使用公共
`dinghai.file-storage.bucket` 将适配器根目录映射为 `local.root / bucket`；它 MUST 将规范化
key 以 UTF-8 文件系统字节解析后再次检查目标路径仍位于该 bucket 根目录，且不得依赖
运行时的 `native.encoding` 或 `sun.jnu.encoding` 表示该 key。写入 MUST 通过同文件系统临时文件完成，校验完成后原子替换目标；需要时 MUST 创建父目录。元数据持久化失败 MUST 使写入报告失败，读取缺失元数据的既有内容时 MUST 使用私有不可缓存的安全默认值。Local 删除对象成功后 SHALL 按配置回收 root 内的空父目录；Local 专用前缀删除 SHALL 在删除匹配对象和元数据后回收 root 内产生的空目录。上述删除操作 MUST NOT 改变 MinIO、RustFS、OSS 或 OBS 适配器行为。

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

#### Scenario: 本地删除后回收空父目录
- **WHEN** Local 删除对象文件和元数据成功，且 `cleanup-empty-directories` 保持默认值 `true`
- **THEN** 从对象父目录开始向上删除 root 内的空目录，但保留存储 root、非空目录和 root 外内容

#### Scenario: 关闭本地空目录回收
- **WHEN** `dinghai.file-storage.local.cleanup-empty-directories=false`
- **THEN** Local 仍按原语义删除对象和元数据，但不删除任何父目录
