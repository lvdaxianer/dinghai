import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));

test("baseline has 30-50 cases and every required category", async () => {
  const fixture = JSON.parse(await readFile(join(root, "../fixtures/baseline.json"), "utf8"));
  assert.ok(fixture.cases.length >= 30 && fixture.cases.length <= 50);
  assert.deepEqual(
    [...new Set(fixture.cases.map((item) => item.category))].sort(),
    ["colloquial", "cross_document", "direct", "overrides", "refusal", "source_replacement", "vector_recovery"]
  );
  for (const item of fixture.cases) {
    assert.ok(item.question);
    assert.ok(Array.isArray(item.expectedSectionIds));
    assert.ok(Array.isArray(item.expectedConceptIds));
    assert.ok(Array.isArray(item.mustNotUseSectionIds));
  }
});

test("operations configuration matches the backend property names", async () => {
  const compose = await readFile(join(root, "../../docker-compose.yml"), "utf8");
  const envExample = await readFile(join(root, "../../.env.example"), "utf8");
  assert.match(compose, /PRODUCTIQ_SQLITE_PATH: \/var\/lib\/productiq\/productiq\.db/);
  assert.match(compose, /PRODUCTIQ_TRUSTED_DIRECTORY: \/var\/lib\/productiq\/knowledge/);
  assert.match(compose, /PRODUCTIQ_CHAT_TIMEOUT:/);
  assert.match(envExample, /^PRODUCTIQ_ADMIN_KEY=replace-at-deploy-time$/m);
  assert.doesNotMatch(compose, /PRODUCTIQ_SQLITE_URL|PRODUCTIQ_KNOWLEDGE_ROOT/);
});
