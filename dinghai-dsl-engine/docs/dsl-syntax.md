# DSL 语法规范

## 1. 文档范围

本文档定义 `dinghai-dsl-engine` 的公共 DSL 请求格式，以及当前第一阶段
`relational-query` v1 的结构化语法。

本文档描述的是 DSL，不是 SQL 语法。请求端只能提交结构化 DSL；解析器把 DSL
转换为类型化 AST 和后端无关的 `ExecutionPlan`，之后由已注册的
`DataAdapter` 选择数据库方言、生成参数化命令并执行。

当前支持关系 DSL。后续 DSL 通过 `DslAdapter` 注册接入，仍然复用本文档定义的
通用请求信封，但由各自的适配器定义 `payload` 语义和版本。

## 2. 通用 DSL 请求信封

所有外部 DSL 请求使用以下结构：

```json
{
  "dslType": "relational-query",
  "version": 1,
  "payload": {},
  "parameters": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dslType` | string | 是 | 已注册的 DSL 类型。当前为 `relational-query`。 |
| `version` | integer | 是 | DSL 版本。当前关系 DSL 为 `1`。未知版本必须拒绝。 |
| `payload` | object | 是 | 对应 DSL 的结构化载荷。 |
| `parameters` | object | 是 | 参数名到参数值的映射，只用于绑定参数，不允许放 SQL 片段。 |

`dslType` 和 `version` 决定使用哪个 `DslAdapter`。客户端不能通过请求体指定
数据库 adapter、数据源、数据库账号密码、`userId`、`domainId` 或其他服务端身份。

## 3. relational-query v1

### 3.1 payload 总体结构

```json
{
  "from": {
    "table": "fact_wireless_outage_timeline",
    "alias": "f"
  },
  "select": [
    {"field": "f.event_date"}
  ],
  "joins": [],
  "where": [],
  "groupBy": [],
  "orderBy": [],
  "limit": 100
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `from` | object | 是 | 无 | 查询起始表。 |
| `select` | array<object> | 是 | 无 | 至少一个字段或聚合投影。 |
| `joins` | array<object> | 否 | `[]` | 同一数据源内的 `INNER JOIN` 或 `LEFT JOIN`。 |
| `where` | array<object> 或 object | 否 | `[]` | 参数化比较条件或逻辑条件组。 |
| `groupBy` | array<string> | 否 | `[]` | 别名限定的分组字段。 |
| `orderBy` | array<object> | 否 | `[]` | 别名限定的排序字段和方向。 |
| `limit` | integer | 是 | 无 | 正整数，并受服务端行数限制约束。 |

`payload` 只接受上述字段。未知字段，例如 `sql`、`selectSql` 或任意 SQL 片段，
必须被拒绝。

### 3.2 表和字段标识符

表名、别名和字段名必须是安全标识符：

- 首字符必须是字母或 `_`。
- 后续字符只能是字母、数字或 `_`。
- 字段必须使用单个表别名限定，例如 `f.event_date`。
- 不接受 `f.*`、`schema.table`、函数表达式、SQL 关键字拼接或带分号的内容。
- `table` 是宿主 `RelationalSchemaPolicy` 使用的逻辑表名，不代表客户端可以任意访问物理表。
- `field` 是白名单中的逻辑字段；物理表名和物理列名由服务端 schema policy 解析。

示例：

```json
{
  "from": {"table": "fact_wireless_outage_timeline", "alias": "f"},
  "select": [
    {"field": "f.event_date"},
    {"field": "f.status", "alias": "outage_status"}
  ],
  "limit": 100
}
```

### 3.3 select 投影和聚合

普通字段投影：

```json
{"field": "f.event_date", "alias": "event_day"}
```

聚合投影必须同时提供 `aggregate` 和 `field`，`alias` 可选：

```json
{"aggregate": "count", "field": "a.asset_id", "alias": "asset_count"}
```

当前支持的聚合函数：

| 值 | 含义 |
| --- | --- |
| `count` | 计数 |
| `sum` | 求和 |
| `avg` | 平均值 |
| `min` | 最小值 |
| `max` | 最大值 |

字段和聚合函数仍然必须通过 schema policy 和计划语义校验。DSL 不支持任意函数、
表达式、子查询或原始投影文本。

### 3.4 JOIN

JOIN 必须包含连接类型、目标表和至少一个 `on` 条件：

```json
{
  "type": "left",
  "table": {"table": "outage_asset", "alias": "a"},
  "on": [
    {"left": "f.asset_id", "right": "a.asset_id"}
  ]
}
```

`type` 支持：

- `inner`
- `left`

JOIN 约束：

- 必须存在 `on`，不能省略或使用空数组。
- `left` 和 `right` 必须是别名限定字段。
- 目标表必须出现在连接条件的至少一侧，禁止伪装成 JOIN 的同表条件。
- 表和字段必须通过白名单校验。
- 只允许同一服务端数据源内 JOIN，不支持 MySQL、Doris、PostgreSQL 之间的跨库 JOIN。
- JOIN 数量受服务端 `max-joins` 限制，默认最多 8 个。
- 不接受没有连接条件的笛卡尔积。

### 3.5 WHERE 条件

单个比较条件：

```json
{
  "field": "f.status",
  "operator": "eq",
  "parameter": "status",
  "logicalType": "string"
}
```

比较条件字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `field` | string | 别名限定字段。 |
| `operator` | string | 比较运算符。 |
| `parameter` | string | `parameters` 中对应的参数名。 |
| `logicalType` | string | 参数逻辑类型，必须与白名单字段类型一致。 |

当前支持的比较运算符：

| 运算符 | 说明 | 参数值 |
| --- | --- | --- |
| `eq` | 等于 | 标量 |
| `neq` 或 `not-eq` | 不等于 | 标量 |
| `gt` | 大于 | 标量 |
| `gte` | 大于等于 | 标量 |
| `lt` | 小于 | 标量 |
| `lte` | 小于等于 | 标量 |
| `like` | 模糊匹配 | 标量 |
| `in` | 集合包含 | 非空数组 |

逻辑条件使用 `and` 或 `or`，每个逻辑组必须至少包含一个子条件：

```json
{
  "and": [
    {
      "field": "f.status",
      "operator": "eq",
      "parameter": "status",
      "logicalType": "string"
    },
    {
      "field": "f.severity",
      "operator": "gte",
      "parameter": "severity",
      "logicalType": "integer"
    }
  ]
}
```

`where` 可以是比较条件数组，也可以是一个逻辑条件对象。逻辑条件可以嵌套，
但受结构深度、节点数和条件数量限制。

### 3.6 参数和逻辑类型

参数值必须在请求的 `parameters` 中提供，并且只能被 DSL 中声明的参数引用：

```json
{
  "where": [
    {
      "field": "f.status",
      "operator": "eq",
      "parameter": "status",
      "logicalType": "string"
    },
    {
      "field": "f.outage_id",
      "operator": "in",
      "parameter": "outageIds",
      "logicalType": "long"
    }
  ],
  "parameters": {
    "status": "OPEN",
    "outageIds": [101, 102]
  }
}
```

当前内置逻辑类型：

| 类型 | 标量示例 | 说明 |
| --- | --- | --- |
| `string` | `"OPEN"` | 文本 |
| `long` | `101` | 长整数；JSON 整数可绑定为整数数值 |
| `integer` | `3` | 整数 |
| `decimal` | `12.50` | 小数 |
| `boolean` | `true` | 布尔值 |
| `date` | `"2026-11-13"` | 日期或服务端日期对象 |
| `datetime` | `"2026-11-13T10:30:00"` | 日期时间或服务端日期时间对象 |

参数校验规则：

- 缺少被引用参数会被拒绝。
- 未被 DSL 引用的额外参数会被拒绝。
- `in` 必须绑定非空数组；其他运算符不能绑定数组。
- 参数值不能为 `null`。
- 同名参数必须保持逻辑类型一致。
- 参数逻辑类型必须与引用字段的白名单逻辑类型一致。
- 参数总量和单个 `in` 数组大小受服务端限制，默认分别为 256 和 1000。

所有参数最终都通过数据库驱动的绑定参数传递，不能拼接成 SQL 文本。

### 3.7 GROUP BY、ORDER BY 和 LIMIT

`groupBy` 使用别名限定字段字符串：

```json
"groupBy": ["f.event_date", "f.status"]
```

`orderBy` 使用字段和方向对象：

```json
"orderBy": [
  {"field": "f.event_date", "direction": "desc"},
  {"field": "f.status", "direction": "asc"}
]
```

`direction` 支持 `asc` 和 `desc`。`limit` 必须是正整数，并且不能超过服务端
配置的 `max-rows` 或关系计划限制。

## 4. 完整示例

```json
{
  "dslType": "relational-query",
  "version": 1,
  "payload": {
    "from": {
      "table": "fact_wireless_outage_timeline",
      "alias": "f"
    },
    "select": [
      {"field": "f.event_date", "alias": "event_day"},
      {"field": "a.asset_name", "alias": "asset_name"},
      {"aggregate": "count", "field": "f.outage_id", "alias": "outage_count"}
    ],
    "joins": [
      {
        "type": "left",
        "table": {"table": "outage_asset", "alias": "a"},
        "on": [
          {"left": "f.asset_id", "right": "a.asset_id"}
        ]
      }
    ],
    "where": {
      "and": [
        {
          "field": "f.status",
          "operator": "eq",
          "parameter": "status",
          "logicalType": "string"
        },
        {
          "field": "f.event_date",
          "operator": "gte",
          "parameter": "startDate",
          "logicalType": "date"
        }
      ]
    },
    "groupBy": ["f.event_date", "a.asset_name"],
    "orderBy": [
      {"field": "f.event_date", "direction": "desc"}
    ],
    "limit": 100
  },
  "parameters": {
    "status": "OPEN",
    "startDate": "2026-11-01"
  }
}
```

该请求经过以下阶段：

```text
DslRequest
  -> DslAdapterRegistry
  -> relational-query parser
  -> 类型化 AST 与语义校验
  -> ExecutionPlan
  -> DataAdapterRegistry
  -> MySQL/Doris 或 PostgreSQL 参数化命令
  -> JDBC DataSource
  -> QueryResult
```

计划扩展可以在服务端代码中增加逻辑删除、租户或数据域约束。扩展产生的是类型化
约束，不是 SQL 字符串，也不能由请求体动态指定。

## 5. 明确禁止的输入

以下内容不是本 DSL 的语法：

```json
{"sql": "select * from fact_wireless_outage_timeline"}
```

也不允许：

- `SELECT`、`INSERT`、`UPDATE`、`DELETE`、DDL 或任意 SQL 片段。
- 函数文本、表达式文本、子查询或数据库方言专有语法。
- 未经 schema policy 白名单校验的表和字段。
- 客户端指定 `adapter`、数据源连接、数据库账号密码或服务端身份。
- 跨 MySQL、Doris、PostgreSQL 的跨数据源 JOIN。

## 6. 扩展其他 DSL

新增 DSL 时实现并注册 `DslAdapter`：

```java
public interface DslAdapter {
    String type();

    boolean supports(DslRequest request);

    ExecutionPlan parse(DslRequest request, DslContext context);
}
```

新适配器必须明确支持的 `dslType` 和版本，并将请求转换为后端无关的执行计划。
它不能把未知 payload 当作 SQL，也不能绕过计划校验、权限策略、参数绑定和运行时限制。

数据后端由独立的 `DataAdapter` 负责。DSL 语法不因为 MySQL、Doris 或 PostgreSQL
而改变；方言差异只存在于后端编译器和数据适配器内部。
