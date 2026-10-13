## 1. 设计与测试先行

- [x] 1.1 增加本地删除空目录回收、前缀删除、路径安全和配置开关的失败测试
- [x] 1.2 运行本地测试确认新增测试在实现前按预期失败

## 2. 本地实现

- [x] 2.1 在 `LocalObjectPathResolver` 中补充前缀路径规范化和 root/符号链接边界校验
- [x] 2.2 在 `LocalFileStorageAdapter.delete(ObjectKey)` 中加入元数据幂等删除和可配置父目录回收
- [x] 2.3 在 `LocalFileStorageAdapter` 中实现本地专用 `deletePrefix`、深度优先目录回收、并发幂等和结构化汇总日志
- [x] 2.4 在 `FileStorageProperties` 与自动装配中增加 `local.cleanup-empty-directories`，默认开启且不影响远端 provider

## 3. 验证与文档

- [x] 3.1 运行本地模块测试并完成 RED/GREEN/REFACTOR 证据
- [x] 3.2 运行文件存储完整 reactor 测试，确认 MinIO/RustFS/OSS/OBS 回归通过
- [x] 3.3 执行 OpenSpec 验证、代码审查和需求逐项审计，更新规格并完成发布说明
