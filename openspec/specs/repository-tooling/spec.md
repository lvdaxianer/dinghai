# repository-tooling Specification

## Purpose

仓库级工具与分发资产：随仓库提交、面向协作者与 AI 编码助手的说明与配置（如 Agent Skill），与模块行为规格无关。

## Requirements

### Requirement: 仓库随附 Agent Skill 能力说明

仓库 SHALL 在 `.agents/skills/dinghai/SKILL.md` 提供随项目分发的 Agent Skill，描述各模块的 Maven 坐标、核心能力、使用范式与适用边界，使支持 Agent Skill 机制的 AI 编码助手在克隆仓库后即可发现并使用本库。Skill 内容 MUST NOT 包含本地路径或个人环境信息，且 MUST 与 README 的能力描述保持一致；模块或契约变更时 MUST 同步更新。

#### Scenario: AI 助手通过 Skill 发现组件库能力

- **WHEN** 使用者在克隆的仓库中以支持 Agent Skill 的工具发起缓存/共享状态类需求
- **THEN** 工具扫描到 `.agents/skills/dinghai/SKILL.md`，并依据其中的坐标与用法引入 `dinghai-shared-state`

### Requirement: 双语 README 与 Agent Skill 引导

仓库 SHALL 提供中文 `README.md` 与英文 `README_EN.md` 两个完整版本，两文件顶部 MUST
互相引用语言链接；`README.md` MUST 包含 AI Agent Skill 小节，介绍 `.agents/skills/dinghai/SKILL.md`
的作用与使用方式（自动发现与 Kimi Code 手动调用）。README、`.env.example` 和 Agent Skill
MUST 使用统一的 `dinghai.file-storage.bucket` 文件存储配置，并明确 Local 映射为
`local.root / bucket / objectKey`、MinIO 映射为 `bucket / objectKey`，且 `storage-alias` 仅参与
稳定 `fileId`。模块或契约变更时 MUST 同步更新。

#### Scenario: 读者在 README 中发现 Agent Skill

- **WHEN** 读者阅读 README.md 的 AI Agent Skill 小节
- **THEN** 读者能得知 Skill 文件位置、自动触发机制与 Kimi Code 的手动调用方式 `/skill:dinghai`

#### Scenario: 中英文 README 互相跳转

- **WHEN** 读者打开 README.md 或 README_EN.md
- **THEN** 文件顶部存在指向另一语言版本的链接

#### Scenario: 使用者找到统一 bucket 配置

- **WHEN** 使用者阅读中文 README、英文 README 或 Agent Skill 的文件存储示例
- **THEN** 三处均将 bucket 配置在 `dinghai.file-storage.bucket`，不展示 `minio.bucket`，并解释
  Local 与 MinIO 的对应物理路径

### Requirement: 发布元数据与托管地址一致

父 POM 的 `url` 与 `scm` 元数据 MUST 指向仓库的实际托管地址（当前为 Gitee `gitee.com/lvdaxianer/dinghai`），MUST NOT 指向不存在或未使用的镜像地址。

#### Scenario: POM 元数据指向实际远程

- **WHEN** 检查父 POM 的 `url` 与 `scm` 配置
- **THEN** 全部地址与 `git remote get-url origin` 的实际托管平台一致
