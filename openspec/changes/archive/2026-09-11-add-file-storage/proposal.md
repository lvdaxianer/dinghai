## Why

业务系统需要在本地磁盘、MinIO 与后续云对象存储之间切换，却不应让各供应商 SDK、对象模型和 URL 机制渗透到业务代码。当前仓库缺少统一的文件存储能力，无法以安全、一致且可配置的方式提供上传、下载、预览和受限访问。

## What Changes

- 新增独立的文件存储组件组：纯 Java 核心契约、本地磁盘适配器、MinIO 适配器和 Spring Boot Starter。
- 提供统一对象操作：覆盖写入、流式读取、元数据查询、存在判断和幂等删除；对象 key 支持 UTF-8 中文与多级相对路径。
- 提供统一预签名上传和下载 URL；MinIO 使用原生受限策略，本地磁盘通过 Starter 的验签端点实现相同业务契约。
- 提供可选的 `/files` HTTP 访问层：默认关闭直接下载/预览，开启后由宿主应用授权策略保护；预签名访问默认可用。
- 提供 HTTP/CDN 缓存语义（ETag、Last-Modified、Cache-Control、Expires、条件请求 304）和 `inline` 在线预览模式。
- 提供 fail-fast 配置、上传大小与内容类型限制、路径穿越防护、临时文件原子落盘和统一稳定异常分类。
- 新增 MinIO SDK 与测试基础设施的受控依赖；同步父 POM、双语 README、Agent Skill 与项目基线环境变量模板。

## Capabilities

### New Capabilities

- `file-storage`: 后端中立的对象存储契约、本地磁盘和 MinIO 适配器、对象 key 与上传安全约束。
- `file-storage-web-access`: 预签名 URL、可选 Spring Boot Web 端点、在线预览与 HTTP/CDN 缓存响应。

### Modified Capabilities

（无）

## Impact

- 代码：新增 `dinghai-file-storage-core`、`dinghai-file-storage-local`、`dinghai-file-storage-minio`、`dinghai-file-storage-spring-boot-starter` 四个 Maven 模块及相应测试。
- 构建：父 `pom.xml` 增加模块、版本与依赖管理；新增 MinIO Java SDK 与测试所需依赖。
- 配置：新增 `dinghai.file-storage.*`，包括本地根目录、MinIO endpoint/bucket/credentials、预签名密钥、上传限制与 Web 开关；敏感值只能来自环境或部署注入，不得提交真实值。
- HTTP：默认路径为 `/files`，可配置为业务路径；直接访问默认关闭，启用时宿主应用必须提供认证授权边界。
- 文档：README、README_EN 与 Agent Skill 同步新增模块说明、使用示例、配置和安全边界。
