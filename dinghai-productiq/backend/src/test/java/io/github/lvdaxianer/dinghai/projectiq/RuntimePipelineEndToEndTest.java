package io.github.lvdaxianer.dinghai.projectiq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.lvdaxianer.dinghai.projectiq.chat.AnsweringService;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatAnswerGateway;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatStreamController;
import io.github.lvdaxianer.dinghai.projectiq.chat.ChatStreamService;
import io.github.lvdaxianer.dinghai.projectiq.chat.DeterministicAnswerer;
import io.github.lvdaxianer.dinghai.projectiq.chat.DeterministicReranker;
import io.github.lvdaxianer.dinghai.projectiq.chat.StructuredReranker;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.domain.KnowledgePackage;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.infrastructure.sqlite.SqliteKnowledgeStore;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.CurrentCandidateHydrator;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.DeterministicEmbeddingClient;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.InMemoryVectorIndex;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.IndexJobRunner;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.OntologyReasoner;
import io.github.lvdaxianer.dinghai.projectiq.retrieval.RuntimeChatAnswerGateway;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * 验证 SQLite、内存向量、索引任务和 SSE 入口组成的真实本地问答链路。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class RuntimePipelineEndToEndTest {
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(2);

    /**
     * 当前事实完成索引后，SSE 应输出答案、脱敏来源和完成事件。
     *
     * @throws Exception 本地 SQLite 或 SSE 调用失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void answersARealIndexedKnowledgePackageThroughSse() throws Exception {
        try (RuntimeComponents components = components(true, true)) {
            String body = stream(components.gateway(), "如何邀请成员");

            assertThat(body).containsSubsequence("event:delta", "event:sources", "event:done");
            assertThat(body).contains("成员邀请", "documentTitle", "sectionTitle");
            assertThat(body).doesNotContain("sectionId", "documentId", "sourceHash");
        }
    }

    /**
     * 没有当前事实时，SSE 必须在模型调用前输出 no_knowledge。
     *
     * @throws Exception 本地 SQLite 或 SSE 调用失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void emitsNoKnowledgeForAnEmptySqliteStore() throws Exception {
        try (RuntimeComponents components = components(false, false)) {
            String body = stream(components.gateway(), "如何邀请成员");

            assertThat(body).contains("event:error", "no_knowledge");
            assertThat(body).doesNotContain("event:delta", "event:sources", "event:done");
        }
    }

    /**
     * 当前事实仍有待执行索引任务时，SSE 必须拒绝可能过期的向量结果。
     *
     * @throws Exception 本地 SQLite 或 SSE 调用失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void emitsKnowledgeUpdatingUntilThePersistedIndexJobCompletes() throws Exception {
        try (RuntimeComponents components = components(true, false)) {
            String body = stream(components.gateway(), "如何邀请成员");

            assertThat(body).contains("event:error", "knowledge_updating");
            assertThat(body).doesNotContain("event:delta", "event:sources", "event:done");
        }
    }

    /**
     * 创建真实 SQLite、内存向量和 deterministic 回答适配器组合。
     *
     * @param hasKnowledge 是否写入当前知识事实
     * @param isIndexed 是否执行待处理索引任务
     * @return 可通过 SSE 查询的运行时组件
     * @throws Exception SQLite 操作失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private RuntimeComponents components(boolean hasKnowledge, boolean isIndexed) throws Exception {
        SqliteDatabase database = dataSource();
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(database.dataSource());
        DeterministicEmbeddingClient embedding = new DeterministicEmbeddingClient();
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        // 仅有事实时才创建与该事实对应的持久化索引任务。
        if (hasKnowledge) {
            store.replaceSource(knowledge(), Instant.parse("2026-09-16T00:00:00Z"));
            store.enqueueVectorJob("member-invitation.md", "upsert");
        }
        // 仅索引完成场景执行 worker，其余场景保留 pending 状态。
        if (isIndexed) {
            assertThat(new IndexJobRunner(store, embedding, index).executePending(1).succeeded()).isEqualTo(1);
        }
        AnsweringService answeringService = new AnsweringService(new StructuredReranker(new DeterministicReranker()),
                new DeterministicAnswerer());
        ChatAnswerGateway gateway = new RuntimeChatAnswerGateway(store, embedding, index,
                new CurrentCandidateHydrator(store), new OntologyReasoner(), answeringService);
        return new RuntimeComponents(gateway, database.path());
    }

    /**
     * 使用聊天控制器执行一次完整 SSE HTTP 交互。
     *
     * @param gateway 已组合运行时网关
     * @param question 有效用户问题
     * @return SSE 响应正文
     * @throws Exception MockMvc 调用失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String stream(ChatAnswerGateway gateway, String question) throws Exception {
        ChatStreamService service = new ChatStreamService(gateway, STREAM_TIMEOUT);
        try {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ChatStreamController(service)).build();
            MvcResult result = mockMvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"" + question + "\"}"))
                    .andExpect(request().asyncStarted()).andReturn();
            MvcResult completed = mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
            return new String(completed.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        } finally {
            service.close();
        }
    }

    /**
     * 创建启用外键约束的临时 SQLite 数据源。
     *
     * @return 独立 SQLite 数据库
     * @throws Exception 临时文件创建失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private SqliteDatabase dataSource() throws Exception {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        Path path = Files.createTempFile("productiq-e2e-", ".db");
        dataSource.setUrl("jdbc:sqlite:" + path);
        return new SqliteDatabase(dataSource, path);
    }

    /**
     * 创建带一条可检索成员邀请规则的代表性当前知识包。
     *
     * @return 可信知识包
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private KnowledgePackage knowledge() {
        KnowledgePackage.Source source = new KnowledgePackage.Source("member-invitation.md",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "成员邀请", "zh-CN");
        KnowledgePackage.Section section = new KnowledgePackage.Section("member_invitation.rule",
                List.of("成员管理", "邀请"), "邀请成员规则", "管理员可以发送成员邀请，受邀人接受后加入团队。",
                List.of("member_invitation"), new KnowledgePackage.SourceAnchor(1, 2));
        KnowledgePackage.Concept concept = new KnowledgePackage.Concept("member_invitation", "成员邀请", "action",
                "向成员发送邀请", List.of("邀请成员"), List.of("怎么邀请"), List.of(section.id()));
        return new KnowledgePackage("1.0", source, new KnowledgePackage.Document("member_invitation", "成员邀请说明"),
                List.of(section), List.of(concept), List.of());
    }

    /**
     * 聚合本地运行时查询所需组件。
     *
     * @param gateway 真实运行时问答网关
     * @param path 临时 SQLite 数据库路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private record RuntimeComponents(ChatAnswerGateway gateway, Path path) implements AutoCloseable {
        /**
         * 删除本次端到端测试创建的 SQLite 文件。
         *
         * @throws Exception 临时文件删除失败
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        @Override
        public void close() throws Exception {
            Files.deleteIfExists(path);
        }
    }

    /**
     * 保存 SQLite 数据源及其可回收的临时文件路径。
     *
     * @param dataSource 独立 SQLite 数据源
     * @param path 临时 SQLite 数据库路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private record SqliteDatabase(SQLiteDataSource dataSource, Path path) {
    }
}
