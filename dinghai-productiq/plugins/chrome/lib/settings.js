/**
 * 插件设置的本地校验与 Chrome storage 适配。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */

export const DEFAULT_SETTINGS = Object.freeze({
  baseUrl: "http://localhost:8080",
  clientKey: ""
});

/**
 * 从 Chrome storage 读取设置并补齐默认值。
 *
 * @returns {Promise<{baseUrl: string, clientKey: string}>} 设置
 */
export async function loadSettings() {
  const stored = await chrome.storage.local.get(DEFAULT_SETTINGS);
  return normalizeSettings(stored);
}

/**
 * 保存经过校验的插件设置。
 *
 * @param {{baseUrl: string, clientKey: string}} settings 待保存设置
 * @returns {Promise<void>} 保存结果
 */
export async function saveSettings(settings) {
  const normalized = normalizeSettings(settings);
  await chrome.storage.local.set(normalized);
}

/**
 * 校验和规范化用户输入，避免把非法地址交给 Worker。
 *
 * @param {Partial<{baseUrl: string, clientKey: string}>} settings 输入设置
 * @returns {{baseUrl: string, clientKey: string}} 规范设置
 */
export function normalizeSettings(settings = {}) {
  const baseUrl = String(settings.baseUrl || DEFAULT_SETTINGS.baseUrl).trim().replace(/\/+$/, "");
  const clientKey = String(settings.clientKey || "").trim();
  let parsed;
  try {
    parsed = new URL(baseUrl);
  } catch (error) {
    throw new Error("后端地址必须是 HTTP 或 HTTPS URL", { cause: error });
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error("后端地址必须是 HTTP 或 HTTPS URL");
  }
  if (clientKey.length > 256 || /[\r\n]/.test(clientKey)) {
    throw new Error("客户端密钥格式无效");
  }
  return { baseUrl, clientKey };
}
