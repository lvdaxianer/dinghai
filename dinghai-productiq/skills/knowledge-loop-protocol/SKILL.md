---
name: knowledge-loop-protocol
description: 共享产品知识加工的文件、JSON、概念、本体关系和判定协议。
---

# Knowledge Loop Protocol

使用同目录 ../references/knowledge-package.schema.json 和
../references/checker-verdict.schema.json 作为结构化契约。

- 可信目录中的知识必须由 A.md 和 A.knowledge.json 配对组成。
- SHA-256 基于 Markdown 原始 UTF-8 内容计算，格式为
  sha256:<64 位小写十六进制>。
- 概念类型仅为 feature、action、entity、state、rule、exception、role。
- 关系类型仅为 parent_of、has_part、creates、requires、governed_by、
  exception_of、overrides、related_to。
- 关系 quote 必须逐字出现在同一知识包所指 section.content 中。
- canonicalAliases 必须来自原文；queryAliases 只能表达不引入新事实的
  口语同义词。
- operation 仅为 add、retain、retire。
