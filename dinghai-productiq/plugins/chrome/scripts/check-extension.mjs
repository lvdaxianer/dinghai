import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

/**
 * 对 Manifest V3 交付物执行无浏览器依赖的静态验收。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */

const root = join(fileURLToPath(new URL("..", import.meta.url)));
const requiredFiles = [
  "manifest.json",
  "popup.html",
  "popup.js",
  "popup.css",
  "options.html",
  "options.js",
  "service-worker.js",
  "lib/sse-protocol.js",
  "lib/settings.js"
];
const forbiddenTerms = /MODEL_GATEWAY_API_KEY|qdrant|sqlite|documentId|sectionId|distance|modelId/i;

/**
 * 读取 Manifest 并校验 MV3 必需字段。
 *
 * @returns {Promise<void>} 校验结果
 */
async function checkManifest() {
  const manifest = JSON.parse(await readFile(join(root, "manifest.json"), "utf8"));
  assert.equal(manifest.manifest_version, 3);
  assert.equal(manifest.background.service_worker, "service-worker.js");
  assert.equal(manifest.action.default_popup, "popup.html");
  assert.equal(manifest.options_page, "options.html");
  assert.deepEqual(manifest.permissions, ["storage"]);
}

/**
 * 校验 JavaScript 语法与敏感边界。
 *
 * @returns {Promise<void>} 校验结果
 */
async function checkSources() {
  const sourceFiles = requiredFiles.filter((file) => file.endsWith(".js"));
  for (const file of sourceFiles) {
    const source = await readFile(join(root, file), "utf8");
    assert.equal(forbiddenTerms.test(source), false, `${file} 含禁止的内部或密钥字段`);
    const result = spawnSync(process.execPath, ["--check", join(root, file)], { encoding: "utf8" });
    assert.equal(result.status, 0, `${file} 语法错误: ${result.stderr}`);
  }
}

/**
 * 执行全部插件静态检查。
 *
 * @returns {Promise<void>} 检查结果
 */
async function main() {
  await Promise.all(requiredFiles.map(async (file) => {
    await readFile(join(root, file));
  }));
  await checkManifest();
  await checkSources();
  console.log(`Chrome 插件检查通过（${requiredFiles.length} 个交付文件）`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
