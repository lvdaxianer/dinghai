package io.github.lvdaxianer.dinghai.projectiq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lvdaxianer.dinghai.projectiq.chat.Answerer;
import io.github.lvdaxianer.dinghai.projectiq.chat.AnsweringService;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatAnswerGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.DeterministicAnswerer;
import io.github.lvdaxianer.dinghai.projectiq.chat.DeterministicReranker;
import io.github.lvdaxianer.dinghai.projectiq.chat.HttpOpenAiCompatibleChatGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.OpenAiCompatibleAnswerer;
import io.github.lvdaxianer.dinghai.projectiq.chat.OpenAiCompatibleRerankerGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.OpenAiHttpConfiguration;
import io.github.lvdaxianer.dinghai.projectiq.chat.RerankerGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.StructuredReranker;
import io.github.lvdaxianer.dinghai.projectiq.integration.OpenAiGatewayConfiguration;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.CurrentCandidateHydrator;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.DeterministicEmbeddingClient;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.EmbeddingClient;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.InMemoryVectorIndex;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.IndexJobRunner;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.OntologyReasoner;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.OpenAiCompatibleEmbeddingClient;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.QdrantVectorIndex;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.RuntimeChatAnswerGateway;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.VectorIndex;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按显式运行模式装配完整检索、索引、重排和回答链路。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@Configuration
public class ProductIqRuntimeWiringConfiguration {
    /**
     * 装配 deterministic 或 remote 的问题嵌入器。
     *
     * @param properties 已校验运行时配置
     * @param objectMapper JSON 编解码器
     * @return 唯一问题嵌入器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    EmbeddingClient embeddingClient(ProductIqRuntimeProperties properties, ObjectMapper objectMapper) {
        if (properties.mode() == ProductIqRuntimeProperties.Mode.DETERMINISTIC) {
            return new DeterministicEmbeddingClient();
        }
        return new OpenAiCompatibleEmbeddingClient(gateway(properties, properties.embeddingTimeout()),
                properties.embeddingModel(), properties.embeddingDimensions(), objectMapper);
    }

    /**
     * 装配 deterministic 内存索引或 remote Qdrant 索引。
     *
     * @param properties 已校验运行时配置
     * @param objectMapper JSON 编解码器
     * @return 唯一章节向量索引
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    VectorIndex vectorIndex(ProductIqRuntimeProperties properties, ObjectMapper objectMapper) {
        if (properties.mode() == ProductIqRuntimeProperties.Mode.DETERMINISTIC) {
            return new InMemoryVectorIndex();
        }
        return new QdrantVectorIndex(properties.qdrantUri().orElseThrow(), properties.qdrantCollection(), objectMapper,
                properties.qdrantTimeout());
    }

    /**
     * 装配 deterministic 或 remote 的结构化候选排序网关。
     *
     * @param properties 已校验运行时配置
     * @param objectMapper JSON 编解码器
     * @return 唯一重排网关
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    RerankerGateway rerankerGateway(ProductIqRuntimeProperties properties, ObjectMapper objectMapper) {
        if (properties.mode() == ProductIqRuntimeProperties.Mode.DETERMINISTIC) {
            return new DeterministicReranker();
        }
        return new OpenAiCompatibleRerankerGateway(gateway(properties, properties.rerankTimeout()),
                properties.rerankModel(), objectMapper);
    }

    /**
     * 装配 deterministic 或 remote 的受限证据回答器。
     *
     * @param properties 已校验运行时配置
     * @param objectMapper JSON 编解码器
     * @return 唯一回答器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    Answerer answerer(ProductIqRuntimeProperties properties, ObjectMapper objectMapper) {
        if (properties.mode() == ProductIqRuntimeProperties.Mode.DETERMINISTIC) {
            return new DeterministicAnswerer();
        }
        OpenAiGatewayConfiguration gateway = gateway(properties, properties.rerankTimeout());
        OpenAiHttpConfiguration configuration = new OpenAiHttpConfiguration(gateway.endpoint("chat/completions"),
                gateway.apiKey(), properties.answerModel(), gateway.timeout());
        return new OpenAiCompatibleAnswerer(properties.answerModel(), new HttpOpenAiCompatibleChatGateway(configuration,
                HttpClient.newBuilder().connectTimeout(gateway.timeout()).build(), objectMapper));
    }

    /**
     * 将结构化重排和回答器组合为统一证据回答服务。
     *
     * @param rerankerGateway 已装配重排网关
     * @param answerer 已装配回答器
     * @return 证据回答服务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    AnsweringService answeringService(RerankerGateway rerankerGateway, Answerer answerer) {
        return new AnsweringService(new StructuredReranker(rerankerGateway), answerer);
    }

    /**
     * 装配向量候选到 SQLite 当前事实的批量水合器。
     *
     * @param store SQLite 当前事实仓储
     * @return 当前性水合器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    CurrentCandidateHydrator currentCandidateHydrator(KnowledgeStore store) {
        return new CurrentCandidateHydrator(store);
    }

    /**
     * 装配固定关系白名单和跳数边界的本体推理器。
     *
     * @return 本体推理器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    OntologyReasoner ontologyReasoner() {
        return new OntologyReasoner();
    }

    /**
     * 装配可恢复的 SQLite 向量索引任务执行器。
     *
     * @param store SQLite 当前事实仓储
     * @param embeddingClient 问题与章节嵌入器
     * @param vectorIndex 当前模式向量索引
     * @return 索引任务执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    IndexJobRunner indexJobRunner(KnowledgeStore store, EmbeddingClient embeddingClient, VectorIndex vectorIndex) {
        return new IndexJobRunner(store, embeddingClient, vectorIndex);
    }

    /**
     * 装配真实问答网关，替代任何不执行检索链路的默认拒答实现。
     *
     * @param store SQLite 当前事实仓储
     * @param embeddingClient 问题嵌入器
     * @param vectorIndex 当前模式向量索引
     * @param hydrator 当前性水合器
     * @param reasoner 本体推理器
     * @param answeringService 受限证据回答服务
     * @return 运行时问答网关
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Bean
    ChatAnswerGateway chatAnswerGateway(KnowledgeStore store, EmbeddingClient embeddingClient, VectorIndex vectorIndex,
                                        CurrentCandidateHydrator hydrator, OntologyReasoner reasoner,
                                        AnsweringService answeringService) {
        return new RuntimeChatAnswerGateway(store, embeddingClient, vectorIndex, hydrator, reasoner,
                answeringService);
    }

    /**
     * 创建具备当前模式已校验网关地址、密钥和超时的远程连接配置。
     *
     * @param properties 已校验运行时配置
     * @param timeout 当前远程调用时限
     * @return OpenAI 兼容网关配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private OpenAiGatewayConfiguration gateway(ProductIqRuntimeProperties properties,
                                               java.time.Duration timeout) {
        return new OpenAiGatewayConfiguration(properties.gatewayBaseUri().orElseThrow(), properties.gatewayApiKey(),
                timeout);
    }
}
