## Context

`MinioClient#getPresignedPostFormData` 返回 SigV4 签名字段，但不会回填由
`PostPolicy` 声明的业务表单字段。当前适配器直接转发该 Map；真实 MinIO
因此无法满足 bucket、key、内容类型和缓存策略的等值条件。

## Goals / Non-Goals

**Goals:**

- 返回包含全部 policy 必填字段和 SDK 签名字段的不可变 POST 表单。
- 保持固定 bucket、对象 key、类型、缓存策略、长度范围和过期时间的原有
  policy 约束。
- 用 unit regression 和真实 Docker MinIO 验证 POST 上传可用。

**Non-Goals:**

- 不创建 bucket、不改变 provider 选择或引入运行时配置。
- 不改变预签名下载、直接上传或其他存储后端。
- 不把签名字段记录到日志或异常中。

## Decisions

- 将 SDK 返回的签名字段复制到新的 `HashMap`，再写入 `bucket`、`key`、
  `Content-Type`、`Cache-Control`。此方式保留 SDK 当前及未来必要字段，且不
  修改可能不可变的 SDK Map。
- 使用精确字段大小写，和 `PostPolicy` 中的 `eq` 条件一致。选择在适配器中
  回填而非要求调用方重建 policy，避免把 MinIO SDK 细节泄漏到 Core 调用方。
- 单元测试 mock 只返回签名字段并断言完整表单，作为不依赖容器的回归保护；
  实际 Docker MinIO 验证 multipart POST 的状态、元数据和读取内容。

## Risks / Trade-offs

- [Risk] MinIO SDK 增加签名字段时手工白名单会丢失字段。 -> Mitigation:
  复制 SDK 返回 Map 后仅补充缺失的 policy 字段。
- [Risk] 调用方提交与表单字段不一致会被服务端拒绝。 -> Mitigation: 返回
  `PresignedUpload#formFields` 中的固定字段，避免调用方猜测。
- [Risk] 签名或凭据泄漏到诊断输出。 -> Mitigation: 测试仅断言字段名和值，
  不记录完整表单、签名 URL、access key 或 secret。

## Migration Plan

发布后调用方继续提交 `PresignedUpload#formFields` 即可获得完整 POST 表单。
旧调用方无需变更；回滚只会恢复已知的上传失败行为，因此应在 Docker MinIO
验收通过后发布。

## Open Questions

None.
