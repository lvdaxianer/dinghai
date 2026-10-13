## 1. 文件名响应头修复

- [x] 1.1 先在 `FileStorageWebControllerTest` 增加并验证失败的回归测试，覆盖 `/rsm/.../index.gltf`、多级 object key、inline、attachment、中文文件名和路径穿越；再在 `FileStorageWebController.responseHeaders()` 中使用已校验 key 的最后一段和 Spring `ContentDisposition` 实现修复，运行 focused 测试、starter 全量测试、Maven 编译和代码审查，完成依赖升级说明。
