# 本地列表元数据容错设计

## 背景

`LocalFileStorageAdapter.list()` 通过 `Files.walk()` 惰性枚举对象文件，并为每个对象读取 sidecar 元数据。元数据缺失或损坏时，单个对象不应阻断整个列表。

## 设计

- 仅在本地适配器的列表路径增加单对象安全读取辅助方法。
- sidecar 缺失、I/O 失败或 Properties 解析失败时返回 `Optional.empty()`，由 `flatMap(Optional::stream)` 跳过当前对象。
- 每个异常对象记录一条 WARN，包含规范化对象路径和限长异常原因，不输出文件内容、密码、Token 或密钥。
- 通过返回 Stream 的 `onClose` 委托关闭 `Files.walk()` 资源，等价满足 try-with-resources 的资源释放要求，并保留现有惰性和可关闭契约。
- 用真实临时目录测试正常对象、缺失 sidecar、损坏 sidecar 和空目录；不修改其他 provider 或公共 API。

## 验证

先以缺陷回归测试确认旧实现失败，再验证本地模块、全量测试、clean install、公共 API 签名和二进制兼容性。
