# Proposal: add-bilingual-readme-and-skill-doc

## Why

仓库已随附 Agent Skill（`.agents/skills/dinghai/SKILL.md`），但 README 未介绍它的存在与使用方式，读者不知道可用 AI 助手直接消费该能力说明。同时仓库仅有中文 README，对非中文用户不友好，不利于开源传播。

## What Changes

- `README.md` 新增"AI Agent Skill"小节：介绍 `.agents/skills/dinghai/SKILL.md` 的作用与使用方式（支持 Agent Skill 的工具克隆仓库后自动发现；Kimi Code 中可 `/skill:dinghai` 手动调用）。
- 新增 `README_EN.md`：README 的完整英文版本（含相同的 Agent Skill 小节）。
- `README.md` 与 `README_EN.md` 顶部互相引用语言链接。
- 纯文档变更，无代码与行为规范变化。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `repository-tooling`：新增双语 README 与 README 介绍 Agent Skill 的文档要求。

## Impact

- 文档：`README.md` 修改、新增 `README_EN.md`。无代码、依赖、构建影响。
