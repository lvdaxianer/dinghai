package io.github.lvdaxianer.projectiq.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证受限重排、证据上下文和稳定拒答行为。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class AnsweringServiceTest {
    @Test
    void ignoresUnknownIdsAndAppliesSectionAndDocumentLimits() {
        RerankerGateway gateway = (question, candidates) -> List.of(
                "unknown", "s6", "s5", "s4", "s3", "s2", "s1", "s6");
        StructuredReranker reranker = new StructuredReranker(gateway);
        List<EvidenceCandidate> candidates = List.of(
                candidate("s1", "d1", 1, 1_500), candidate("s2", "d1", 1, 1_500),
                candidate("s3", "d2", 1, 1_500), candidate("s4", "d2", 1, 1_500),
                candidate("s5", "d3", 1, 1_500), candidate("s6", "d4", 1, 1_500));

        RerankResult result = reranker.rerank("如何配置", candidates);
        EvidenceContext context = new EvidenceContextBuilder().build(candidates, result.sectionIds());

        assertThat(result.sectionIds()).containsExactly("s6", "s5", "s4", "s3");
        assertThat(context.sections()).hasSize(4);
        assertThat(context.documentIds()).containsExactlyInAnyOrder("d4", "d3", "d2");
        assertThat(context.characterCount()).isLessThanOrEqualTo(8_000);
    }

    @Test
    void refusesWithoutCallingAnswererWhenIndexIsUpdatingOrNoRelevantCandidate() {
        AtomicInteger calls = new AtomicInteger();
        Answerer answerer = prompt -> {
            calls.incrementAndGet();
            return new AnswerResult("不应调用", "fake");
        };
        AnsweringService service = new AnsweringService(
                new StructuredReranker((question, candidates) -> candidates.stream()
                        .map(RerankCandidate::sectionId).toList()), answerer);

        AnswerResponse updating = service.answer(new AnswerRequest(
                "问题", KnowledgeState.INDEX_UPDATING, List.of()));
        AnswerResponse noRelevant = service.answer(new AnswerRequest(
                "问题", KnowledgeState.READY, List.of()));

        assertThat(updating.status()).isEqualTo(AnswerStatus.KNOWLEDGE_UPDATING);
        assertThat(noRelevant.status()).isEqualTo(AnswerStatus.NO_RELEVANT_KNOWLEDGE);
        assertThat(calls).hasValue(0);
    }

    @Test
    void refusesWhenNoKnowledgeExists() {
        AtomicInteger calls = new AtomicInteger();
        AnsweringService service = new AnsweringService(
                new StructuredReranker((question, candidates) -> List.of()),
                prompt -> {
                    calls.incrementAndGet();
                    return new AnswerResult("不应调用", "fake");
                });

        AnswerResponse response = service.answer(new AnswerRequest(
                "问题", KnowledgeState.NO_KNOWLEDGE, List.of()));

        assertThat(response.status()).isEqualTo(AnswerStatus.NO_KNOWLEDGE);
        assertThat(calls).hasValue(0);
    }

    @Test
    void deterministicAnswererReceivesOnlyBoundedEvidenceAndRefusalInstruction() {
        EvidenceCandidate candidate = candidate("s1", "d1", 0, 100);
        CapturingAnswerer answerer = new CapturingAnswerer();
        AnsweringService service = new AnsweringService(
                new StructuredReranker((question, candidates) -> List.of("s1")), answerer);

        AnswerResponse response = service.answer(new AnswerRequest(
                "如何配置", KnowledgeState.READY, List.of(candidate)));

        assertThat(response.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(answerer.prompt.question()).isEqualTo("如何配置");
        assertThat(answerer.prompt.evidence().sections()).hasSize(1);
        assertThat(answerer.prompt.instruction()).contains("仅根据证据");
    }

    @Test
    void keepsThirdAndFourthHopCandidatesOutOfAnswererEvidence() {
        EvidenceCandidate remote = candidate("remote", "d9", 3, 10);
        EvidenceContext context = new EvidenceContextBuilder().build(List.of(remote), List.of("remote"));

        assertThat(context.sections()).isEmpty();
    }

    private static EvidenceCandidate candidate(String sectionId, String documentId, int hop, int chars) {
        return new EvidenceCandidate(sectionId, documentId, "doc-" + documentId,
                "摘要-" + sectionId, "x".repeat(chars), List.of("concept-" + sectionId), hop, 0.8f);
    }

    private static final class CapturingAnswerer implements Answerer {
        private AnswerPrompt prompt;

        @Override
        public AnswerResult answer(AnswerPrompt prompt) {
            this.prompt = prompt;
            return new AnswerResult("已回答", "fake");
        }
    }
}
