## Context

当前本地适配器的 `delete(ObjectKey)` 删除对象文件和 sidecar 元数据，但不处理对象
父目录。当前公共 `FileStorage` 接口没有 `deletePrefix`，所以本 change 的前缀删除
能力只属于 `LocalFileStorageAdapter`，不能要求远端 provider 改变行为。

本地 root 在构造时已规范化为真实路径，`ObjectKey` 已拒绝绝对路径、`..`、反斜杠和
控制字符，并支持 URL 百分号编码。新增清理逻辑必须延续这些约束，还要处理并发删除、
中文路径和符号链接竞争。

## Goals / Non-Goals

**Goals:**

- 单对象删除成功后逐层清理 root 内空父目录。
- 前缀删除对象和元数据后按深度倒序清理空目录。
- 默认开启并可通过 `local.cleanup-empty-directories=false` 关闭目录清理。
- 在 root 内、拒绝路径穿越、不跟随符号链接、支持并发幂等删除。
- 以汇总结构化日志记录删除统计和耗时。

**Non-Goals:**

- 不修改公共 `FileStorage` 契约。
- 不实现远端 provider 的前缀删除或目录语义。
- 不删除非空目录、root 本身、符号链接或 root 外文件。
- 不修改 `qinghai-server` 或通过业务层执行递归删除。

## Decisions

### 本地专用 API

新增 `LocalFileStorageAdapter.deletePrefix(String)`，避免扩展所有 provider 的公共接口。
前缀先经 URL 解码、Unicode 规范化和对象 key 安全校验，再解析为 root 内路径。

### 删除顺序与并发

对象文件和 sidecar 先删除；对象删除失败会被记录并使操作失败，但继续处理其他对象。
对象处理完成后遍历目录并按深度倒序调用 `Files.deleteIfExists`。不存在、并发已删除的
文件或目录按成功处理；非空目录停止向上清理；其他目录异常仅告警并计数，不回滚已完成
的对象删除。

### 路径安全

所有候选路径使用 `normalize()` 并检查 `startsWith(root)`，且禁止候选路径等于 root。
文件、目录和扫描均使用 `NOFOLLOW_LINKS` 或不启用 `FOLLOW_LINKS`。前缀目标和已有父级
路径通过真实路径检查，避免中间符号链接指向 root 外部。

### 配置兼容性

在 `FileStorageProperties.Local` 增加默认值为 `true` 的布尔属性。已有适配器构造器
保留并委托到新构造器，确保直接 new 适配器的调用方默认获得目录回收行为。

### 日志与错误

每次前缀删除结束输出一条汇总 INFO，包含 prefix、删除文件数、删除目录数、跳过目录数、
失败数和 durationMs。对象或目录失败输出 WARN，采用限长安全标识和异常类型，不输出
密钥、完整配置、文件内容或无界用户数据。目录失败不改变已完成对象删除的成功事实；
对象失败通过稳定存储异常向调用方报告未完成。

## Risks / Trade-offs

- [并发遍历时目录项消失] → 将 `NoSuchFileException` 视为幂等成功，并在最终汇总中保留失败计数。
- [清理过程遇到新写入文件] → 只调用空目录删除，非空目录自然保留，不会删除新对象。
- [符号链接竞态替换目录] → 不跟随符号链接，删除前再次检查目录属性和 root 边界。
- [前缀对象数量很大] → 使用惰性 `Files.walk`，不把全部文件内容加载到内存；统计只保存计数。

## Migration Plan

发布包含该修复的 Dinghai starter 后无需修改 `qinghai-server` 业务代码。默认开启空目录
清理；如需暂时保持旧目录行为，设置
`dinghai.file-storage.local.cleanup-empty-directories=false`。回滚 starter 即可恢复旧
行为，不涉及数据格式或迁移。

## Open Questions

无。当前仓库不存在既有 `deletePrefix` 返回值，因此本地专用方法不改变公共契约；单对象
`delete` 的既有布尔返回语义保持不变。
