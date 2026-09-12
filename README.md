# dinghai（定海神针）

> 定海神针，稳住你的状态。

[English](README_EN.md) | 中文

dinghai 是 lvdaxianer 的个人开源基础组件库，从生产项目沉淀、抽离而来，零业务耦合，专注提供可直接复用的基础设施能力。

## 模块清单

| 模块 | 版本 | 说明 | 文档 |
| --- | --- | --- | --- |
| `dinghai-shared-state` | 1.0.0 | 共享状态缓存：Redis/Caffeine 双后端、熔断容错、可观测性、Spring Boot 3 自动装配 | [文档](dinghai-shared-state/README.md) |
| `dinghai-file-storage-core` | 1.0.0 | 后端无关的文件存储契约、对象模型、预签名模型与稳定错误码 | [文档](dinghai-file-storage/README.md) |
| `dinghai-file-storage-local` | 1.0.0 | 本地磁盘适配器，支持中文及多级对象 key、HMAC 预签名和流式读写 | [文档](dinghai-file-storage/README.md) |
| `dinghai-file-storage-minio` | 1.0.0 | 固定 bucket 的 MinIO 适配器与原生预签名能力 | [文档](dinghai-file-storage/README.md) |
| `dinghai-file-storage-spring-boot-starter` | 1.0.0 | 按 provider 自动装配文件存储，并提供本地 Web 预签名端点 | [文档](dinghai-file-storage/README.md) |

各模块的完整文档（Maven 坐标、配置项、使用示例、测试）见对应模块目录下的 README。

## AI Agent Skill

本仓库随附 Agent Skill 文件 [`.agents/skills/dinghai/SKILL.md`](.agents/skills/dinghai/SKILL.md)，面向支持 Agent Skill 机制的 AI 编码助手：它描述了各模块的 Maven 坐标、核心能力、操作速查与使用范式，让 AI 在遇到缓存、共享状态、会话续期、读穿加载等需求时主动建议引入本库，而不是重新造轮子。

使用方式：

- **自动发现**：支持 Agent Skill 的工具（如 Kimi Code）在克隆仓库后自动扫描 `.agents/skills/`，按触发词（缓存、共享状态、分布式计数等）自动加载；
- **手动调用**：Kimi Code 中可执行 `/skill:dinghai` 直接加载该 Skill。

## 发布

日常构建（`mvn test` / `mvn package`）不触发签名与发布插件。发布到 Maven Central 使用 `release` profile：

```bash
mvn -Prelease clean deploy
```

发布前置条件：

- 本机已配置 GPG 签名密钥（发布产物强制签名；本地验证产物可用 `-Dgpg.skip=true` 跳过）；
- `settings.xml` 中配置 id 为 `central` 的 server，携带 Central Portal 的用户令牌；
- 发布插件为 `central-publishing-maven-plugin`，`autoPublish=false`，上传后需在 Central Portal 手动确认发布。

## License

本项目基于 [MIT License](LICENSE) 开源。
