## ADDED Requirements

### Requirement: 本地对象路径使用 UTF-8 字节语义
本地适配器 SHALL 将已验证的对象 key 以 UTF-8 字节解析为配置根目录内的本地路径，
不得依赖 `native.encoding` 或 `sun.jnu.encoding` 能否表示该 key。该规则 MUST 支持
中文目录、中文文件名和其他非 ASCII UTF-8 文件名的读取、写入、元数据查询与删除。

#### Scenario: 读取既有中文文件
- **WHEN** 根目录中已存在 UTF-8 字节名 `origin/uuid/板卡_PSR-663UG-DM-1-HMI.gltf`
- **THEN** 以相同的 Unicode 或 UTF-8 百分号编码 object key 读取时返回该文件内容

#### Scenario: 非 UTF-8 原生编码环境
- **WHEN** JDK 21 的 `native.encoding` 不能表示中文文件名
- **THEN** 本地适配器仍使用 UTF-8 对象 key 构造路径而不抛出不可映射字符异常

### Requirement: 本地 Unicode 路径保持安全边界
本地适配器 SHALL 在 UTF-8 路径构造后继续拒绝目录穿越，并拒绝对象路径或其既有父目录
通过符号链接逃离配置根目录。

#### Scenario: Unicode key 不弱化目录穿越校验
- **WHEN** 调用方提交包含 `..` 或 URL 编码后解析为 `..` 的 key
- **THEN** 适配器在任何文件系统读取或写入前拒绝该请求
