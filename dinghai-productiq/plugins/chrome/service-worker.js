import { buildStreamUrl, parseSsePayload, sanitizeErrorCode } from "./lib/sse-protocol.js";
import { loadSettings } from "./lib/settings.js";

/**
 * Chrome MV3 后台流代理：集中管理后端请求、SSE 解析和 AbortController。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */

const activeStreams = new Map();
const MESSAGE_START = "productiq.chat.start";
const MESSAGE_CANCEL = "productiq.chat.cancel";
const MESSAGE_EVENT = "productiq.chat.event";

chrome.runtime.onMessage.addListener((message, sendResponse) => {
  if (message?.type === MESSAGE_START) {
    startStream(message.streamId, message.question).catch(() => undefined);
    sendResponse({ accepted: true });
    return false;
  }
  if (message?.type === MESSAGE_CANCEL) {
    cancelStream(message.streamId);
    sendResponse({ accepted: true });
    return false;
  }
  return false;
});

/**
 * 校验请求并在后台开始一条可取消的 SSE 流。
 *
 * @param {string} streamId 流标识
 * @param {string} question 用户问题
 * @returns {Promise<void>} 流完成结果
 */
async function startStream(streamId, question) {
  const request = normalizeStartRequest(streamId, question);
  if (!request) {
    postEvent(String(streamId || "").trim(), { name: "error", data: { code: "answer_failed" } });
    return;
  }
  const controller = new AbortController();
  activeStreams.set(request.streamId, controller);
  try {
    const settings = await loadSettings();
    const response = await fetchStream(settings, request, controller);
    await readStream(request.streamId, response.body, controller.signal);
  } catch (error) {
    if (!controller.signal.aborted) {
      postEvent(request.streamId, { name: "error", data: { code: "answer_failed" } });
    }
  } finally {
    activeStreams.delete(request.streamId);
  }
}

/** 规范化启动消息并阻止重复流。 */
function normalizeStartRequest(streamId, question) {
  const normalizedId = String(streamId || "").trim();
  const normalizedQuestion = String(question || "").trim();
  if (!normalizedId || !normalizedQuestion || activeStreams.has(normalizedId)) {
    return undefined;
  }
  return { streamId: normalizedId, question: normalizedQuestion };
}

/** 请求聊天 SSE，并要求响应具有可读取的流体。 */
async function fetchStream(settings, request, controller) {
  const response = await fetch(buildStreamUrl(settings.baseUrl),
    requestOptions(settings, request.question, controller));
  if (!response.ok || !response.body) {
    throw new Error(`聊天服务 HTTP ${response.status}`);
  }
  return response;
}

/**
 * 构造仅访问聊天流的 POST 请求，模型密钥不会进入插件。
 *
 * @param {{clientKey: string}} settings 插件设置
 * @param {string} question 已校验问题
 * @param {AbortController} controller 取消控制器
 * @returns {RequestInit} fetch 请求选项
 */
function requestOptions(settings, question, controller) {
  const headers = {
    Accept: "text/event-stream",
    "Content-Type": "application/json"
  };
  if (settings.clientKey) {
    headers["X-Client-Key"] = settings.clientKey;
  }
  return {
    method: "POST",
    headers,
    body: JSON.stringify({ question }),
    signal: controller.signal
  };
}

/**
 * 增量读取 SSE，保留未完成帧并在流结束后丢弃不完整传输。
 *
 * @param {string} streamId 流标识
 * @param {ReadableStream} body 后端响应体
 * @param {AbortSignal} signal 取消信号
 * @returns {Promise<void>} 读取结果
 */
async function readStream(streamId, body, signal) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (!signal.aborted) {
    const result = await reader.read();
    if (result.done) {
      break;
    }
    buffer += decoder.decode(result.value, { stream: true });
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() || "";
    frames.flatMap(parseSsePayload).forEach((event) => postEvent(streamId, event));
  }
  buffer += decoder.decode();
  if (buffer.trim() && !signal.aborted) {
    parseSsePayload(`${buffer}\n\n`).forEach((event) => postEvent(streamId, event));
  }
}

/**
 * 取消指定流并中断 fetch 与响应读取。
 *
 * @param {string} streamId 流标识
 * @returns {void}
 */
function cancelStream(streamId) {
  const controller = activeStreams.get(String(streamId || ""));
  if (controller) {
    controller.abort();
  }
}

/**
 * 将公开事件投递回 Popup；不存在接收方时静默处理生命周期竞态。
 *
 * @param {string} streamId 流标识
 * @param {{name: string, data: object}} event 公开事件
 * @returns {void}
 */
function postEvent(streamId, event) {
  const safeEvent = event.name === "error"
    ? { name: "error", data: { code: sanitizeErrorCode(event.data?.code) } }
    : event;
  chrome.runtime.sendMessage({ type: MESSAGE_EVENT, streamId, event: safeEvent }).catch(() => undefined);
}
