import { loadSettings, saveSettings } from "./lib/settings.js";

/**
 * Options 页面，仅管理后端地址与可撤销的轻量客户端键。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */

const form = document.querySelector("#settings-form");
const baseUrl = document.querySelector("#base-url");
const clientKey = document.querySelector("#client-key");
const status = document.querySelector("#settings-status");

form.addEventListener("submit", handleSubmit);
loadSettings().then((settings) => {
  baseUrl.value = settings.baseUrl;
  clientKey.value = settings.clientKey;
}).catch(() => {
  status.textContent = "无法读取设置，请重新输入。";
});

/**
 * 校验并保存连接设置，失败时保留用户输入。
 *
 * @param {SubmitEvent} event 表单提交事件
 * @returns {Promise<void>} 保存结果
 */
async function handleSubmit(event) {
  event.preventDefault();
  try {
    await saveSettings({ baseUrl: baseUrl.value, clientKey: clientKey.value });
    status.textContent = "设置已保存。";
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : "设置无效，请检查后重试。";
  }
}
