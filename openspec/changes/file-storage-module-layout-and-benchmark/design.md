# Design

## 模块布局

目标目录为：

```text
dinghai-file-storage/
  core/
  local/
  minio/
  spring-boot-starter/
  benchmark/
```

父 POM 的 `<modules>` 使用上述相对路径。artifactId 不变，因此仓库外的 Maven 使用方和模块间依赖不受影响。

## Benchmark 契约

压测入口为 `FileStorageBenchmark`，参数包含 provider、文件、并发度、读取轮数和本地根目录。MinIO 连接从 `DINGHAI_FILE_STORAGE_MINIO_ENDPOINT`、`...ACCESS_KEY`、`...SECRET_KEY`、`...BUCKET` 读取，缺失即快速失败且不输出敏感值。

每个并发工作者使用独立 key 上传同一源文件，随后对每个对象执行配置轮次的流式读取并校验 SHA-256。输出仅包含文件大小、并发度、操作数、吞吐、平均/P95/P99 延迟、错误数和缓存状态。所有对象 key 以一次运行 ID 隔离，结束后删除；失败也会执行尽力清理。

MinIO cache 场景通过 `ProviderFileReadCache` 执行：冷读先填充，然后等待填充完成，再测热读。缓存目录位于临时目录，运行结束删除。

## Safety

- 不读取或输出 MinIO 密钥。
- 源文件只读；Local benchmark 输出写入系统临时目录。
- 并发度和读取轮数必须为正数，并限制为合理上限。
- 单次读取使用固定大小缓冲区，不将完整文件放入 JVM 堆。

## Validation

- Maven reactor 在重组后执行完整测试。
- benchmark 参数和 Local 并发流程单元测试。
- 用 `~/Downloads/982-合0820.FBX` 执行 Local 并发实测。
- 用 `~/Downloads/doc.zip` 执行 MinIO 并发实测；运行凭据仅由 Docker 容器环境注入。
