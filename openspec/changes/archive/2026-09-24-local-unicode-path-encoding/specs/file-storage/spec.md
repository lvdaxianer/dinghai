## MODIFIED Requirements

### Requirement: 本地磁盘适配器安全持久化
本地适配器 SHALL 仅在配置的根目录下读写对象。Starter 使用公共
`dinghai.file-storage.bucket` 将适配器根目录映射为 `local.root / bucket`；它 MUST 将
规范化 key 以 UTF-8 文件系统字节解析后再次检查目标路径仍位于该 bucket 根目录，且
不得依赖运行时的 `native.encoding` 或 `sun.jnu.encoding` 表示该 key。写入 MUST 通过
同文件系统临时文件完成，校验完成后原子替换目标；需要时 MUST 创建父目录。元数据
持久化失败 MUST 使写入报告失败，读取缺失元数据的既有内容时 MUST 使用私有不可缓存的
安全默认值。

#### Scenario: 非 ASCII 本地对象 key
- **WHEN** 配置根目录中包含 UTF-8 字节名 `中文目录/板卡.gltf` 且 JDK 本机编码无法表示中文
- **THEN** 本地适配器以同一 Unicode 或 UTF-8 百分号编码 key 读取、写入和查询该对象
