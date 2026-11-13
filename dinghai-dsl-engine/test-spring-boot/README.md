# DSL Engine Test Spring Boot

该模块是 DSL 引擎的真实 Spring Boot 测试应用，不是企业身份服务。它通过 starter 装配真实 JDBC 数据适配器，提供结构化 DSL 的校验、解释和执行接口。

## 启动

默认使用 MySQL/Doris MySQL 协议适配器。数据库账号只从环境变量读取，不写入请求体：

```bash
export DINGHAI_TEST_JDBC_URL='jdbc:mysql://127.0.0.1:3306/dinghai_test'
export DINGHAI_TEST_DB_USERNAME='dinghai_test'
export DINGHAI_TEST_DB_PASSWORD='change-me-locally'
mvn -pl dinghai-dsl-engine/test-spring-boot -am spring-boot:run
```

切换 PostgreSQL：

```bash
export DINGHAI_TEST_DATA_ADAPTER=postgresql
export DINGHAI_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/dinghai_test'
mvn -pl dinghai-dsl-engine/test-spring-boot -am spring-boot:run
```

请求不能指定 adapter、数据源、数据库账号密码、`userId` 或 `domainId`。测试应用从服务端环境构造可信 `QueryContext`；生产系统应在网关或企业应用中完成登录、服务 Token 白名单和用户/域权限映射。

接口完整契约见 [`apis/dsl.md`](apis/dsl.md)。
