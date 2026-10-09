## Why

本地存储目录中的对象文件与元数据文件可能因人工清理、磁盘异常或历史数据不完整而失配。当前 `LocalFileStorageAdapter.list()` 对单个对象直接调用 `Optional.orElseThrow()`，导致一个坏对象使整个对象枚举失败，阻断迁移、清理等批量流程。

## What Changes

- 使本地对象枚举对缺失、损坏或读取失败的单个元数据容错：跳过该对象并继续返回其他有效对象。
- 为被跳过的对象记录包含对象路径和安全异常原因的 WARN 日志，不输出密钥、Token、密码或完整文件内容。
- 使用可正确关闭的文件遍历流，保持现有对象路径、过滤规则、公共 API 和其他存储适配器行为不变。
- 增加本地适配器回归测试，覆盖正常枚举、缺失元数据、损坏元数据、空目录和既有生命周期行为。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `file-storage`: 明确本地适配器的惰性对象枚举在单个对象元数据缺失或不可读时跳过该对象而不使整个列表失败。

## Impact

- 代码：`dinghai-file-storage/local` 模块中的 `LocalFileStorageAdapter`。
- 测试：`LocalFileStorageAdapterTest` 增加本地文件与元数据失配场景。
- 运行时：仅改变本地适配器 `list()` 对坏对象的容错和 WARN 可观测性；不改变 `get`、`put`、`delete`、`localFile`、multipart、对象路径格式、签名规则或 MinIO/OSS/RustFS 实现。
- 依赖/API：不新增依赖，不改变公共方法签名、Maven 坐标或版本。
