## 1. 扩展契约与配置

- [x] 1.1 定义宿主限流 key resolver 公共接口及每 key 并发配置默认值和正数校验。
- [x] 1.2 扩展自动装配，将可选 resolver 和每 key 上限传入 Web 控制器。

## 2. 组合许可实现

- [x] 2.1 先用失败测试定义 key 上传/读取额度独立、全局回滚和状态回收行为。
- [x] 2.2 实现带活跃 key 回收的上传/读取组合许可和异常安全释放。
- [x] 2.3 在 Web 上传、流式读取与 sendfile 完成路径接入 resolver key。

## 3. 回归测试与文档

- [x] 3.1 添加 resolver 空值、空白值和异常降级，以及 HTTP 429 行为测试。
- [x] 3.2 添加 sendfile 与流式路径的每 key 许可释放回归测试。
- [x] 3.3 更新中英文 README 和 OpenSpec 配置、边界说明。

## 4. 验证与集成

- [x] 4.1 运行 starter 模块及依赖模块 Maven 测试、OpenSpec 严格校验和 diff 检查。
- [ ] 4.2 完成代码审查、归档 OpenSpec，并将已验证任务合并回 `main`。
