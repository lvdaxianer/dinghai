# dinghai-productiq

`dinghai-productiq` 是 `dinghai` 仓库中的产品知识本体问答助手子项目。它与现有
`dinghai-*` 基础组件保持构建边界隔离，后端可以从本目录独立构建和启动。

## 目录

```text
dinghai-productiq/
├── backend/       # Java 21 / Spring Boot 后端
├── skills/        # 离线知识加工 Skills 与共享协议
├── plugins/       # 浏览器插件交付物
├── docs/          # 产品运行、部署和验收资料
└── knowledge/     # 本地可信知识目录（原始资料不提交）
```

## 后端构建

后端使用 Maven 坐标 `io.github.lvdaxianer.dinghai:knowledge-assistant`，Java 根包为
`io.github.lvdaxianer.dinghai.projectiq`。根 Maven 聚合不会自动引入本产品模块；请在后端目录
执行构建：

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml spring-boot:run
```

默认 HTTP 端口为 `8080`，可通过 `SERVER_PORT` 环境变量覆盖。模型密钥、数据库和其他
外部服务配置只应由部署环境注入，不要写入仓库文件。
