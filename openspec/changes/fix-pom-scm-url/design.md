# Design: fix-pom-scm-url

## Context

T7（发布配置补齐）引入 POM 元数据时按 groupId `io.github.lvdaxianer` 的惯例写了 GitHub 地址；实际远程为 Gitee（`git push` 输出确认 `gitee.com/lvdaxianer/dinghai.git`）。

## Goals / Non-Goals

**Goals:**

- `url`、`scm.connection`、`scm.developerConnection`、`scm.url` 全部指向 Gitee。

**Non-Goals:**

- 不变更 groupId（`io.github.lvdaxianer` 是 Sonatype 命名空间，与托管平台无关，无需修改）。
- 不处理 README 中的链接（README 当前未引用 GitHub 地址）。

## Decisions

- 直接修正为 `https://gitee.com/lvdaxianer/dinghai` 系列地址；connection 用 `scm:git:https://gitee.com/lvdaxianer/dinghai.git`，developerConnection 用 `scm:git:git@gitee.com:lvdaxianer/dinghai.git`。

## Risks / Trade-offs

- 无。
