# Delta Spec: repository-tooling

## ADDED Requirements

### Requirement: 双语 README 与 Agent Skill 引导

仓库 SHALL 提供中文 `README.md` 与英文 `README_EN.md` 两个完整版本，两文件顶部 MUST 互相引用语言链接；`README.md` MUST 包含 AI Agent Skill 小节，介绍 `.agents/skills/dinghai/SKILL.md` 的作用与使用方式（自动发现与 Kimi Code 手动调用）。README 内容变更时 MUST 同步两个语言版本与该 Skill 文件。

#### Scenario: 读者在 README 中发现 Agent Skill

- **WHEN** 读者阅读 README.md 的 AI Agent Skill 小节
- **THEN** 读者能得知 Skill 文件位置、自动触发机制与 Kimi Code 的手动调用方式 `/skill:dinghai`

#### Scenario: 中英文 README 互相跳转

- **WHEN** 读者打开 README.md 或 README_EN.md
- **THEN** 文件顶部存在指向另一语言版本的链接
