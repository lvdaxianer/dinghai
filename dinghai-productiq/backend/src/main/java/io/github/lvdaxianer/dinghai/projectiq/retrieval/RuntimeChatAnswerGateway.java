package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import io.github.lvdaxianer.dinghai.projectiq.chat.AnswerRequest;
import io.github.lvdaxianer.dinghai.projectiq.chat.AnswerResponse;
import io.github.lvdaxianer.dinghai.projectiq.chat.AnswerStatus;
import io.github.lvdaxianer.dinghai.projectiq.chat.AnsweringService;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatAnswerGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatQuestion;
import io.github.lvdaxianer.dinghai.projectiq.chat.EvidenceCandidate;
import io.github.lvdaxianer.dinghai.projectiq.chat.KnowledgeState;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.SectionRecord;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将检索、水合、本体推理和证据回答收敛为一个受限运行时问答网关。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class RuntimeChatAnswerGateway implements ChatAnswerGateway {
    private static final int VECTOR_CANDIDATE_LIMIT = 20;
    private static final int EVIDENCE_CANDIDATE_LIMIT = 32;
    private final KnowledgeStore store;
    private final EmbeddingClient embeddingClient;
    private final VectorIndex vectorIndex;
    private final CurrentCandidateHydrator hydrator;
    private final OntologyReasoner reasoner;
    private final AnsweringService answeringService;

    /**
     * 创建使用 SQLite 事实作为最终可信来源的问答网关。
     *
     * @param store 当前知识仓储
     * @param embeddingClient 问题向量生成器
     * @param vectorIndex 相似章节索引
     * @param hydrator 当前性水合器
     * @param reasoner 有界本体推理器
     * @param answeringService 证据回答服务
     */
    public RuntimeChatAnswerGateway(KnowledgeStore store, EmbeddingClient embeddingClient, VectorIndex vectorIndex,
                                    CurrentCandidateHydrator hydrator, OntologyReasoner reasoner,
                                    AnsweringService answeringService) {
        this.store = Objects.requireNonNull(store, "store");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient");
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
        this.hydrator = Objects.requireNonNull(hydrator, "hydrator");
        this.reasoner = Objects.requireNonNull(reasoner, "reasoner");
        this.answeringService = Objects.requireNonNull(answeringService, "answeringService");
    }

    /**
     * 在稳定拒答边界之后执行有界检索回答。
     *
     * @param question 已校验问题
     * @return 回答或稳定拒答状态
     */
    @Override
    public AnswerResponse answer(ChatQuestion question) {
        try {
            KnowledgeState state = knowledgeState();
            if (state != KnowledgeState.READY) {
                return refusal(state);
            }
            return answeringService.answer(new AnswerRequest(question.text(), KnowledgeState.READY,
                    evidenceCandidates(question.text())));
        } catch (IOException | InterruptedException | SQLException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return AnswerResponse.refusal(AnswerStatus.ANSWER_FAILED);
        }
    }

    /**
     * 判断当前资料和索引是否允许启动下游检索。
     *
     * @return 当前知识状态
     * @throws SQLException SQLite 查询失败
     */
    private KnowledgeState knowledgeState() throws SQLException {
        if (!store.hasDocuments()) {
            return KnowledgeState.NO_KNOWLEDGE;
        }
        return store.hasIncompleteVectorJobs() ? KnowledgeState.INDEX_UPDATING : KnowledgeState.READY;
    }

    /**
     * 将可用状态映射为无需调用下游模型的稳定响应。
     *
     * @param state 当前知识状态
     * @return 稳定拒答响应
     */
    private AnswerResponse refusal(KnowledgeState state) {
        return state == KnowledgeState.NO_KNOWLEDGE
                ? AnswerResponse.refusal(AnswerStatus.NO_KNOWLEDGE)
                : AnswerResponse.refusal(AnswerStatus.KNOWLEDGE_UPDATING);
    }

    /**
     * 从向量召回和本体扩展构建已去重的 SQLite 当前证据候选。
     *
     * @param question 已校验问题文本
     * @return 当前性校验后的候选
     * @throws IOException 向量索引调用失败
     * @throws InterruptedException 当前线程被中断
     * @throws SQLException SQLite 查询失败
     */
    private List<EvidenceCandidate> evidenceCandidates(String question)
            throws IOException, InterruptedException, SQLException {
        List<Float> vector = embeddingClient.embed(question);
        List<HydratedCandidate> hydrated = hydrator.hydrate(vectorIndex.search(vector, VECTOR_CANDIDATE_LIMIT));
        return candidates(hydrated, reasoner.expand(hydrated, store));
    }

    /**
     * 先保留直接向量命中，再补充本体候选，且按章节 ID 去重。
     *
     * @param hydrated 已水合的直接候选
     * @param ontology 有界本体候选
     * @return 去重后的证据候选
     * @throws SQLException SQLite 查询失败
     */
    private List<EvidenceCandidate> candidates(List<HydratedCandidate> hydrated, List<OntologyCandidate> ontology)
            throws SQLException {
        List<EvidenceDescriptor> descriptors = descriptors(hydrated, ontology);
        Map<String, DocumentRecord> documents = store.findDocumentsByIds(documentIds(descriptors));
        Map<String, List<String>> concepts = store.findConceptIdsBySectionIds(sectionIds(descriptors));
        return evidenceCandidates(descriptors, documents, concepts);
    }

    /**
     * 先保留直接命中，再补充本体候选，且严格限制总证据数量。
     *
     * @param hydrated 已水合向量候选
     * @param ontology 有界本体候选
     * @return 按章节去重的证据描述
     */
    private List<EvidenceDescriptor> descriptors(List<HydratedCandidate> hydrated, List<OntologyCandidate> ontology) {
        Map<String, EvidenceDescriptor> descriptors = new LinkedHashMap<>(EVIDENCE_CANDIDATE_LIMIT);
        hydrated.forEach(candidate -> addDirectCandidate(descriptors, candidate));
        ontology.forEach(candidate -> addOntologyCandidate(descriptors, candidate));
        return List.copyOf(descriptors.values());
    }

    /**
     * 加入直接向量命中，并将其保留为零跳高优先级候选。
     *
     * @param descriptors 已加入的证据描述
     * @param candidate 已水合向量候选
     */
    private void addDirectCandidate(Map<String, EvidenceDescriptor> descriptors, HydratedCandidate candidate) {
        if (descriptors.size() < EVIDENCE_CANDIDATE_LIMIT) {
            descriptors.putIfAbsent(candidate.sectionId(), new EvidenceDescriptor(candidate.section(), List.of(), 0,
                    candidate.match().score(), false));
        }
    }

    /**
     * 在存在剩余容量时加入本体候选，直接候选优先。
     *
     * @param descriptors 已加入的证据描述
     * @param candidate 本体候选
     */
    private void addOntologyCandidate(Map<String, EvidenceDescriptor> descriptors, OntologyCandidate candidate) {
        if (descriptors.size() < EVIDENCE_CANDIDATE_LIMIT) {
            descriptors.putIfAbsent(candidate.section().sectionId(), new EvidenceDescriptor(candidate.section(),
                    List.of(candidate.conceptId()), candidate.hop(), 0.0f, true));
        }
    }

    /**
     * 提取有序且去重的文档 ID。
     *
     * @param descriptors 证据描述
     * @return 稳定文档 ID
     */
    private List<String> documentIds(List<EvidenceDescriptor> descriptors) {
        return descriptors.stream().map(descriptor -> descriptor.section().documentId()).distinct().toList();
    }

    /**
     * 提取有序且去重的章节 ID。
     *
     * @param descriptors 证据描述
     * @return 稳定章节 ID
     */
    private List<String> sectionIds(List<EvidenceDescriptor> descriptors) {
        return descriptors.stream().map(descriptor -> descriptor.section().sectionId()).distinct().toList();
    }

    /**
     * 将批量读取的当前事实投影为可回答证据。
     *
     * @param descriptors 有界证据描述
     * @param documents 当前文档映射
     * @param concepts 当前章节概念映射
     * @return 可重排的证据候选
     */
    private List<EvidenceCandidate> evidenceCandidates(List<EvidenceDescriptor> descriptors,
                                                        Map<String, DocumentRecord> documents,
                                                        Map<String, List<String>> concepts) {
        List<EvidenceCandidate> candidates = new ArrayList<>(descriptors.size());
        descriptors.forEach(descriptor -> candidates.add(evidenceCandidate(descriptor, documents, concepts)));
        return List.copyOf(candidates);
    }

    /**
     * 创建一条完整的当前事实证据，缺失文档元数据时拒绝整个回答。
     *
     * @param descriptor 证据描述
     * @param documents 当前文档映射
     * @param concepts 当前章节概念映射
     * @return 可重排的证据候选
     */
    private EvidenceCandidate evidenceCandidate(EvidenceDescriptor descriptor, Map<String, DocumentRecord> documents,
                                                Map<String, List<String>> concepts) {
        SectionRecord section = descriptor.section();
        DocumentRecord document = documents.get(section.documentId());
        if (document == null) throw new IllegalStateException("SQLite 当前文档不存在");
        List<String> conceptIds = descriptor.fromOntology() ? descriptor.conceptIds()
                : concepts.getOrDefault(section.sectionId(), List.of());
        return new EvidenceCandidate(section.sectionId(), section.documentId(), document.title(), section.summary(),
                section.content(), conceptIds, descriptor.hop(), descriptor.score(), section.headingPathJson());
    }

    /**
     * 记录在批量事实读取前已经确定的证据优先级和本体跳数。
     *
     * @param section 当前章节
     * @param conceptIds 本体概念，仅本体候选使用
     * @param hop 本体跳数
     * @param score 向量相似度
     * @param fromOntology 是否来自本体扩展
     */
    private record EvidenceDescriptor(SectionRecord section, List<String> conceptIds, int hop, float score,
                                      boolean fromOntology) {
    }
}
