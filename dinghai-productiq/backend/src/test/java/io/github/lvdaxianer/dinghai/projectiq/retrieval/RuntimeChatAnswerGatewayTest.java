package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lvdaxianer.dinghai.projectiq.chat.AnswerResponse;
import io.github.lvdaxianer.dinghai.projectiq.chat.AnswerStatus;
import io.github.lvdaxianer.dinghai.projectiq.chat.AnsweringService;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatQuestion;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.SectionRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 验证运行时网关只使用 SQLite 当前性校验后的检索候选回答。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class RuntimeChatAnswerGatewayTest {
    /**
     * 当前知识应经嵌入、Top-20 召回和水合后交给回答服务。
     *
     * @throws Exception 检索调用失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void answersOnlyFromCurrentHydratedCandidates() throws Exception {
        KnowledgeStore store = mock(KnowledgeStore.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorIndex vectorIndex = mock(VectorIndex.class);
        CurrentCandidateHydrator hydrator = mock(CurrentCandidateHydrator.class);
        OntologyReasoner reasoner = mock(OntologyReasoner.class);
        AnsweringService answeringService = mock(AnsweringService.class);
        SectionRecord section = new SectionRecord("section", "document", "[\"团队\"]", "摘要", "正文", 1, 2);
        VectorMatch match = new VectorMatch("section", "document", "team.md", "sha256:abc", 0.9f);
        when(store.hasDocuments()).thenReturn(true);
        when(store.hasIncompleteVectorJobs()).thenReturn(false);
        when(embeddingClient.embed("邀请如何处理")).thenReturn(List.of(1.0f));
        when(vectorIndex.search(List.of(1.0f), 20)).thenReturn(List.of(match));
        when(hydrator.hydrate(List.of(match))).thenReturn(List.of(new HydratedCandidate(section, match)));
        when(reasoner.expand(any(), eq(store))).thenReturn(List.of());
        when(store.findDocumentsByIds(List.of("document"))).thenReturn(Map.of("document",
                new DocumentRecord("document", "team.md", "团队", "sha256:abc", Instant.now())));
        when(store.findConceptIdsBySectionIds(List.of("section"))).thenReturn(Map.of("section",
                List.of("member_invitation")));
        when(answeringService.answer(any())).thenReturn(AnswerResponse.refusal(AnswerStatus.NO_RELEVANT_KNOWLEDGE));

        AnswerResponse response = new RuntimeChatAnswerGateway(store, embeddingClient, vectorIndex, hydrator,
                reasoner, answeringService).answer(new ChatQuestion("邀请如何处理"));

        assertThat(response.status()).isEqualTo(AnswerStatus.NO_RELEVANT_KNOWLEDGE);
        verify(vectorIndex).search(List.of(1.0f), 20);
        verify(store).findDocumentsByIds(List.of("document"));
        verify(store).findConceptIdsBySectionIds(List.of("section"));
        verify(answeringService).answer(any());
    }

    /**
     * 无当前文档时必须直接稳定拒答，不能调用任何下游检索或回答依赖。
     *
     * @throws Exception 仓储查询失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void refusesWithoutKnowledgeBeforeCallingDownstreamDependencies() throws Exception {
        KnowledgeStore store = mock(KnowledgeStore.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorIndex vectorIndex = mock(VectorIndex.class);
        AnsweringService answeringService = mock(AnsweringService.class);
        when(store.hasDocuments()).thenReturn(false);

        AnswerResponse response = gateway(store, embeddingClient, vectorIndex, answeringService)
                .answer(new ChatQuestion("邀请如何处理"));

        assertThat(response.status()).isEqualTo(AnswerStatus.NO_KNOWLEDGE);
        verify(embeddingClient, never()).embed(any());
        verify(vectorIndex, never()).search(any(), any(Integer.class));
        verify(answeringService, never()).answer(any());
    }

    /**
     * 索引存在任何未完成任务时必须拒绝旧向量检索。
     *
     * @throws Exception 仓储查询失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void refusesWhileKnowledgeIsUpdatingBeforeCallingDownstreamDependencies() throws Exception {
        KnowledgeStore store = mock(KnowledgeStore.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorIndex vectorIndex = mock(VectorIndex.class);
        AnsweringService answeringService = mock(AnsweringService.class);
        when(store.hasDocuments()).thenReturn(true);
        when(store.hasIncompleteVectorJobs()).thenReturn(true);

        AnswerResponse response = gateway(store, embeddingClient, vectorIndex, answeringService)
                .answer(new ChatQuestion("邀请如何处理"));

        assertThat(response.status()).isEqualTo(AnswerStatus.KNOWLEDGE_UPDATING);
        verify(embeddingClient, never()).embed(any());
        verify(vectorIndex, never()).search(any(), any(Integer.class));
        verify(answeringService, never()).answer(any());
    }

    /**
     * 创建使用空本体扩展的运行时网关，供拒答边界测试使用。
     *
     * @param store 当前知识仓储
     * @param embeddingClient 问题向量生成器
     * @param vectorIndex 向量索引
     * @param answeringService 证据回答服务
     * @return 运行时问答网关
     */
    private static RuntimeChatAnswerGateway gateway(KnowledgeStore store, EmbeddingClient embeddingClient,
                                                    VectorIndex vectorIndex, AnsweringService answeringService) {
        return new RuntimeChatAnswerGateway(store, embeddingClient, vectorIndex,
                new CurrentCandidateHydrator(store), new OntologyReasoner(), answeringService);
    }
}
