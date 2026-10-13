## Context

`FileStorageWebController` 已经在读取对象前将路径参数转换为经过 `ObjectKey`
校验的对象 key，并通过 `ObjectMetadata` 携带该 key。当前统一响应头方法仍把
`Content-Disposition` 文件名固定为 `file`，影响预览、下载、legacy 直读和稳定
读取等所有响应模式。该修复只调整响应头中的文件名，不改变对象读取、授权、缓存、
Range 或流式传输链路。

## Goals / Non-Goals

**Goals:**

- 从已校验 object key 提取最后一个 `/` 后的路径段。
- 让 inline 和 attachment 都使用该 basename。
- 通过 Spring `ContentDisposition` 生成兼容 ASCII 和 UTF-8 的 RFC 响应头。
- 保证文件名不包含完整目录路径，且路径穿越仍在 `ObjectKey` 校验阶段被拒绝。
- 用 MVC 回归测试验证 legacy `/rsm/**`、普通 inline、attachment、中文文件名和非法 key。

**Non-Goals:**

- 不修改业务 object key、存储布局或 qinghai-server 路由配置。
- 不改变 Content-Type、ETag、Last-Modified、Cache-Control、Expires、Range、权限、并发限制或传输方式。
- 不在本仓库内发布 Maven 制品或修改 qinghai-server 依赖；这些属于外部集成步骤。

## Decisions

1. **使用元数据中的 `ObjectKey` 提取文件名。** `ObjectMetadata.key()` 已来自同一
   读取链路并由 `ObjectKey` 规范化，因此不会把未校验的原始路径用于响应头。使用
   `lastIndexOf('/')` 提取 basename，避免 `java.nio.file.Path` 受宿主操作系统路径
   语义影响。
2. **使用 Spring `ContentDisposition`.** 以响应模式作为 disposition type，并调用
   `filename(name, StandardCharsets.UTF_8)`，让 Spring 同时生成兼容的 ASCII
   `filename` 和 RFC 5987/6266 的 UTF-8 参数。手工拼接 header 会在中文、引号或
   特殊字符场景下产生编码和转义风险。
3. **在现有 `responseHeaders` 中局部替换。** 继续由 `readObject` 在获取元数据后
   统一创建响应头，所有读取入口自然复用同一行为，避免分别修改 preview、download、
   legacy 和稳定 ID 路由。
4. **以现有 MVC 测试作为回归边界。** 测试通过真实 Local provider 和 MockMvc
   触发异步响应，检查最终 header；路径穿越请求必须在调用 provider 前返回稳定
   参数错误或 4xx，并保持既有授权/读取链路。

## Risks / Trade-offs

- [文件名包含特殊字符] → 使用 Spring ContentDisposition 的编码和转义逻辑，测试
  中文文件名并断言 header 不包含完整 object key。
- [未来元数据 key 与请求 key 不一致] → 只信任 provider 返回的已校验
  `ObjectMetadata.key()`，不从原始 URI 字符串截取。
- [外部 qinghai-server 仍使用旧 starter] → Dinghai 修复完成后发布新版本，集成方
  升级依赖；当前仓库明确记录无法直接执行该外部编译。

## Migration Plan

1. 在 Dinghai starter 中完成测试先行的 header 修复并运行模块测试及 Maven 编译。
2. 发布包含修复的 Dinghai starter 新版本（当前基线为 `1.0.0`，版本号由发布流程
   决定）。
3. qinghai-server 升级 `dinghai-file-storage-spring-boot-starter` 到新版本，并用
   `/rsm/model-version/.../index.gltf` 做集成验证。
4. 回滚时将 qinghai-server 依赖恢复到上一版本；Dinghai 修复不涉及数据迁移。

## Open Questions

- qinghai-server 的实际构建仓库和目标依赖版本尚未提供，因此本次只能完成 Dinghai
  starter 的源码、测试和本仓库编译验证。
