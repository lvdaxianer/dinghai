package io.github.lvdaxianer.projectiq.retrieval;

import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.VectorIndexJobRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.assertThat;

class VectorIndexTest {
    @Test
    void inMemoryIndexReturnsClosestPointAndSupportsSourceDeletion() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.upsert(List.of(
                new VectorPoint("a", "doc", "team.md", "sha256:a", List.of(1.0f, 0.0f), "邀请成员"),
                new VectorPoint("b", "doc", "team.md", "sha256:a", List.of(0.0f, 1.0f), "计费规则")));

        assertThat(index.search(List.of(0.9f, 0.1f), 1)).extracting(VectorMatch::sectionId)
                .containsExactly("a");
        index.deleteBySourceKey("team.md");
        assertThat(index.search(List.of(1.0f, 0.0f), 10)).isEmpty();
    }

    @Test
    void payloadBuilderUsesCurrentDocumentHash() {
        VectorPoint point = VectorPoint.from(new SectionRecord("section", "doc", "[\"团队\"]",
                "邀请摘要", "邀请成员", 1, 3), "team.md", "sha256:abc");

        assertThat(point.sourceHash()).isEqualTo("sha256:abc");
        assertThat(point.text()).contains("邀请成员");
    }

    @Test
    void runnerRebuildsCurrentSectionsAndMarksJobSucceeded() throws Exception {
        KnowledgeStore store = mock(KnowledgeStore.class);
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        when(store.findPendingVectorJobs(10)).thenReturn(List.of(
                new VectorIndexJobRecord(1, "team.md", "upsert", "pending", 0, null)));
        when(store.findDocumentBySourceKey("team.md")).thenReturn(Optional.of(
                new DocumentRecord("doc", "team.md", "团队", "sha256:abc", Instant.now())));
        when(store.findSectionsBySourceKey("team.md")).thenReturn(List.of(
                new SectionRecord("section", "doc", "[]", "摘要", "邀请成员", 1, 1)));

        IndexJobRunner.ExecutionResult result = new IndexJobRunner(store,
                new DeterministicEmbeddingClient(), index).executePending(10);

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(index.search(new DeterministicEmbeddingClient().embed("邀请成员"), 1))
                .extracting(VectorMatch::sectionId).containsExactly("section");
        verify(store).updateVectorJobStatus(1, "processing", null);
        verify(store).updateVectorJobStatus(1, "succeeded", null);
    }

    @Test
    void qdrantAdapterMapsSearchPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/collections/product/points/search", exchange -> {
            byte[] body = "{\"result\":[{\"score\":0.9,\"payload\":{\"sectionId\":\"s1\",\"documentId\":\"d1\",\"sourceKey\":\"team.md\",\"sourceHash\":\"sha256:a\"}}]}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            QdrantVectorIndex index = new QdrantVectorIndex(
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/"), "product",
                    new ObjectMapper(), java.time.Duration.ofSeconds(2));
            assertThat(index.search(List.of(1.0f), 1)).extracting(VectorMatch::sectionId).containsExactly("s1");
        } finally {
            server.stop(0);
        }
    }
}
