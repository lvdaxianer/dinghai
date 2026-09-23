# file-storage-configurable-web-routes Specification

## Purpose

提供启动期校验、规范化的文件 Web 路由配置，以及可选的历史真实 key 直读兼容地址。

## Requirements

### Requirement: 可配置文件 Web 路由
Starter SHALL 在 `dinghai.file-storage.web` 提供 `path-prefix`、`upload-path`、`download-path`、`preview-path`、`read-path`、`stable-read-path`、`legacy-read-enabled` 与 `legacy-read-path` 配置。默认值 MUST 分别保持 `/files`、`/upload`、`/download`、`/preview`、`/read/key`、`/read`、`false` 与 `/` 的既有行为。所有路径值 MUST 在应用启动期间规范化：允许裸路径、前导 `/`、空字符串与 `/`；生成的路由不得含重复斜杠；包含 `?`、`#`、反斜杠、回车或换行的值 MUST 以 `INVALID_CONFIGURATION` 使启动失败。

#### Scenario: 裸路径被规范化
- **WHEN** `path-prefix` 配置为 `rsm` 且 `read-path` 配置为 `read`
- **THEN** Starter 注册 `/rsm/read/{*key}`，且不会注册含 `//` 的映射

#### Scenario: 根与空片段可组合
- **WHEN** 路径前缀或子路径配置为 `""` 或 `/`
- **THEN** Starter 注册的映射含单个分隔斜杠，并保持有效的 Spring MVC 路径模式

#### Scenario: 非法路径启动失败
- **WHEN** 任一路径配置包含 `?`、`#`、反斜杠、回车或换行
- **THEN** 属性校验以 `INVALID_CONFIGURATION` 拒绝应用启动，且不等待 HTTP 请求

### Requirement: 路由冲突在启动期拒绝
Starter MUST 在注册前拒绝彼此相同的上传、下载、预览与真实 key 读取路径。稳定 ID 路径与真实 key 读取路径相同或会产生相同路由模式时 MUST 以 `INVALID_CONFIGURATION` 失败。启用根历史路由时，Starter MUST 保留更具体的已配置读取、上传、下载和预览路由优先匹配，不得使默认路由失效。

#### Scenario: 读取与预览路径冲突
- **WHEN** `read-path` 与 `preview-path` 配置成同一规范化路径
- **THEN** 应用在启动期返回 `INVALID_CONFIGURATION`

#### Scenario: 根历史路由不覆盖显式读取
- **WHEN** `path-prefix=/rsm`、`read-path=/read`、`legacy-read-enabled=true` 且 `legacy-read-path=/`
- **THEN** `/rsm/read/model/a.gltf` 匹配真实 key 读取路由，而不是将 `read/model/a.gltf` 作为历史 key

### Requirement: 历史直读路由复用统一读取流程
当 `legacy-read-enabled=true` 时，Starter SHALL 额外注册 `{path-prefix}{legacy-read-path}/{*key}`。该路由 MUST 将捕获的多段路径解码并转换为 `ObjectKey`，然后调用现有 `FileStorage` 对象读取流程；MUST NOT 直接读取本地文件或依赖 provider 专属客户端。该路由 MUST 保持 inline 响应、Range、ETag、Last-Modified、条件请求、读取并发限制、provider 缓存和统一异常映射语义。

#### Scenario: 历史地址读取多级对象
- **WHEN** `/rsm/model/123/a.gltf` 请求命中 `path-prefix=/rsm`、`legacy-read-path=/` 的历史路由
- **THEN** 路由以 `ObjectKey.of("model/123/a.gltf")` 通过 `FileStorage` 返回对象

#### Scenario: 历史地址保留编码文件名
- **WHEN** 历史路由请求包含多级中文目录、空格或点号文件名的 URL 编码 key
- **THEN** 路由以相同的规范化 `ObjectKey` 读取对象，并保持现有条件与 Range 响应语义

#### Scenario: 禁用历史路由
- **WHEN** `legacy-read-enabled=false`
- **THEN** Starter 不注册 `{path-prefix}{legacy-read-path}/{*key}`，且该地址不会作为对象读取处理
