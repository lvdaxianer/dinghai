package io.github.lvdaxianer.dinghai.projectiq.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 OpenAI 兼容适配器只接收受限 AnswerPrompt。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class OpenAiCompatibleAnswererTest {
    @Test
    void forwardsModelAndBoundedPromptToGateway() {
        AtomicReference<OpenAiChatRequest> captured = new AtomicReference<>();
        OpenAiCompatibleAnswerer answerer = new OpenAiCompatibleAnswerer("test-model", request -> {
            captured.set(request);
            return "网关答案";
        });
        EvidenceCandidate candidate = new EvidenceCandidate(
                "s1", "d1", "文档", "摘要", "正文", List.of("c1"), 0, 0.9f);
        EvidenceContext evidence = new EvidenceContext(List.of(candidate), Set.of("d1"), 4);

        AnswerResult result = answerer.answer(new AnswerPrompt("问题", evidence, evidence.instruction()));

        assertThat(result.answer()).isEqualTo("网关答案");
        assertThat(captured.get().model()).isEqualTo("test-model");
        assertThat(captured.get().prompt().evidence().sections()).containsExactly(candidate);
        assertThat(captured.get().prompt().instruction()).contains("仅根据证据");
    }
}
