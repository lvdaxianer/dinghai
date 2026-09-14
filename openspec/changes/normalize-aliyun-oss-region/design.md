## Context

阿里云 OSS V2 Java SDK 的 builder 会基于 region 生成 endpoint。当前实现把用户提供的 `oss-cn-beijing` 原样传入，SDK 因而生成 `oss-oss-cn-beijing.aliyuncs.com`。现有文档和用户配置已经约定使用控制台地域 ID，不应要求调用方改写配置。

## Goals / Non-Goals

**Goals:**

- 在 SDK 边界将 `oss-<region>` 映射为 `<region>`。
- 继续允许已经是 SDK 地域格式的值，避免重复转换。
- 通过测试证明 `oss-cn-beijing` 生成标准 `cn-beijing` SDK 配置。

**Non-Goals:**

- 不修改 endpoint、bucket、凭证或超时配置契约。
- 不支持华为云、STS 或自定义 OSS endpoint。
- 不改变已发布的 `aliyun-oss` provider 名称。

## Decisions

- 采用客户端工厂内的纯函数规范化：仅移除一个开头的 `oss-`，再传给 SDK。这样保留配置层的阿里云地域 ID，同时避免在适配器和 Spring 配置中重复处理。
- 不通过字符串替换 endpoint 或新增 endpoint 配置：SDK 自己负责 endpoint 规则，手工拼接容易造成双重域名或区域错误。
- 同时覆盖带前缀和不带前缀输入：前者对应用户文档，后者保证底层 SDK 格式可直接使用。
- 首次 V2 ListObjects 请求省略 continuation token：V2 builder 不接受 `null` 值；仅在服务端提供下一页 token 后设置该字段。

## Risks / Trade-offs

- [Risk] 其他阿里云 region 命名规则可能存在例外 → 仅移除明确的 `oss-` 前缀，不对其余内容做猜测，并以真实 bucket 验收覆盖当前配置。
- [Risk] 错误输入仍可能在首次远程调用时暴露 → 保持现有非空校验，并由 SDK/统一异常映射处理远端失败。
- [Risk] 首次列举请求向 SDK 传递 `null` token 会触发本地空指针异常 → 通过捕获请求的单元测试和真实生命周期列举验证首请求省略该字段。

## Migration Plan

无需数据迁移。部署后继续使用 `aliyun-oss.region=oss-cn-beijing`；升级后重新运行真实生命周期测试确认 endpoint 和权限。

## Open Questions

无。
