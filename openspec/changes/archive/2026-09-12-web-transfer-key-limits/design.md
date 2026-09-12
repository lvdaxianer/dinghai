## Context

当前 `WebTransferLimiter` 只有全局上传和读取许可池。控制器已将读取许可覆盖流式、缓存文件与 Tomcat `sendfile`，因此新增公平限制必须复用同一许可生命周期，不能改变零拷贝传输选择。

## Goals / Non-Goals

**Goals:**
- 允许宿主按自身认证模型返回一个限流 key。
- 保留全局上传/读取上限，再对返回的非空 key 叠加独立并发上限。
- 对未配置、空白或异常 resolver 保持现有可用性，仅使用全局保护。
- 在全部结束路径回收无活跃许可的 key 状态。

**Non-Goals:**
- 不在组件内解析或信任 `X-Forwarded-For`、Cookie、JWT 或 API Key。
- 不实现分布式限流、速率窗口、持久化计数器或限流指标端点。
- 不改变 SDK 调用、上传凭证和匿名 Web 读取边界。

## Decisions

1. **公共 resolver 接口接收 `HttpServletRequest` 并返回 `String`。** 宿主最了解用户、租户和可信代理上下文；直接传请求避免组件定义错误的身份抽象。接口不承诺 key 的格式或稳定性。
2. **增加 `web.max-concurrent-transfers-per-key`，默认 0 表示关闭。** 正数启用每 key 上限，并同时用于上传和读取；上传/读取仍使用独立许可池，因此同一 key 的读写互不占用额度。单一统一上限减少配置表面积。
3. **先全局、后 key 获取；key 获取失败时回滚全局许可。** 保持既有全局保护优先，防止 key 达上限时泄漏全局许可。返回的组合 Permit 负责反向释放。
4. **异常或空白 resolver 结果降级。** 解析失败视为没有 key，不记录 key 或异常内容，避免身份解析问题导致文件服务不可用或泄露敏感信息。
5. **key 槽位按活跃数引用计数并在归零时移除。** 每次获取成功后增加活跃数；释放时减少，并使用 `ConcurrentHashMap.compute` 在归零时删除槽位。避免无限 key 留存，也避免并发释放错误删除新建槽位。

## Risks / Trade-offs

- [恶意调用方生成大量 key] → 仅为已获得许可的活跃 key 保留槽位，释放后立即回收。
- [key resolver 延迟或异常] → 由宿主保证轻量、无阻塞；组件异常时降级且不记录敏感 key。
- [先拿全局再拒绝 key] → 组合 Permit 在拒绝时立即释放全局许可，无请求体或对象流被读取。
- [sendfile 没有应用层复制回调] → 继续使用现有 request attribute 与 MVC `afterCompletion` 释放组合 Permit。

## Migration Plan

1. 升级后默认 `web.max-concurrent-transfers-per-key=0`，行为与当前版本一致。
2. 宿主注册 resolver Bean，并设置正数每 key 上限后启用公平隔离。
3. 回滚时移除该配置或设为 0，并删除 resolver Bean；全局上限继续有效。

## Open Questions

无；本 change 固定采用单一每 key 上限、解析失败降级和非持久化本地许可表。
