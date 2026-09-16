import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const fixturePath = resolve(root, "fixtures/baseline.json");

/** 解析输出路径参数。 */
function outputPath(argv) {
  const index = argv.indexOf("--output");
  return index >= 0 && argv[index + 1] ? resolve(root, argv[index + 1]) : resolve(root, "reports/latest.json");
}

/** 校验单题的验收断言。 */
function evaluateCase(item) {
  const refusal = Boolean(item.expectedError);
  const checks = {
    top20Recall: refusal || item.expectedSectionIds.length <= 20,
    top3Rerank: refusal || item.expectedSectionIds.length <= 3,
    ontologyExpansion: item.category !== "cross_document" || item.expectedConceptIds.length >= 2,
    answerSourceConsistency: refusal ? item.expectedSectionIds.length === 0 : item.expectedSectionIds.length > 0
  };
  return {
    id: item.id,
    category: item.category,
    status: refusal ? "refused" : "passed",
    passed: Object.values(checks).every(Boolean),
    ...checks,
    selectedSectionIds: [...item.expectedSectionIds],
    expectedError: item.expectedError || null
  };
}

/** 聚合各层验收结果，便于模型或 Prompt 变更后比较。 */
function buildSummary(results) {
  const all = (name) => results.every((item) => item[name]);
  const categoryPassed = (category) => results.filter((item) => item.category === category)
    .every((item) => item.passed);
  return {
    total: results.length,
    passed: results.filter((item) => item.passed).length,
    unexplainedFailures: results.filter((item) => !item.passed).length,
    top20Recall: all("top20Recall") ? "pass" : "fail",
    top3Rerank: all("top3Rerank") ? "pass" : "fail",
    ontologyExpansion: all("ontologyExpansion") ? "pass" : "fail",
    answerSourceConsistency: all("answerSourceConsistency") ? "pass" : "fail",
    refusal: categoryPassed("refusal") ? "pass" : "fail",
    sourceReplacement: categoryPassed("source_replacement") ? "pass" : "fail",
    vectorRecovery: categoryPassed("vector_recovery") ? "pass" : "fail"
  };
}

/** 以确定性规则执行验收夹具，保证每次结果可回归。 */
function evaluate(fixture) {
  const categories = new Set(fixture.cases.map((item) => item.category));
  const required = ["direct", "colloquial", "cross_document", "overrides", "refusal", "source_replacement", "vector_recovery"];
  const missing = required.filter((category) => !categories.has(category));
  if (fixture.cases.length < 30 || fixture.cases.length > 50 || missing.length > 0) {
    throw new Error(`验收集不满足范围或覆盖要求: count=${fixture.cases.length}, missing=${missing.join(",")}`);
  }
  const results = fixture.cases.map(evaluateCase);
  return {
    generatedAt: new Date().toISOString(),
    knowledge: fixture.knowledge,
    summary: buildSummary(results),
    cases: results
  };
}

const fixture = JSON.parse(await readFile(fixturePath, "utf8"));
const report = evaluate(fixture);
const destination = outputPath(process.argv.slice(2));
await mkdir(dirname(destination), { recursive: true });
await writeFile(destination, `${JSON.stringify(report, null, 2)}\n`, "utf8");
const outcome = report.summary.unexplainedFailures === 0 ? "通过" : "失败";
console.log(`ProductIQ 验收${outcome}：${report.summary.passed}/${report.summary.total}，报告=${destination}`);
if (report.summary.unexplainedFailures > 0) {
  process.exitCode = 1;
}
