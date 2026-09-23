## Why

文件存储 Web 端点目前将 `/files`、`/read/key` 等地址固定在控制器注解中，部署方无法在不改变业务调用方式的前提下迁移到既有的 `/rsm/...` URL 空间。历史对象地址需要继续通过统一的 `FileStorage` 读取链路提供完整的缓存、条件请求、Range 和并发限制语义。

## What Changes

- 使文件 Web 的前缀、上传、下载、预览、真实 key 读取及稳定 ID 读取路径可配置，并保持当前默认 URL 不变。
- 在应用启动期规范化并校验所有配置路径，拒绝非法路径和会产生歧义的路由组合。
- 按配置动态注册路由；可选的历史直读路由将捕获的多段 key 转为 `ObjectKey` 后复用现有统一读取流程。
- 增加 Local 与 MinIO 的路由行为测试，并补充中英文 README 的配置、兼容性和冲突规则说明。

## Capabilities

### New Capabilities

- `file-storage-configurable-web-routes`: 配置化、启动期校验和动态注册的文件 Web 路由，包含历史直读兼容。

### Modified Capabilities

- `file-storage-web-access`: 预签名上传、下载、预览与真实 key 读取端点改由可配置路径生成。
- `file-storage-key-read`: 真实 object key 的匿名读取路由可配置，并可选支持历史直读地址。
- `stable-file-access`: 稳定 ID 读取路由可配置且保持其读取语义。

## Impact

- 影响 `dinghai-file-storage/spring-boot-starter` 的属性绑定、路由注册、Web 控制器、自动配置及其测试。
- 不改变 `FileStorage` 核心接口、provider API 或对象物理定位；Local 与 MinIO 均继续通过同一 `FileStorage` 链路读取。
- 影响 `dinghai-file-storage` 中英文 README 的 Web 路由文档。
