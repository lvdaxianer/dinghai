---
name: knowledge-orchestrator
description: 调度 Producer 与 Checker，将指定 Markdown 文件或目录加工为可信产品知识包。
---

# Knowledge Orchestrator

先读取 knowledge-loop-protocol。输入可以是一个 .md 文件、包含 Markdown 的
目录，或显式 --remove A.md 请求。可信输出目录由部署配置固定。

目录模式递归发现 Markdown，按文件名字典序逐份处理。处理 A.md 时，从可信目录
聚合 graphContext；更新 A 时排除旧 A.knowledge.json，但将其作为 previousKnowledge。

## General Loop

1. 固化 sourceHash，构建 graphContext 和 previousKnowledge。
2. 调 Producer(create)。
3. 检查 candidate 文件存在、完整且 candidateHash 可计算。
4. 调独立 Checker。
5. PASS 且 sourceHash、candidateHash、Schema 一致时，原子写入 A.md 与
   A.knowledge.json。
6. FAIL 时把 verdict 原样交回同一个 Producer(repair)，最多五轮。
7. ERROR、源文件变化或超过五轮时失败退出，保留旧可信包。

总控不得改写候选内容或 Checker 结论。缺失于本轮目录扫描不等于删除；删除只
能由显式 remove 请求触发。最终输出成功/失败文件、轮次、sections、concepts、
relations 和失败原因。
