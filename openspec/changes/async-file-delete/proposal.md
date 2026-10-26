# Proposal

## Why

当前 `FileStorage.delete(ObjectKey)` 会在调用线程中同步等待本地文件系统或远端对象存储完成删除。大文件、远端超时或批量删除会占用业务请求线程，因此需要在不改变既有同步契约的前提下提供可选的、可恢复的异步删除任务。

## What Changes

- 新增可选的异步文件删除能力：先持久化删除任务，再由独立 worker 执行 provider 删除。
- 未配置异步删除或任务数据库时，保持现有同步删除行为，应用不得因缺少 JDBC 配置启动失败。
- 单机模式支持配置 SQLite 任务库；高可用模式复用宿主应用的关系型数据库 `DataSource`。
- 任务支持 `PENDING`、`RUNNING`、`RETRY_WAIT`、`SUCCESS` 和 `FAILED` 状态、租约接管、有限重试和安全幂等删除。
- 新增异步删除任务提交与状态查询的 Java 服务 API；不修改既有 `FileStorage.delete()` 的同步语义。
- 使用独立、有界的删除执行器，避免与 Web 读取执行器和其他业务线程池互相争抢资源。

## Capabilities

### New Capabilities

- `file-storage-async-delete`: 可选、持久化、可恢复且支持单机与高可用部署的异步文件删除任务。

### Modified Capabilities

无。既有 `FileStorage` 同步删除契约保持不变。

## Impact

- 影响 `dinghai-file-storage/spring-boot-starter` 的配置、自动装配、任务服务、任务持久化和 worker 实现。
- 增加 SQLite 单机任务库支持，并支持使用宿主提供的关系型数据库 `DataSource`。
- 增加异步删除任务相关的配置、状态模型、测试和中文/英文使用文档。
- 不改变 Local、MinIO、RustFS、阿里云 OSS、华为云 OBS 的底层 `FileStorage.delete()` 实现。
