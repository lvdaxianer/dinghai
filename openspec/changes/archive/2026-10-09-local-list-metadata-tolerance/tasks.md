## 1. 本地列表容错实现与测试

- [x] 1.1 **完成本地列表元数据容错**：任务边界仅允许修改 `dinghai-file-storage/local/src/main/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalFileStorageAdapter.java`、`dinghai-file-storage/local/src/test/java/io/github/lvdaxianer/dinghai/filestorage/local/LocalFileStorageAdapterTest.java` 以及本 change 的 OpenSpec/设计资产；不得修改 core、MinIO、OSS、RustFS、Starter、路径/签名/数据库逻辑。依赖为 Java 21、现有 JUnit 4 和本地元数据布局。由于实现和测试共享两个文件且任务为单一局部修复，不并行派发实现代理，主代理直接执行以避免共享文件冲突并保持 TDD 闭环。
  - RED：新增真实临时文件系统测试，覆盖元数据完整、缺失元数据、损坏元数据、空目录和可关闭列表流；确认旧实现分别出现错误/错误断言。
  - GREEN：将单对象元数据读取转换为安全 `Optional`，使用 `flatMap(Optional::stream)` 过滤坏对象；对缺失/损坏/读取失败记录一次包含对象路径和安全异常原因的 WARN，不输出密码、Token、密钥或完整文件内容；使用返回 Stream 的 `onClose` 关闭 `Files.walk()`。
  - 验证：聚焦命令为 `mvn -pl dinghai-file-storage/local -am -Dtest=LocalFileStorageAdapterTest -DfailIfNoTests=false test`；模块命令为 `mvn -pl dinghai-file-storage/local -am test`；全量命令为 `mvn test`；构建命令为 `mvn clean install`。
  - 审计：检查 diff 范围、OpenSpec 覆盖、`code-review-spec`、`logging-boundaries` 和 backend reliability 要求；用 Maven 编译与 `javap -public` 检查公共 API/二进制兼容性，确认 Maven 坐标 `io.github.lvdaxianer:dinghai-file-storage-local:1.0.0` 未变化；执行 `git ls-files` 密钥/Token/构建产物检查。
