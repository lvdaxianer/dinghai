# Design: add-bilingual-readme-and-skill-doc

## Context

README.md 为中文单语文档；`.agents/skills/dinghai/SKILL.md` 已存在但未在 README 中提及。开源仓库惯例提供英文 README 并在两版本头部互相链接。

## Goals / Non-Goals

**Goals:**

- README.md 新增 AI Agent Skill 小节（位置：模块清单之后、模块详情之前，靠近文档入口）。
- README_EN.md 完整翻译 README.md（含新 Skill 小节），文件名采用常见约定 `README_EN.md`。
- 两文件顶部互相引用：`[English](README_EN.md)` / `[中文](README.md)`。

**Non-Goals:**

- 不维护双语以外的更多语言版本。
- 不翻译 openspec/ 与代码注释（仓库规范为中文注释）。

## Decisions

- **英文版为完整镜像翻译**：逐节对应，避免读者跨文件查找；后续 README 变更需同步两版（写入扩展约定说明）。
- **Skill 小节内容**：说明 Skill 位置、触发机制（description/whenToUse 自动触发）、Kimi Code 手动调用方式 `/skill:dinghai`，以及"其他工具可参照 `.agents/skills/` 约定"。

## Risks / Trade-offs

- [双语 README 漂移] → 在英文版底部注明以中文为准的同步约定，并在 repository-tooling 规格中固化同步要求。
