## Context

`dinghai-productiq/` 当前是 dinghai 仓库内独立构建的产品目录，但它已经具有自己的后端、
插件、运维文档和验收基线。目标 Gitee 仓库为空；dinghai 当前工作区还有不属于本迁移的
治理 UI 未提交修改，必须保留。

## Goals / Non-Goals

**Goals:**

- 将 ProductIQ 的当前目录树和该目录的 Git 提交历史发布为目标仓库的 `main` 根目录。
- 从一个全新克隆验证目标目录树、后端、插件、验收基线和 Compose 配置。
- 仅在目标远端和本地验证成功后，从 dinghai 当前树删除 `dinghai-productiq/`。
- 保留 dinghai 历史和 OpenSpec archive，不执行历史重写。

**Non-Goals:**

- 不修改 ProductIQ 的业务实现、模型配置、接口或运行时行为。
- 不迁移 dinghai 其他组件、根构建文件或任何用户未提交的治理 UI 修改。
- 不覆盖非空的目标远端，也不 force-push。

## Decisions

### 1. 使用 subtree split 作为目录历史导出

从当前已验证的 dinghai commit 对 `dinghai-productiq/` 执行 `git subtree split`，再将结果推送到
`product-iq` 的 `main`。这样目标仓库根 tree 与源目录 tree 相同，并保留 ProductIQ 各次提交；
相比复制文件后创建单一提交，提交审计、作者和时间线都不会丢失。

不使用 filter-repo 重写 dinghai 历史，因为这会影响基础组件仓库的所有引用；不使用普通拷贝，
因为它会丢失产品自身历史。

### 2. 先发布并验证，再删除源目录

T1 仅在目标远端无引用时创建 `main`，随后从新克隆执行与当前交付相同的构建和验收命令，并比较
目标根 tree 与源目录 tree。任一失败都不会删除 dinghai 目录。T2 通过普通 `git rm` 创建可回滚的
删除提交，不改写源仓库历史。

### 3. 迁移 scope 是目录而不是已有归档记录

目标仓库获得 `dinghai-productiq/` 目录内的全部交付物；dinghai 中既有的 OpenSpec archive 保留在
原仓库作为“曾在 monorepo 内演进”的历史证据。此次迁移 OpenSpec 也保留在 dinghai，记录目录移除。

## Risks / Trade-offs

- [目标远端在操作前变为非空] → 停止，读取其分支和历史后再请求处理方式，绝不覆盖。
- [推送或目标验证失败] → 不执行源目录删除；保留可重试的 source task worktree。
- [源目录删除后需要紧急恢复] → 对删除提交执行普通 revert；目标仓库保留独立历史。
- [本地 Maven/Node 缓存影响验证] → 在新克隆中分别运行后端、插件和验收命令，而不是复用源目录结果。

## Migration Plan

1. 核验目标远端为空，生成 ProductIQ subtree commit 并无 force 推送到 `main`。
2. 克隆目标仓库到独立本地目录，比较根 tree，执行后端、插件、验收及 Compose 静态验证。
3. 合并发布验证任务；从新的 dinghai task worktree 删除 `dinghai-productiq/`。
4. 复核 dinghai 中不存在该目录、目标远端仍可克隆且验证通过，归档迁移 OpenSpec。

## Open Questions

无。目标 URL、目录迁移范围和源仓库删除授权均由用户明确提供。
