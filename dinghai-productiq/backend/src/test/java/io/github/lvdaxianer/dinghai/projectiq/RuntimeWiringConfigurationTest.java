package io.github.lvdaxianer.dinghai.projectiq;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lvdaxianer.dinghai.projectiq.chat.Answerer;
import io.github.lvdaxianer.dinghai.projectiq.chat.AnswerStatus;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatAnswerGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatQuestion;
import io.github.lvdaxianer.dinghai.projectiq.chat.DeterministicAnswerer;
import io.github.lvdaxianer.dinghai.projectiq.chat.OpenAiCompatibleAnswerer;
import io.github.lvdaxianer.dinghai.projectiq.chat.RerankerGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.DeterministicReranker;
import io.github.lvdaxianer.dinghai.projectiq.chat.OpenAiCompatibleRerankerGateway;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.DeterministicEmbeddingClient;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.EmbeddingClient;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.InMemoryVectorIndex;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.OpenAiCompatibleEmbeddingClient;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.QdrantVectorIndex;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.RuntimeChatAnswerGateway;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.VectorIndex;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 验证运行时模式只装配其对应的检索、重排和回答适配器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class RuntimeWiringConfigurationTest {
    /**
     * 确定性模式应连接真实运行时网关和全部离线适配器。
     *
     * @throws Exception 临时数据库创建失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void wiresDeterministicRuntimeWithoutTheDefaultRefusalGateway() throws Exception {
        try (ConfigurableApplicationContext context = application("deterministic").run()) {
            assertThat(context.getBean(ChatAnswerGateway.class)).isInstanceOf(RuntimeChatAnswerGateway.class);
            assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(DeterministicEmbeddingClient.class);
            assertThat(context.getBean(VectorIndex.class)).isInstanceOf(InMemoryVectorIndex.class);
            assertThat(context.getBean(RerankerGateway.class)).isInstanceOf(DeterministicReranker.class);
            assertThat(context.getBean(Answerer.class)).isInstanceOf(DeterministicAnswerer.class);
            assertThat(context.getBean(ChatAnswerGateway.class).answer(new ChatQuestion("如何邀请成员")).status())
                    .isEqualTo(AnswerStatus.NO_KNOWLEDGE);
        }
    }

    /**
     * 远程模式应连接 OpenAI 兼容适配器和 Qdrant，且无需调用外部依赖即可启动。
     *
     * @throws Exception 临时数据库创建失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void wiresRemoteRuntimeToRemoteAdapters() throws Exception {
        try (ConfigurableApplicationContext context = application("remote")
                .properties("productiq.gateway.base-url=http://127.0.0.1:18080/v1/",
                        "productiq.gateway.api-key=test-key",
                        "productiq.gateway.embedding-model=test-embedding",
                        "productiq.gateway.rerank-model=test-rerank",
                        "productiq.gateway.answer-model=test-answer",
                        "productiq.embedding.dimensions=8",
                        "productiq.qdrant.url=http://127.0.0.1:16333/",
                        "productiq.qdrant.collection=test-collection")
                .run()) {
            assertThat(context.getBean(ChatAnswerGateway.class)).isInstanceOf(RuntimeChatAnswerGateway.class);
            assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(OpenAiCompatibleEmbeddingClient.class);
            assertThat(context.getBean(VectorIndex.class)).isInstanceOf(QdrantVectorIndex.class);
            assertThat(context.getBean(RerankerGateway.class)).isInstanceOf(OpenAiCompatibleRerankerGateway.class);
            assertThat(context.getBean(Answerer.class)).isInstanceOf(OpenAiCompatibleAnswerer.class);
        }
    }

    /**
     * 创建隔离的非 Web Spring 应用，并为每次测试分配独立 SQLite 文件。
     *
     * @param mode 运行时模式
     * @return 可继续补充模式属性的启动器
     * @throws Exception 临时数据库创建失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private SpringApplicationBuilder application(String mode) throws Exception {
        return new SpringApplicationBuilder(KnowledgeAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .properties("productiq.runtime.mode=" + mode,
                        "productiq.sqlite-path=" + Files.createTempFile("productiq-runtime-", ".db"),
                        "productiq.index-worker.interval=1h");
    }
}
