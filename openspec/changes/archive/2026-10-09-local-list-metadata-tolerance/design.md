## Context

`LocalFileStorageAdapter.list()` 使用 `Files.walk(root)` 枚举本地对象文件，再根据相对路径读取对象元数据。当前实现将 `stat(key).orElseThrow()` 放在 Stream 的末端：对象文件存在但元数据缺失时，`stat` 返回空 Optional，`orElseThrow()` 抛出 `NoSuchElementException`，并中止整个惰性枚举。

本次修复必须保持 `FileStorage` 公共契约、对象 key 映射、文件过滤规则和其他 provider 不变，同时保证遍历资源在调用方关闭返回流时能够释放。元数据读取失败的原因只能以安全摘要进入 WARN 日志。

## Goals / Non-Goals

**Goals:**

- 将本地列表中的单对象元数据读取转换为可恢复的 `Optional<ObjectMetadata>` 结果。
- 缺失或读取失败的元数据只跳过当前对象，并继续处理其他普通文件。
- 记录对象路径和异常原因的 WARN，避免密码、Token、密钥及完整文件内容泄露。
- 以 try-with-resources 或返回 Stream 的 `onClose` 等价委托管理 `Files.walk()`，保留惰性消费和可关闭语义。
- 为正常对象、缺失元数据、损坏元数据、空目录和现有生命周期行为提供自动化回归覆盖。

**Non-Goals:**

- 不改变 `get`、`put`、`stat`、`delete`、`localFile`、multipart 或签名行为。
- 不修复或重建缺失/损坏元数据，不删除孤立对象文件。
- 不调整对象路径格式、存储目录结构、过滤规则、公共 API、Maven 坐标/版本或远程 provider 实现。

## Decisions

### 1. 在单对象边界捕获元数据读取异常

新增本地私有辅助逻辑，先确认对象 sidecar 存在，再读取并返回 `ObjectMetadata`；对缺失 sidecar、`IOException` 以及元数据解析可能抛出的运行时异常统一转换为空 Optional，并记录一次 WARN。`list()` 通过 `flatMap(Optional::stream)` 过滤坏对象，避免在 Stream 中使用 `orElseThrow()`。

相比在 `list()` 外层捕获异常，这种方案将失败隔离在单个对象，不会吞掉目录遍历本身的 I/O 故障，也不会错误地把整个列表当成成功结果。日志仅使用规范化对象 key 和异常类型/安全 message，不读取或输出对象内容及敏感配置。

### 2. 由返回 Stream 持有遍历资源

`Files.walk(root)` 创建后，构造经过 `onClose` 委托的返回 Stream；返回 Stream 关闭时关闭底层 walk stream。不能在 `list()` 方法返回前直接关闭 walk stream，否则会破坏惰性枚举。

### 3. 测试使用真实临时文件系统

回归测试使用现有 `TemporaryFolder` 和 `put()` 创建正常对象，再直接删除或覆盖对应元数据文件，验证 `list()` 的真实失败路径和继续枚举语义。测试同时使用 try-with-resources 消费列表，确保关闭约定保持不变；不修改其他 provider 测试。

## Risks / Trade-offs

- [坏元数据对象不再出现在列表中] → 这是本次容错的明确契约；保留对象文件，使用 WARN 暴露运维修复线索。
- [高数量孤立对象产生较多 WARN] → 每个异常对象最多记录一条 WARN，不在正常对象路径记录日志；不增加重试或额外扫描。
- [元数据解析异常类型可能超出 IOException] → 只在单对象辅助逻辑边界捕获可预期的读取/解析异常，并保持 `Files.walk()` 自身 IOException 继续映射为列表失败。

## Migration Plan

无需数据迁移和配置迁移。部署新版本后，已有孤立对象在 `list()` 中被跳过并记录 WARN；元数据完整的对象行为不变。若需回滚，可回退应用版本，存储目录结构无需改变。

## Open Questions

无。日志级别、字段范围和异常隔离策略已由需求确定。
