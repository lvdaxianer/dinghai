## Why

ProductIQ 已作为独立可构建、可部署的产品形成闭环，不应继续与 dinghai 基础组件共享
仓库和发布边界。现在迁移到专用 Gitee 仓库，使产品版本、问题和部署独立演进，同时从
dinghai 当前工作树移除重复副本。

## What Changes

- 将 `dinghai-productiq/` 作为目标仓库 `https://gitee.com/lvdaxianer/product-iq.git`
  的根目录发布，并保留该目录的 Git 提交历史。
- 在目标仓库执行后端、插件、验收基线和 Compose 配置验证，证明目录迁移没有改变交付物。
- **BREAKING** 从 `dinghai` 的当前分支删除 `dinghai-productiq/`，之后 ProductIQ
  的源码、部署和文档只在独立仓库维护。
- 保留 dinghai 内已有 OpenSpec archive 作为原仓库历史审计，不重写已发布提交。

## Capabilities

### New Capabilities

- `standalone-productiq-repository`: 将完整 ProductIQ 目录及其可追溯提交历史发布为
  独立仓库根目录，并验证交付物完整性。

### Modified Capabilities

无。现有 `openspec/specs/` 没有 ProductIQ capability；已归档的 ProductIQ 规格保留为
dinghai 的历史审计记录。

## Impact

- `dinghai-productiq/` 从 dinghai 当前树移除。
- Gitee `product-iq` 的 `main` 分支成为 ProductIQ 唯一源码、文档、Compose 与验收基线位置。
- dinghai 基础组件、现有 API、其他 `dinghai-*` 模块和用户未提交的治理 UI 修改不受影响。
