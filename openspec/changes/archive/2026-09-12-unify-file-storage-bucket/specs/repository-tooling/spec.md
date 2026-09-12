## MODIFIED Requirements

### Requirement: 双语 README 与 Agent Skill 引导
仓库 SHALL 提供中文 `README.md` 与英文 `README_EN.md` 两个完整版本，两文件顶部 MUST
互相引用语言链接；`README.md` MUST 包含 AI Agent Skill 小节，介绍
`.agents/skills/dinghai/SKILL.md` 的作用与使用方式（自动发现与 Kimi Code 手动调用）。
README、`.env.example` 和 Agent Skill MUST 使用统一的 `dinghai.file-storage.bucket` 文件存储
配置，并明确 Local 映射为 `local.root / bucket / objectKey`、MinIO 映射为
`bucket / objectKey`，且 `storage-alias` 仅参与稳定 `fileId`。模块或契约变更时 MUST 同步更新。

#### Scenario: 使用者找到统一 bucket 配置
- **WHEN** 使用者阅读中文 README、英文 README 或 Agent Skill 的文件存储示例
- **THEN** 三处均将 bucket 配置在 `dinghai.file-storage.bucket`，不展示 `minio.bucket`，并解释
  Local 与 MinIO 的对应物理路径

#### Scenario: 读者在 README 中发现 Agent Skill
- **WHEN** 读者阅读 README.md 的 AI Agent Skill 小节
- **THEN** 读者能得知 Skill 文件位置、自动触发机制与 Kimi Code 的手动调用方式 `/skill:dinghai`

#### Scenario: 中英文 README 互相跳转
- **WHEN** 读者打开 README.md 或 README_EN.md
- **THEN** 文件顶部存在指向另一语言版本的链接
