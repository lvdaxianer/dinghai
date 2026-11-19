# Align MetricView DSL Specification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Dinghai relational-query v1 the canonical DSL contract consumed by the MetricView lifecycle skill, with an explicit JSON Schema and synchronized skill references.

**Architecture:** Keep the public DSL contract in `dinghai-dsl-engine/docs`, including a machine-readable v1 schema. Copy that contract into the skill references and add only MetricView-specific binding guidance there; do not move MetricView management semantics into the public engine repository.

**Tech Stack:** Markdown, JSON Schema-compatible JSON, Python standard-library validation scripts, Git worktrees, OpenSpec.

---

### Task 1: Complete the public DSL syntax contract

**Files:**
- Modify: `dinghai-dsl-engine/docs/dsl-syntax.md`

- [x] **Step 1: Add the generator-facing compatibility section** with the canonical payload names (`from`, `select`, `joins`, `where`, `groupBy`, `orderBy`, `limit`), current operator set, and explicit rejection of unsupported `not_in` and `between`; verify every claimed field/operator exists in the existing examples and tables.
- [x] **Step 2: Add temporal window semantics** stating that generated interval filters use `gte(start)` plus `lt(end)`, and distinguish `date`, `local_datetime`, and `instant`; verify the section does not contradict the existing type table.
- [x] **Step 3: Add the schema/version ownership note** describing `relational-query` version 1, unknown-version rejection, and the boundary between structural schema validation and runtime schema-policy validation; verify the document has one consistent version statement.

### Task 2: Add and validate the machine-readable schema

**Files:**
- Create: `dinghai-dsl-engine/docs/schemas/relational-query-v1.schema.json`

- [x] **Step 1: Define the JSON Schema** for the request envelope, payload fields, safe identifiers, joins, projections, predicates, logical groups, order directions, and basic parameter value shapes; verify it rejects unknown top-level/payload keys and accepts the complete documented example.
- [x] **Step 2: Run a standard-library structural check** that loads the JSON, asserts `$schema`, `title`, `dslType`, and `version` metadata, and parses every JSON example extracted or manually mirrored from the document; verify the command exits 0.

### Task 3: Synchronize the MetricView skill references

**Files:**
- Create: `metric-view-lifecycle/references/dsl-syntax.md`
- Create: `metric-view-lifecycle/references/schemas/relational-query-v1.schema.json`
- Create: `metric-view-lifecycle/references/metric-view-dsl-binding.md`
- Modify: `metric-view-lifecycle/SKILL.md`
- Modify: `metric-view-lifecycle/references/type-mapping.md`

- [x] **Step 1: Copy the canonical engine document and schema byte-for-byte** into the skill references; provenance and version are recorded in the binding note so the protocol snapshot remains byte-identical; verify `cmp` passes for both artifacts.
- [x] **Step 2: Add the binding note** covering Python embedding (`{"dsl": <request>}`), route selection, `typeContext` as MetricView evidence, final `returnDef` output mapping, and the fact that `test/save/Mock` remain skill-owned; verify it contains no unsupported DSL node names.
- [x] **Step 3: Update the skill's DSL planning and type mapping text** to use engine names and logical types, map MySQL `DATETIME` to `local_datetime` and `TIMESTAMP` to `instant`, and only claim operators documented by the engine; verify a repository-wide search finds no active generator instruction that treats `sources/dimensions/measures/filters/sort` as v1 fields.
- [x] **Step 4: Align the type compatibility validator and generated fixtures/tests with the engine logical types and supported operators.**

### Task 4: Cross-repository integration verification

**Files:**
- Verify: `dinghai-dsl-engine/docs/dsl-syntax.md`
- Verify: `dinghai-dsl-engine/docs/schemas/relational-query-v1.schema.json`
- Verify: `metric-view-lifecycle/references/dsl-syntax.md`
- Verify: `metric-view-lifecycle/references/schemas/relational-query-v1.schema.json`

- [x] **Step 1: Run documentation consistency checks** for copied files, version metadata, operator names, type mappings, and the `gte`/`lt` convention; verify all checks pass.
- [x] **Step 2: Run the existing local MetricView skill test suite** with the documented `python3 -m unittest discover -s scripts -p 'test_*.py'` command in a clean skill worktree; verify no regression in existing tests.
- [x] **Step 3: Review the final diff** for accidental edits to the user's pre-existing skill changes and verify only the intended documentation/schema/reference files are included.

## Workflow follow-up

- Run `openspec validate align-metric-view-dsl-spec --strict` before completion.
- Complete the per-task review/audit, commit each repository task, merge into each repository's `main`, and remove temporary task worktrees only after verification.
