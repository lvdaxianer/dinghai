# Proposal: fix-pom-scm-url

## Why

父 POM 的 `url` 与 `scm` 元数据指向 GitHub（`github.com/lvdaxianer/dinghai`），但仓库实际托管在 Gitee（`gitee.com/lvdaxianer/dinghai`）。Maven Central 发布会校验这些元数据，指向错误的地址会误导使用者且可能影响发布校验。

## What Changes

- 父 `pom.xml` 的 `<url>` 与 `<scm>`（connection/developerConnection/url）从 GitHub 地址修正为 Gitee 地址。
- 纯构建配置变更，无代码与行为变化。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `repository-tooling`：新增发布元数据与实际托管地址一致的要求。

## Impact

- 构建配置：`pom.xml` 元数据。无代码、依赖影响。
