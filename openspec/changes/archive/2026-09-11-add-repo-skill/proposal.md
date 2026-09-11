# Proposal: add-repo-skill

## Why

dinghai 是可复用的基础组件库，但使用 AI 编码助手的开发者（包括作者自己在新项目中）无法自动感知仓库能力，容易在需要缓存/共享状态时重新造轮子。仓库需要一份随项目分发的 Agent Skill，让支持该机制的工具（Kimi Code 等扫描 `.agents/skills/` 的工具）在克隆仓库后即可发现并使用 `dinghai-shared-state`。

## What Changes

- 新增 `.agents/skills/dinghai/SKILL.md`：描述 dinghai-shared-state 的 Maven 坐标、核心能力、契约操作速查、配置与使用范式、适用/不适用边界、扩展约定。
- 纯文档变更，不涉及任何代码与行为规范变化；内容只引用公开发布坐标，不包含本地路径或环境相关信息。

## Capabilities

### New Capabilities

- `repository-tooling`：仓库级工具与分发资产（随仓库提交的 Agent Skill 等），与模块行为规格无关

### Modified Capabilities

（无——本变更不改变 shared-state-cache 的任何需求，仅为仓库增加 AI 工具可分发的说明文件）

## Impact

- 新增单个文档文件 `.agents/skills/dinghai/SKILL.md`，无代码、依赖、构建影响。
- `.agents/` 未被 `.gitignore` 忽略，文件随仓库提交分发。
