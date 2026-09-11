# Delta Spec: repository-tooling

## ADDED Requirements

### Requirement: 发布元数据与托管地址一致

父 POM 的 `url` 与 `scm` 元数据 MUST 指向仓库的实际托管地址（当前为 Gitee `gitee.com/lvdaxianer/dinghai`），MUST NOT 指向不存在或未使用的镜像地址。

#### Scenario: POM 元数据指向实际远程

- **WHEN** 检查父 POM 的 `url` 与 `scm` 配置
- **THEN** 全部地址与 `git remote get-url origin` 的实际托管平台一致
