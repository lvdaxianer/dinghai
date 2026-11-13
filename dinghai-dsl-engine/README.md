# dinghai-dsl-engine

`dinghai-dsl-engine` 的核心是 DSL 解析和编译，不是把请求直接变成 SQL。调用链固定为：

```text
DslRequest
  -> DslAdapterRegistry
  -> DSL parser / AST / semantic validation
  -> ExecutionPlan
  -> DataAdapterRegistry
  -> backend compiler
  -> parameterized command
  -> DataSource / JDBC
  -> QueryResult
```

请求端只能提交已注册的结构化 DSL，不能提交原始 SQL、SQL 片段、数据库账号密码或 adapter 名称。

## Maven 构件

| 构件 | 用途 |
| --- | --- |
| `dinghai-dsl-engine-core` | `DslRequest`、`ExecutionPlan`、`QueryResult` 和诊断模型 |
| `dinghai-dsl-engine-spi` | DSL/Data Adapter、编译、缓存、执行边界、指标和审计扩展点 |
| `dinghai-dsl-engine-relational` | `relational-query` v1 解析器、类型化 AST、白名单和计划构建 |
| `dinghai-dsl-engine-mysql` | MySQL/Doris MySQL 协议公共能力编译和 JDBC 执行 |
| `dinghai-dsl-engine-postgresql` | PostgreSQL 编译和 JDBC 执行 |
| `dinghai-dsl-engine-spring-boot-starter` | 启动期配置、DataSource、Registry 和执行引擎装配 |

provider 构件按需引入，不要在公共 core 中复制或引入企业业务代码。

Spring Boot 应用至少显式选择一个 provider 构件；starter 中的 provider 依赖是 optional，不会把两个数据库驱动强制传递给所有应用：

```xml
<dependency>
  <groupId>io.github.lvdaxianer</groupId>
  <artifactId>dinghai-dsl-engine-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>io.github.lvdaxianer</groupId>
  <artifactId>dinghai-dsl-engine-mysql</artifactId>
  <version>1.0.0</version>
</dependency>
```

使用 PostgreSQL 时，将最后一个构件替换为
`dinghai-dsl-engine-postgresql`，并将配置中的默认 adapter 改为
`postgresql`。

## 第一阶段 DSL：relational-query v1

这是参考 DSL，不是原始 SQL。它支持受限的单数据源查询：字段投影、参数化 `WHERE`、`INNER JOIN`、`LEFT JOIN`、聚合、`GROUP BY`、`ORDER BY` 和 `LIMIT`。

```json
{
  "dslType": "relational-query",
  "version": 1,
  "payload": {
    "from": {"table": "fact_outage", "alias": "f"},
    "select": [{"field": "f.event_date"}],
    "where": [{
      "field": "f.status",
      "operator": "eq",
      "parameter": "status",
      "logicalType": "string"
    }],
    "limit": 100
  },
  "parameters": {"status": "OPEN"}
}
```

表、字段和物理名称必须由宿主实现 `RelationalSchemaPolicy` 白名单解析。解析器拒绝原始 SQL、未知字段、未限定字段、笛卡尔积、危险标识符和超出复杂度限制的请求。

## Spring Boot 配置

引入 `dinghai-dsl-engine-spring-boot-starter`，数据库连接继续由服务端的 Spring `DataSource` 配置管理：

```yaml
dinghai:
  data-query:
    default-data-adapter: mysql # mysql 或 postgresql
    dsl-adapters:
      relational-query: enabled
    max-request-bytes: 1048576
    max-concurrency: 32
    timeout: 30s
    max-plan-nodes: 1000
    max-plan-depth: 16
    max-joins: 8
    max-rows: 10000

spring:
  datasource:
    url: jdbc:mysql://db.example.internal:3306/analytics
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

PostgreSQL 将 `default-data-adapter` 改为 `postgresql`，并使用 PostgreSQL JDBC URL。Doris 使用 MySQL 协议端口时复用 `mysql` 适配器；该适配器只生成 MySQL 与 Doris 公共支持的 SQL，不放行 Doris 特有语法。PostgreSQL 不存在时不会自动回退到 MySQL。

应用启动时会校验默认 adapter、DataSource、DSL 适配器、能力声明和重复注册；错误会在应用 ready 前失败。请求不能通过参数指定未注册的 adapter，也不能跨 MySQL、Doris、PostgreSQL 做 JOIN。

宿主应用必须提供 schema policy 和 `PlanPolicy`；公共模块不实现企业表权限、`userId`/`domainId` 权限或租户规则。缺少 `PlanPolicy` 时 starter fail-fast，禁止默认放行数据权限。

## 计划扩展：通过代码 Bean 注入语义

逻辑删除、租户隔离、数据域约束等跨请求规则不通过 YAML 拼接 SQL，也不允许请求体携带规则。宿主应用以代码实现 `PlanExtension`，并注册为 Spring Bean；starter 会在启动阶段收集扩展、校验唯一 `id`、按 `order` 排序并冻结 `PlanExtensionRegistry`：

```java
@Bean
RelationalPlanExtension activeRowsExtension() {
    return new RelationalPlanExtension() {
        @Override
        public String id() {
            return "active-rows";
        }

        @Override
        public List<RelationalConstraint> constraints(
                RelationalQueryPlan plan, PlanExtensionContext context) {
            return List.of(RelationalConstraint.of(
                    new RelationalQueryAst.RelationalField("f", "active"),
                    RelationalQueryAst.ComparisonOperator.EQ,
                    "integer",
                    1,
                    ConstraintPlacement.WHERE));
        }
    };
}
```

关系扩展只能返回类型化、白名单校验后的约束；重写器负责把约束变成绑定参数，并根据 `ConstraintPlacement` 放入 `WHERE` 或目标 JOIN 的 `ON`。扩展上下文只包含 DSL 类型、版本和服务端构造的 `QueryContext`，不包含原始请求、SQL、数据库连接信息或密码。扩展失败、重复 ID 或非法字段会在启动或编译阶段明确报错，不会自动改用其他扩展或数据库。

`PlanExtensionRegistry` 是公共 DSL 核心的扩展点，企业应用可以在自己的模块实现身份、租户和领域权限；公共仓库不反向依赖企业模块。

## 日志、审计和调优边界

默认安全日志只记录 `traceId`、计划摘要、adapter、结果码、耗时和行数，不记录密码、Token、原始 SQL、参数值或完整结果。`QueryAuditSink` 和 `QueryTelemetry` 可由宿主接入企业审计与指标系统，审计接收器故障不会改变查询结果。

运行时提供请求大小、计划节点/深度/JOIN、并发、超时、最大行数和 JDBC 查询超时控制；缓存只缓存不含参数值的 AST、计划或编译模板，使用 DSL/编译器/Schema/Policy/能力版本化键，默认不缓存查询结果。

## 企业认证边界

公共仓库不实现 Controller、登录、服务 Token 白名单或企业身份权限。企业应用可以在自己的网关/Controller 层配置：

```http
Authorization: Bearer ${SERVICE_TOKEN}
```

白名单策略示例为只允许 `/api/data-query/metricView/.*`。Token 必须由企业密钥管理或部署密文配置生成和保存，并映射到服务端明确的用户、域或服务身份；请求体不得伪造 `userId`、`domainId`，日志不得输出 Token。真实 Token、密码和生产配置不得提交到 Git。

## 限制

- 当前首个 DSL 是 `relational-query` v1，其他 DSL 通过 `DslAdapter` 扩展。
- 当前关系后端是 MySQL/Doris 公共协议和 PostgreSQL；其他后端必须独立声明能力并显式注册。
- 当前不支持 DDL、`INSERT`、`UPDATE`、`DELETE`、原始 SQL、跨数据源 JOIN 或默认结果缓存。
