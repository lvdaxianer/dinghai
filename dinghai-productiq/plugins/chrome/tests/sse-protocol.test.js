import assert from "node:assert/strict";
import test from "node:test";

import {
  buildStreamUrl,
  parseSsePayload,
  sanitizeErrorCode
} from "../lib/sse-protocol.js";
import { normalizeSettings } from "../lib/settings.js";

test("buildStreamUrl appends the chat endpoint to a configured backend base URL", () => {
  assert.equal(
    buildStreamUrl("https://product.example.internal/productiq"),
    "https://product.example.internal/productiq/api/chat/stream"
  );
});

test("buildStreamUrl rejects non-HTTP backend URLs", () => {
  assert.throws(() => buildStreamUrl("chrome://settings"), /HTTP/);
});

test("parseSsePayload preserves public answer events and ignores comments", () => {
  const events = parseSsePayload(
    ": heartbeat\n\n"
      + "event: delta\n"
      + "data: {\"text\":\"可以重新邀请。\"}\n\n"
      + "event: sources\n"
      + "data: {\"sources\":[{\"documentTitle\":\"团队成员管理\",\"sectionTitle\":\"邀请链接失效\",\"sectionId\":\"secret\"}]}\n\n"
  );

  assert.deepEqual(events, [
    { name: "delta", data: { text: "可以重新邀请。" } },
    {
      name: "sources",
      data: {
        sources: [{ documentTitle: "团队成员管理", sectionTitle: "邀请链接失效" }]
      }
    }
  ]);
});

test("parseSsePayload rejects malformed JSON rather than rendering raw transport data", () => {
  assert.throws(
    () => parseSsePayload("event: delta\ndata: not-json\n\n"),
    /SSE JSON/
  );
});

test("sanitizeErrorCode exposes only documented recoverable states", () => {
  assert.equal(sanitizeErrorCode("no_knowledge"), "no_knowledge");
  assert.equal(sanitizeErrorCode("internal_stack_trace"), "answer_failed");
});

test("normalizeSettings accepts only HTTP(S) endpoints and bounded client keys", () => {
  assert.deepEqual(normalizeSettings({ baseUrl: "https://a.example/", clientKey: "client-1" }), {
    baseUrl: "https://a.example",
    clientKey: "client-1"
  });
  assert.throws(() => normalizeSettings({ baseUrl: "file:///tmp/backend" }), /HTTP/);
  assert.throws(() => normalizeSettings({ baseUrl: "https://a.example", clientKey: "line\nfeed" }), /格式/);
});
