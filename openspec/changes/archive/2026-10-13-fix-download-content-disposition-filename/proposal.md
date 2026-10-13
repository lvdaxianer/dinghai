## Why

文件存储 Web 响应当前把 `Content-Disposition` 的文件名固定为 `file`，导致
`/rsm/**` 历史直读和普通下载地址无法保留对象的真实文件名。需要在不改变
现有读取安全、缓存和流式传输契约的前提下修复浏览器下载体验，并避免暴露
完整 object key 的目录层级。

## What Changes

- 从已校验的 object key 提取最后一个路径段作为响应文件名。
- 使用 Spring 的 RFC 兼容 `ContentDisposition` 生成 inline 和 attachment 响应头。
- 支持 UTF-8 中文文件名，并覆盖多级 key、路径穿越输入和两种响应模式的回归测试。
- 保持现有 Content-Type、ETag、Last-Modified、Cache-Control、Range、权限和流式传输逻辑不变。
- 不修改业务 object key；Dinghai starter 作为独立依赖发布修复版本。

## Capabilities

### New Capabilities

### Modified Capabilities

- `file-storage-web-access`: Web 对象读取的 Content-Disposition 文件名应使用 object key 的最后一段，并兼容 UTF-8 文件名。

## Impact

- 修改 `dinghai-file-storage/spring-boot-starter` 的 `FileStorageWebController`。
- 扩展 `FileStorageWebControllerTest` 的 legacy、下载、预览和安全回归覆盖。
- Dinghai 发布新版本后，外部 qinghai-server 需要升级 `dinghai-file-storage-spring-boot-starter` 依赖；本仓库不包含 qinghai-server 源码，无法在此直接修改其依赖或执行其集成编译。
