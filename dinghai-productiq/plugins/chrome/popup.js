import { sanitizeErrorCode } from "./lib/sse-protocol.js";

/**
 * Popup 交互控制器：提交问题、显示受限事件并提供取消入口。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */

const MESSAGE_START = "productiq.chat.start";
const MESSAGE_CANCEL = "productiq.chat.cancel";
const MESSAGE_EVENT = "productiq.chat.event";
const elements = {
  form: document.querySelector("#question-form"),
  question: document.querySelector("#question"),
  submit: document.querySelector("#submit-button"),
  cancel: document.querySelector("#cancel-button"),
  status: document.querySelector("#status"),
  answer: document.querySelector("#answer"),
  sources: document.querySelector("#sources")
};
let activeStreamId = "";

elements.form.addEventListener("submit", submitQuestion);
elements.cancel.addEventListener("click", cancelQuestion);
chrome.runtime.onMessage.addListener(handleStreamEvent);

/**
 * 校验本地问题并启动后台流，重复提交被禁用直到终态。
 *
 * @param {SubmitEvent} event 表单事件
 * @returns {Promise<void>} 处理结果
 */
async function submitQuestion(event) {
  event.preventDefault();
  const question = elements.question.value.trim();
  if (!question || activeStreamId) {
    elements.status.textContent = "请输入问题。";
    return;
  }
  activeStreamId = crypto.randomUUID();
  elements.answer.textContent = "";
  elements.sources.replaceChildren();
  setBusy(true, "正在检索产品资料…");
  try {
    await chrome.runtime.sendMessage({ type: MESSAGE_START, streamId: activeStreamId, question });
  } catch (error) {
    finishWithError("answer_failed", error);
  }
}

/**
 * 取消当前请求并立即恢复可提问状态。
 *
 * @returns {Promise<void>} 取消结果
 */
async function cancelQuestion() {
  if (!activeStreamId) {
    return;
  }
  const streamId = activeStreamId;
  activeStreamId = "";
  try {
    await chrome.runtime.sendMessage({ type: MESSAGE_CANCEL, streamId });
  } finally {
    setBusy(false, "已取消，可以继续提问。");
  }
}

/**
 * 接收 Worker 的公开事件，只处理当前 Popup 创建的流。
 *
 * @param {{type: string, streamId: string, event: {name: string, data: object}}} message 消息
 * @returns {void}
 */
function handleStreamEvent(message) {
  if (message?.type !== MESSAGE_EVENT || message.streamId !== activeStreamId) {
    return;
  }
  const event = message.event;
  if (event.name === "delta") {
    elements.answer.textContent += event.data?.text || "";
  } else if (event.name === "sources") {
    renderSources(event.data?.sources);
  } else if (event.name === "done") {
    finish("回答完成。");
  } else if (event.name === "error") {
    finishWithError(event.data?.code);
  }
}

/**
 * 渲染公开来源名称，不把内部 ID、引文和诊断信息写入 DOM。
 *
 * @param {Array<{documentTitle: string, sectionTitle: string}>} sources 来源
 * @returns {void}
 */
function renderSources(sources) {
  elements.sources.replaceChildren();
  (Array.isArray(sources) ? sources : []).forEach((source) => {
    const item = document.createElement("li");
    const documentTitle = source?.documentTitle || "未命名文档";
    const sectionTitle = source?.sectionTitle || "未命名章节";
    item.textContent = `${documentTitle} / ${sectionTitle}`;
    elements.sources.append(item);
  });
}

/**
 * 结束流并保留回答内容。
 *
 * @param {string} message 状态文案
 * @returns {void}
 */
function finish(message) {
  activeStreamId = "";
  setBusy(false, message);
}

/**
 * 结束流并将稳定错误码转换为用户文案。
 *
 * @param {string} code 错误码
 * @param {unknown} error 传输错误
 * @returns {void}
 */
function finishWithError(code, error) {
  const safeCode = sanitizeErrorCode(code);
  const messages = {
    no_knowledge: "当前产品资料未覆盖这个问题。",
    knowledge_updating: "产品资料正在更新，请稍后重试。",
    no_relevant_knowledge: "已检索相关资料，但没有找到可确认的答案。",
    answer_failed: "暂时无法生成回答，请稍后重试。"
  };
  if (error) {
    console.debug("产品知识流传输失败", error instanceof Error ? error.name : "unknown");
  }
  finish(messages[safeCode]);
}

/**
 * 统一切换提交、取消和状态反馈。
 *
 * @param {boolean} busy 是否正在请求
 * @param {string} message 状态文案
 * @returns {void}
 */
function setBusy(busy, message) {
  elements.submit.disabled = busy;
  elements.cancel.hidden = !busy;
  elements.question.disabled = busy;
  elements.status.textContent = message;
}
