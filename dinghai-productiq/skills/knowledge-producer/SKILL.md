---
name: knowledge-producer
description: 从产品 Markdown 生成或修复完整知识包候选。
---

# Knowledge Producer

先读取 knowledge-loop-protocol。输入包含 source、sourceHash、graphContext、
previousKnowledge，以及 repair 模式下的 checkerVerdict。

## create 模式

1. 按 Markdown 标题提取可独立回答的 sections。
2. 绑定概念、概念类型、别名和带原文 quote 的 relations。
3. 优先复用 graphContext 中语义等价的 conceptId。
4. 仅抽取原文能证明的关系，并为每条关系给出 quote。

## repair 模式

逐项修复 Checker 中所有 severity=error 问题，在完整候选包中返回修复结果，
并列出 resolvedIssueIds；禁止只输出补丁。

禁止使用原文外产品事实、跨文档伪造证据、发明协议外关系类型或把候选直接
写入可信目录。输出 candidate.json 与 CANDIDATE_READY，包含 sourceHash、
candidateHash、candidatePath 和 resolvedIssueIds。
