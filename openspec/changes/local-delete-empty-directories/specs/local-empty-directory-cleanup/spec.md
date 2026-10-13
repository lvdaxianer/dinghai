## ADDED Requirements

### Requirement: 本地删除后的空目录回收
本地适配器 SHALL 在 `delete(ObjectKey)` 成功删除对象文件及其元数据后，从对象父目录
开始向上删除空目录；清理 SHALL 只发生在配置的本地存储 root 内，且 MUST NOT 删除 root
本身或包含其他目录项的目录。目录不存在时 SHALL 按幂等成功处理。

#### Scenario: 删除单个文件后清理空目录
- **WHEN** 对象位于 `model-version/123/V0/model.glb` 且删除对象文件和元数据成功
- **THEN** `V0`、`123` 和 `model-version` 中为空的目录按深度向上删除，存储 root 保留

#### Scenario: 父目录仍有其他文件时保留目录
- **WHEN** 删除一个对象后其父目录仍包含其他对象或目录项
- **THEN** 该父目录及其上级目录不因本次删除被误删

#### Scenario: 关闭空目录回收
- **WHEN** `dinghai.file-storage.local.cleanup-empty-directories=false`
- **THEN** 对象文件和元数据仍按原语义删除，但任何父目录都不被清理

### Requirement: 本地前缀删除
本地适配器 SHALL 提供本地专用的 `deletePrefix` 操作，删除规范化前缀下的所有普通对象
文件和对应元数据，并在对象处理完成后按最深目录优先回收空目录。该操作 MUST NOT 改变
远端 provider 的行为。

#### Scenario: 删除前缀后清理多级空目录
- **WHEN** `deletePrefix("model-version/123/V0")` 匹配多个模型资源并删除成功
- **THEN** 匹配对象、元数据和产生的多级空目录被删除，存储 root 保留

#### Scenario: 重复删除不存在的前缀
- **WHEN** 对不存在的 prefix 连续执行 `deletePrefix`
- **THEN** 每次操作均按幂等成功处理且不抛出不存在错误

#### Scenario: 并发删除同一前缀
- **WHEN** 多个线程同时删除同一个 prefix
- **THEN** 已被其他线程删除的对象或目录按成功处理，不因竞态抛出不存在错误

### Requirement: 本地删除路径安全边界
本地删除 SHALL 在规范化后验证目标仍位于 root 之下，拒绝空前缀、绝对路径、`..`、反斜杠、
控制字符和 root 外路径，并且 MUST NOT 跟随符号链接删除 root 外部内容。

#### Scenario: 拒绝路径穿越前缀
- **WHEN** prefix 包含 `../`、绝对路径或规范化后越出 root
- **THEN** 操作以稳定的非法对象 key 错误拒绝，root 外内容保持不变

#### Scenario: 符号链接不能越界删除
- **WHEN** root 内存在指向 root 外部目录的符号链接且删除前缀命中该路径
- **THEN** 操作不跟随该链接，不删除 root 外部文件或目录

#### Scenario: 中文和 URL 编码路径
- **WHEN** 对象 key 包含中文目录，或 prefix 使用对应的 URL 百分号编码
- **THEN** 操作按同一规范化对象路径删除对象并清理 root 内空目录

### Requirement: 本地删除可观测性与失败语义
本地前缀删除 SHALL 输出包含 `prefix`、`deletedFiles`、`deletedDirectories`、
`skippedDirectories`、`failedDeletions` 和 `durationMs` 的结构化汇总日志。对象删除失败
MUST 保持失败语义并记录明确告警；单个目录删除失败 MUST NOT 影响已完成的对象删除，
但 MUST 记录告警。日志 MUST NOT 输出密钥、完整敏感配置、文件内容或无界用户数据。

#### Scenario: 目录删除失败不回滚对象删除
- **WHEN** 对象文件删除成功但某个空目录删除因 IO 错误失败
- **THEN** 对象删除结果保持已完成，失败目录被计数并以 WARN 记录

#### Scenario: 对象删除失败不误报成功
- **WHEN** 匹配对象的文件删除失败
- **THEN** 该对象计入失败并向调用方报告前缀删除未完全成功，同时继续处理其他对象
