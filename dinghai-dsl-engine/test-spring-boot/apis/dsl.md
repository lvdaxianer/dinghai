# DSL Engine Test REST API

## 1. 模块概览与覆盖范围

| 项目 | 内容 |
| --- | --- |
| 业务模块 | DSL Engine Test API（`dsl`） |
| 文档路径 | `dinghai-dsl-engine/test-spring-boot/apis/dsl.md` |
| 模块职责 | 接收结构化 DSL，调用 DSL parser/plan extension/data adapter；用于本地和真实数据库协议验收 |
| DSL 语法规范 | [`dinghai-dsl-engine/docs/dsl-syntax.md`](../../docs/dsl-syntax.md) |
| 基础 URL / 版本 | `http://localhost:8080`；当前无 URL 版本前缀 |
| 通用认证与约定 | 本测试应用不实现生产登录或 Token 白名单；应由企业网关保护。请求使用 JSON，响应统一包含 `code`、`message`、`data`、`traceId` 或错误详情 |

### 1.1 对外 Controller/route 覆盖表

| Controller / 路由归属 | 对外端点 | 文档章节 | 覆盖状态 |
| --- | --- | --- | --- |
| `TestDslController` | `POST /api/test/dsl/execute` | [执行 DSL](#2-执行-dsl) | 已覆盖 |
| `TestDslController` | `POST /api/test/dsl/validate` | [校验 DSL](#3-校验-dsl) | 已覆盖 |
| `TestDslController` | `POST /api/test/dsl/explain` | [解释计划](#4-解释计划) | 已覆盖 |

## 2. 执行 DSL

### 2.1 接口概览

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 执行结构化 DSL |
| 接口用途 | 解析、校验、应用服务端计划扩展、编译为后端参数化命令并通过已注册数据适配器查询 |
| 业务场景 | 测试客户端验证 MySQL/Doris MySQL 协议或 PostgreSQL 的真实结果映射 |
| 权限要求 | 生产环境由外部网关/企业应用认证；测试应用只使用服务端 `dinghai.test.user-id` 和 `domain-id` |
| 幂等性 | 只读查询；无写操作和幂等键 |
| 版本 | `DslRequest.version=1`；关系 DSL 为 `relational-query` v1 |

### 2.2 请求定义

#### 2.2.1 Method and URL

```http
POST http://localhost:8080/api/test/dsl/execute
```

| 项目 | 内容 |
| --- | --- |
| Method | `POST` |
| URL | `http://localhost:8080/api/test/dsl/execute` |
| Content-Type | `application/json` |
| Accept | `application/json` |

#### 2.2.2 param

路径参数：不适用。查询参数：不适用。Form 参数：不适用。

#### 2.2.3 Request headers

| Header | 必填 | 类型/格式 | 示例 | 用途 | 缺失或非法时 |
| --- | --- | --- | --- | --- | --- |
| `Content-Type` | 是 | `application/json` | `application/json` | 请求体编码 | 返回 `415` 或请求解析错误 |
| `X-Trace-Id` | 否 | `[A-Za-z0-9._:-]{1,128}` | `trace_dsl_001` | 链路关联 | 缺失或非法时由服务端生成 |
| `Authorization` | 按部署 | `Bearer <TOKEN>` | `Bearer <SERVICE_TOKEN>` | 应由外部网关认证 | 本测试应用本身不校验 |

请求 Cookie：不适用。响应 Cookie：不适用。

#### 2.2.4 requestBody

请求体必须是 `DslRequest`：

字段语义和 `relational-query v1` 的完整结构请先阅读
[DSL 语法规范](../../docs/dsl-syntax.md)。本节只说明该语法在 HTTP 接口中的请求位置和接口约定。

```json
{
  "dslType": "relational-query",
  "version": 1,
  "payload": {
    "from": {"table": "fact_wireless_outage_timeline", "alias": "f"},
    "select": [{"field": "f.event_date"}],
    "where": [],
    "joins": [],
    "groupBy": [],
    "orderBy": [],
    "limit": 100
  },
  "parameters": {}
}
```

| 字段路径 | 类型 | 必填 | 默认值 | 格式/范围/枚举 | 示例 | 业务说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `dslType` | string | 是 | 无 | 已注册类型；当前为 `relational-query` | `relational-query` | DSL 类型 |
| `version` | integer | 是 | 无 | 当前为 `1` | `1` | DSL 版本 |
| `payload` | object | 是 | 无 | 只能使用 DSL schema 字段 | `{...}` | 结构化 DSL，不允许 `sql` |
| `payload.from` | object | 是 | 无 | 单数据源表 | `{"table":"...","alias":"f"}` | 查询起始表 |
| `payload.select` | array<object> | 是 | 无 | 至少一个白名单字段或聚合项 | `[{"field":"f.event_date"}]` | 投影字段 |
| `payload.joins` | array<object> | 否 | `[]` | 每个 JOIN 必须有 `type`、表、`on` | `[]` | 同一数据源内 JOIN |
| `payload.where` | array<object> | 否 | `[]` | 参数化条件，字段必须使用别名 | `[]` | 过滤条件 |
| `payload.groupBy` | array<string> | 否 | `[]` | 别名限定字段 | `[]` | 分组字段 |
| `payload.orderBy` | array<object> | 否 | `[]` | `field` + `direction` | `[]` | 排序 |
| `payload.limit` | integer | 是 | 无 | 正数且不超过服务端限制 | `100` | 返回行数上限 |
| `parameters` | object | 是 | 无 | 只作为绑定参数值 | `{}` | 不接受 SQL 片段 |

未知顶层字段（例如 `adapter`、`userId`、`domainId`）和未知 DSL 字段会被拒绝。数据库账号、密码、数据源选择和服务端身份不得放在请求体中。

### 2.3 响应定义

#### 2.3.1 Response overview

| HTTP 状态码 | 业务含义 | 触发条件 | 调用方处理 |
| --- | --- | --- | --- |
| `200 OK` | 查询成功 | DSL 编译和后端执行成功 | 读取 `data.columns` 与 `data.rows` |
| `400 Bad Request` | DSL/请求错误 | 未知字段、版本、白名单字段或参数错误 | 按稳定 `code` 修正请求 |
| `403 Forbidden` | 服务端上下文缺失 | 用户或数据域没有可信映射 | 修复服务端认证/权限 |
| `429 Too Many Requests` | 运行时边界拒绝 | 并发、超时或结果限制 | 按退避策略处理 |
| `502 Bad Gateway` | 数据库执行失败 | JDBC 或结果映射失败 | 使用 `traceId` 排查 |
| `500 Internal Server Error` | 未预期服务错误 | 未分类内部异常 | 使用 `traceId` 报障 |

#### 2.3.2 Success response

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "columns": [{"name": "event_date", "logicalType": "date"}],
    "rows": [["2026-11-13"]],
    "rowCount": 1,
    "truncated": false
  },
  "traceId": "trace_dsl_001"
}
```

`data.rows` 是真实数据库返回的数据；审计日志不记录完整结果、参数值或密码。

#### 2.3.3 Error response

```json
{
  "code": "UNKNOWN_FIELD",
  "message": "field is not allowed",
  "details": [{"field": "$.payload.sql", "reason": "field is not allowed"}],
  "traceId": "trace_dsl_002"
}
```

错误响应不返回堆栈、原始 SQL、绑定参数、Token、数据库连接信息或密码。

### 2.4 Complete example

```bash
curl --request POST 'http://localhost:8080/api/test/dsl/execute' \
  --header 'Content-Type: application/json' \
  --header 'X-Trace-Id: trace_dsl_001' \
  --data '{
    "dslType":"relational-query",
    "version":1,
    "payload":{
      "from":{"table":"fact_wireless_outage_timeline","alias":"f"},
      "select":[{"field":"f.event_date"}],
      "where":[],"joins":[],"groupBy":[],"orderBy":[],"limit":100
    },
    "parameters":{}
  }'
```

## 3. 校验 DSL

### 3.1 接口概览

与执行 DSL 相同，但只调用 parser、语义校验和计划扩展，不获取数据库连接；成功响应的 `data` 为 `PlanSummary`。

### 3.2 Method and URL

```http
POST http://localhost:8080/api/test/dsl/validate
```

请求头和请求体与“执行 DSL”相同。成功响应示例：

```json
{
  "code":"OK",
  "message":"success",
  "data":{
    "planType":"relational-query",
    "planVersion":1,
    "metadata":{
      "joinCount":0,
      "conditionCount":2,
      "appliedPlanExtensions":["test-active-domain-scope"]
    }
  },
  "traceId":"trace_validate_001"
}
```

`metadata` 不含 SQL 或绑定参数值。

## 4. 解释计划

### 4.1 接口概览

与校验 DSL 相同，但语义上用于展示后端无关计划摘要。它不会返回 MySQL/PostgreSQL SQL；SQL 只在服务端 Data Adapter 内部由计划编译并使用 JDBC 绑定参数执行。

### 4.2 Method and URL

```http
POST http://localhost:8080/api/test/dsl/explain
```

请求头、请求体、成功响应和错误响应与“校验 DSL”相同。

## 5. 通用约定检查

- 认证：生产环境由外部网关或企业应用完成登录、服务 Token 白名单和路径限制；Token 使用 `Authorization: Bearer <SERVICE_TOKEN>`，不得写入请求体或日志。
- 权限：`userId`、`domainId` 来自服务端可信上下文，客户端传入同名字段会被拒绝。
- 数据源：客户端不能传 adapter 或数据源；应用启动配置选择已注册的 adapter。
- 安全：只接受结构化 DSL；禁止原始 SQL、DDL、INSERT、UPDATE、DELETE 和跨数据源 JOIN。
- 日志：只记录 trace ID、计划摘要、adapter、结果码、耗时和行数，不记录密码、Token、SQL、参数值或完整结果。
- 时间：示例日期仅为占位测试数据；真实时间类型和时区由 schema logicalType 与数据库协议映射共同约束。
