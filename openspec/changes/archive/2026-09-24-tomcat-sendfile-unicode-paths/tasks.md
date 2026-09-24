## 1. Tomcat Unicode sendfile fallback

- [x] 1.1 以 TDD 为 `TomcatSendfileTransfer` 增加最终绝对路径 ASCII 普通文件资格判断，并覆盖 ASCII 注册、Unicode 拒绝、真实 HTTP 流式回退、内容长度、正文与 Range 回归；保持既有二进制 sendfile 配置行为。

## Task Boundary and Dispatch Plan

- **Agent / responsibility:** `tomcat-sendfile-unicode-paths` owns the isolated sendfile helper and
  its starter-module regression tests.
- **Allowed files:** `TomcatSendfileTransfer.java`, `TomcatSendfileTransferTest.java`,
  `TomcatSendfileIntegrationTest.java`, and this OpenSpec change.
- **Out of scope:** object-key parsing, provider path construction, global Tomcat configuration,
  other modules, and unrelated refactoring.
- **Dependencies:** existing local UTF-8 resolver and controller bounded `copyRange` behavior.
- **Direct execution fallback:** a single helper change and its tightly coupled unit and
  embedded-Tomcat tests share the same module and test fixture state; parallel implementation would
  overlap files and provide no safe ownership boundary.
- **Focused verification:** `mvn -pl dinghai-file-storage/spring-boot-starter -am
  -Dtest=TomcatSendfileTransferTest,TomcatSendfileIntegrationTest
  -Dsurefire.failIfNoSpecifiedTests=false test`.
- **Broader verification:** `mvn -pl dinghai-file-storage/spring-boot-starter -am test`.
- **Handoff evidence:** focused RED and GREEN outputs, embedded HTTP assertions, broader Maven
  output, OpenSpec strict validation, and diff review.
