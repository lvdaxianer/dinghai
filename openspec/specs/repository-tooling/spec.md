# repository-tooling Specification

## Purpose

仓库级工具与分发资产：随仓库提交、面向协作者与 AI 编码助手的说明与配置（如 Agent Skill），与模块行为规格无关。

## Requirements

### Requirement: 仓库随附 Agent Skill 能力说明

仓库 SHALL 在 `.agents/skills/dinghai/SKILL.md` 提供随项目分发的 Agent Skill，描述各模块的 Maven 坐标、核心能力、使用范式与适用边界，使支持 Agent Skill 机制的 AI 编码助手在克隆仓库后即可发现并使用本库。Skill 内容 MUST NOT 包含本地路径或个人环境信息，且 MUST 与 README 的能力描述保持一致；模块或契约变更时 MUST 同步更新。

#### Scenario: AI 助手通过 Skill 发现组件库能力

- **WHEN** 使用者在克隆的仓库中以支持 Agent Skill 的工具发起缓存/共享状态类需求
- **THEN** 工具扫描到 `.agents/skills/dinghai/SKILL.md`，并依据其中的坐标与用法引入 `dinghai-shared-state`
