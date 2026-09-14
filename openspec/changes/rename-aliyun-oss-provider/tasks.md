## 1. Naming contract and RED tests

**Task boundary and dispatch:** `aliyun-oss-starter-contract` owns only the Starter-facing naming, configuration, and SDK-contract RED tests in `spring-boot-starter` and the current Alibaba module test sources. It excludes production implementation, Maven module relocation, documentation, and real cloud access. Focused verification is `mvn -f dinghai-file-storage/pom.xml -pl spring-boot-starter -am -Dtest=FileStorageAutoConfigurationTest test`; broader verification is the file-storage reactor test suite. Direct execution is required because the tests and all subsequent Maven/package/Starter changes share one public naming boundary; parallel edits would leave an incompatible intermediate build.

- [x] 1.1 Record the direct-execution boundary and update RED tests for OSS V2 `aliyun-oss` provider selection, rejection of legacy `oss`, region property binding, environment credential validation, V2 PUT/GET presigning, renamed public Java types, and default remote cache creation.

## 2. Cross-module implementation migration

- [x] 2.1 Rename the Maven module directory, artifact, aggregator module, parent dependency, Starter dependency, Java package, public types, imports, and test packages from generic OSS names to `aliyun-oss` / `aliyunoss`.
- [x] 2.2 Replace the V1 SDK implementation with OSS V2 request/response models, environment credentials, V2 PUT/GET presigning, stable error mapping, and lifecycle handling.
- [x] 2.3 Update Starter provider enum, auto-configuration conditions, property holder, validation messages, cache routing, and all `aliyun-oss.*` configuration bindings while preserving non-presign runtime behavior.
- [x] 2.4 Add an opt-in real OSS integration test for bucket `dinghai` in `oss-cn-beijing`, using only injected environment credentials, random keys, and cleanup.
- [x] 2.5 Update Chinese/English READMEs, `.env.example`, main OpenSpec specs, and migration notes; verify no project-level Alibaba implementation reference retains the generic `oss` provider/module/config naming or application configuration AK/SK.

## 3. Verification and delivery

- [x] 3.1 Run focused Starter/module tests, full file-storage tests, `git diff --check`, and strict OpenSpec validation; perform workflow consistency, project hygiene, backend reliability, and code-review audits.
- [x] 3.2 Commit the complete atomic migration with the OpenSpec task footer, merge into the confirmed integration branch, archive the OpenSpec change, and verify the clean main worktree.
