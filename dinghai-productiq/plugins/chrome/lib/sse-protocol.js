/**
 * Chrome 插件的后端 SSE 协议工具，只保留公开事件字段。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */

const STREAM_PATH = "/api/chat/stream";
const DEFAULT_EVENT = "message";
const ERROR_CODES = new Set([
  "no_knowledge",
  "knowledge_updating",
  "no_relevant_knowledge",
  "answer_failed"
]);

/**
 * 根据用户配置生成唯一的聊天端点。
 *
 * @param {string} baseUrl 后端基础地址
 * @returns {string} SSE 地址
 */
export function buildStreamUrl(baseUrl) {
  const normalized = String(baseUrl || "").trim();
  let parsed;
  try {
    parsed = new URL(normalized);
  } catch (error) {
    throw new Error("后端地址必须是 HTTP 或 HTTPS URL", { cause: error });
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error("后端地址必须是 HTTP 或 HTTPS URL");
  }
  parsed.pathname = `${parsed.pathname.replace(/\/+$/, "")}${STREAM_PATH}`;
  parsed.search = "";
  parsed.hash = "";
  return parsed.toString();
}

/**
 * 解析一个或多个完整 SSE 帧，并忽略注释帧。
 *
 * @param {string} payload SSE 文本
 * @returns {Array<{name: string, data: object}>} 公开事件
 */
export function parseSsePayload(payload) {
  return String(payload || "")
    .split(/\r?\n\r?\n/)
    .map(parseFrame)
    .filter(Boolean);
}

/**
 * 将稳定错误码限制在插件可显示的集合内。
 *
 * @param {string} code 服务端错误码
 * @returns {string} 稳定错误码
 */
export function sanitizeErrorCode(code) {
  return ERROR_CODES.has(code) ? code : "answer_failed";
}

/**
 * 解析单个 SSE 帧，禁止将未知字段转给用户界面。
 *
 * @param {string} frame SSE 帧
 * @returns {{name: string, data: object}|undefined} 公开事件
 */
function parseFrame(frame) {
  const lines = frame.split(/\r?\n/);
  const fields = lines.filter((line) => !line.startsWith(":"));
  const dataLines = fields
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart());
  if (dataLines.length === 0) {
    return undefined;
  }
  let data;
  try {
    data = JSON.parse(dataLines.join("\n"));
  } catch (error) {
    throw new Error("SSE JSON 无效", { cause: error });
  }
  const name = fields.find((line) => line.startsWith("event:"))?.slice(6).trim()
    || DEFAULT_EVENT;
  return { name, data: safeEventData(name, data) };
}

/**
 * 只复制允许进入 Popup 的回答、来源和错误字段。
 *
 * @param {string} name 事件名称
 * @param {unknown} data 原始 JSON
 * @returns {object} 公开事件数据
 */
function safeEventData(name, data) {
  if (name === "delta") {
    return { text: typeof data?.text === "string" ? data.text : "" };
  }
  if (name === "sources") {
    return {
      sources: Array.isArray(data?.sources) ? data.sources.map(publicSource) : []
    };
  }
  if (name === "error") {
    return { code: sanitizeErrorCode(data?.code) };
  }
  return {};
}

/**
 * 将来源转换成只含文档名和章节名的展示模型。
 *
 * @param {unknown} source 后端来源
 * @returns {{documentTitle: string, sectionTitle: string}} 公开来源
 */
function publicSource(source) {
  return {
    documentTitle: typeof source?.documentTitle === "string" ? source.documentTitle : "",
    sectionTitle: typeof source?.sectionTitle === "string" ? source.sectionTitle : ""
  };
}
