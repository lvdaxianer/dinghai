package io.github.lvdaxianer.dinghai.projectiq.chat;

import java.util.Objects;
import java.util.Optional;

/**
 * 编排知识状态、重排、证据边界和 Answerer 调用。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class AnsweringService {
    private final StructuredReranker reranker;
    private final EvidenceContextBuilder contextBuilder;
    private final Answerer answerer;

    /** 创建回答服务。 */
    public AnsweringService(StructuredReranker reranker, Answerer answerer) {
        this.reranker = Objects.requireNonNull(reranker, "reranker");
        this.contextBuilder = new EvidenceContextBuilder();
        this.answerer = Objects.requireNonNull(answerer, "answerer");
    }

    /** 在调用下游模型前处理稳定拒答状态。 */
    public AnswerResponse answer(AnswerRequest request) {
        Optional<AnswerResponse> refusal = refusalFor(request);
        if (refusal.isPresent()) {
            return refusal.orElseThrow();
        }
        try {
            return answerWithEvidence(request);
        } catch (RuntimeException exception) {
            return AnswerResponse.refusal(AnswerStatus.ANSWER_FAILED);
        }
    }

    /** 先重排和限制证据，再调用 Answerer。 */
    private AnswerResponse answerWithEvidence(AnswerRequest request) {
        RerankResult rerank = reranker.rerank(request.question(), request.candidates());
        EvidenceContext evidence = contextBuilder.build(request.candidates(), rerank.sectionIds());
        if (evidence.sections().isEmpty()) {
            return AnswerResponse.refusal(AnswerStatus.NO_RELEVANT_KNOWLEDGE);
        }
        AnswerResult result = answerer.answer(new AnswerPrompt(
                request.question(), evidence, evidence.instruction()));
        return new AnswerResponse(AnswerStatus.ANSWERED, result.answer(), result.modelId(), evidence);
    }

    /** 处理没有知识、索引更新和无候选三种无需调用模型的路径。 */
    private Optional<AnswerResponse> refusalFor(AnswerRequest request) {
        if (request.knowledgeState() == KnowledgeState.NO_KNOWLEDGE) {
            return Optional.of(AnswerResponse.refusal(AnswerStatus.NO_KNOWLEDGE));
        }
        if (request.knowledgeState() == KnowledgeState.INDEX_UPDATING) {
            return Optional.of(AnswerResponse.refusal(AnswerStatus.KNOWLEDGE_UPDATING));
        }
        if (request.candidates().isEmpty()) {
            return Optional.of(AnswerResponse.refusal(AnswerStatus.NO_RELEVANT_KNOWLEDGE));
        }
        return Optional.empty();
    }
}
