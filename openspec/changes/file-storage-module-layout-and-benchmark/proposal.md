# Proposal: 文件存储模块重组与并发压测

文件存储的 Core、Local、MinIO 与 Starter 模块同属一个能力域，但当前位于仓库根目录，无法在 IDE 中形成清晰的聚合。同时缺少可使用真实文件复现的并发压测工具，无法对本地磁盘和 MinIO 的上传、读取及 MinIO 缓存读取进行对比。

## Why

- 让文件存储的四个 Maven 模块在仓库与 IDE 中形成一个明确的逻辑边界。
- 提供不依赖业务应用的真实文件压测工具，以实际吞吐、延迟、错误率和内容校验确认适配器表现。

## What Changes

- 将四个文件存储模块移动至 `dinghai-file-storage/{core,local,minio,spring-boot-starter}`。
- 保持 artifactId、Java package 和公开 API 不变，仅调整 Maven reactor 的相对路径。
- 新增仅测试范围的 benchmark 模块，支持 Local 与 MinIO 的并发上传/读取，及 MinIO 缓存冷读/热读。
- benchmark 从命令行参数读取文件和并发度，从环境变量读取 MinIO 凭据；不提交实际文件、密钥或运行报告。
