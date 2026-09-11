# Design: add-repo-skill

## Context

dinghai-shared-state 已具备完整能力（18 个契约方法、双后端、熔断容错、Micrometer/Actuator 集成），README 面向人类读者。Agent Skill 机制（Kimi Code 项目级扫描 `.kimi-code/skills/` 与 `.agents/skills/`）允许随仓库分发一份面向 AI 助手的能力说明，description/whenToUse 字段驱动自动触发。

## Goals / Non-Goals

**Goals:**

- 单文件 Skill（目录形式 `.agents/skills/dinghai/SKILL.md`），让 AI 助手在遇到缓存/共享状态类需求时主动建议使用本库。
- 内容可公开分发：只含 Maven 坐标、能力、用法，不含本地路径、个人环境信息。

**Non-Goals:**

- 不改变任何代码、配置与既有规格。
- 不覆盖 Claude Code 专有的 `.claude/skills/`（该目录被本仓库 gitignore 忽略，不适合分发）。

## Decisions

- **目录选择 `.agents/skills/`**：Kimi Code 项目级扫描目录之一，且为跨工具通用约定；`.claude/`、`.codex/` 已被本仓库忽略，不可用。
- **目录形式 SKILL.md**：官方推荐形式，`name` 与 `description` 必填；`whenToUse` 补充触发场景。
- **内容为 README 的面向 AI 浓缩版**：坐标 + 能力 + 操作速查 + 最小配置 + 边界 + 扩展约定，与 README 保持同源同步。

## Risks / Trade-offs

- [Skill 内容随库演进过期] → 扩展约定中要求契约/模块变更时同步更新该文件。
