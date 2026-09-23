## Context

当前 `FileStorageWebController` 使用固定 `@RequestMapping` 和 `@GetMapping` 路径，`FileStorageProperties.Web` 只有前缀。配置化子路径与根历史路由会造成 Spring 注解占位符的空片段、重复斜杠及通配映射优先级难以可靠校验。现有 Controller 已集中处理 `ObjectKey`、稳定 ID、缓存、Range、ETag、并发许可和异常映射，不能复制或绕过。

## Goals / Non-Goals

**Goals:**
- 以启动期验证的路径组合动态注册所有文件 Web 路由。
- 保持默认 URL、稳定 ID、预签名、Local/MinIO 读取和统一响应流程兼容。
- 允许可选历史直读地址映射任意多段 `ObjectKey`。

**Non-Goals:**
- 不改变 `FileStorage`、provider 接口或对象 key 校验。
- 不改变迁移管理和 multipart 端点的既有契约。
- 不将 bucket、物理文件系统路径或 provider 客户端暴露到 Web 层。

## Decisions

1. **集中路径模型与启动期校验。** 为 `Web` 增加规范化路径方法和冲突校验，产出唯一完整模式。属性校验使用既有 `FileStorageException(INVALID_CONFIGURATION)` 语义。选择此方案而非 Controller 占位符，因为它能够对空路径、根路径和等价组合在请求前失败。
2. **动态注册到一个业务 Controller。** Controller 的现有 HTTP 处理方法保留为普通可调用方法；自动配置注册映射并指定对应方法。选择动态注册而非多 Controller，是为了让历史路径与标准 key 路径共享同一读取方法、读取 permit、缓存与异常映射。
3. **显式路由先于历史 catch-all。** 对开启 legacy 的情况，在映射注册和冲突检测中保留 upload/download/preview/read/stable 的确定性模式，根 catch-all 仅作为回退。选择 Spring 的具体模式优先级而非在 Controller 手工分派，以免历史 key 拦截已配置操作路径。
4. **预览和下载语义不变。** 标准读取、上传/下载/预览和 legacy 都调用既有 `FileStorage` 访问路径；仅路由解析不同。

## Risks / Trade-offs

- [动态映射可能与已有 MVC 映射重复] → 启动期校验配置路径，测试同时断言默认、自定义和根 legacy 映射。
- [根 catch-all 与业务对象名发生歧义] → 明确启用才注册，显式操作路由由更具体模式优先；不可消除的同模式配置在启动期拒绝。
- [URL 编码 key 解析差异] → 复用当前 path-variable 到 `ObjectKey` 的转换，并对中文、空格、点号和多段 key 做 MockMvc 覆盖。

## Migration Plan

1. 部署方先使用默认配置验证现有 `/files/**` 地址不变。
2. 配置 `path-prefix=/rsm` 和需要的子路径后，应用在启动期注册新地址。
3. 迁移期间启用 `legacy-read-enabled=true` 与 `legacy-read-path=/`，使既有 `/rsm/{*key}` 直接通过 `FileStorage` 继续读取。
4. 如需回滚，删除新增 Web 子路径配置并禁用 legacy；对象和 provider 无需迁移。

## Open Questions

无；用户已指定默认值、冲突策略与兼容性边界。
