package io.github.lvdaxianer.projectiq.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证聊天 SSE HTTP 契约的输入校验。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class ChatStreamControllerTest {
    /** 验证空白 JSON 问题在调度下游前返回客户端错误。 */
    @Test
    void rejectsBlankQuestion() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ChatStreamController(
                new ChatStreamService(question -> AnswerResponse.refusal(AnswerStatus.NO_KNOWLEDGE),
                        Duration.ofSeconds(1)))).build();

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    /** 验证成功响应可被客户端按 delta、sources、done 顺序消费。 */
    @Test
    void emitsStableSseEventsForAnsweredQuestion() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ChatStreamController(
                new ChatStreamService(question -> new AnswerResponse(AnswerStatus.ANSWERED, "答案", "test", emptyEvidence()),
                        Duration.ofSeconds(1)))).build();

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"如何配置\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).containsSubsequence("event:delta", "event:sources", "event:done");
    }

    /** 创建不带来源的最小成功证据。 */
    private static EvidenceContext emptyEvidence() {
        return new EvidenceContext(java.util.List.of(), java.util.Set.of(), 0);
    }
}
