package io.github.lvdaxianer.dinghai.projectiq.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.repository.KnowledgeStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeImportServiceTest {
    @Test
    void importsTrustedPairAndEnqueuesAfterFactsCommit() throws Exception {
        Path root = Files.createTempDirectory("trusted-");
        Path markdown = root.resolve("team.md");
        String source = "# 团队\n\n邀请链接有效期为 72 小时。\n";
        Files.writeString(markdown, source, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("team.knowledge.json"), candidate(source), StandardCharsets.UTF_8);
        KnowledgeStore store = mock(KnowledgeStore.class);
        KnowledgeImportService service = new KnowledgeImportService(root,
                new KnowledgePackageValidator(new com.fasterxml.jackson.databind.ObjectMapper()), store);

        KnowledgeImportService.ImportResult result = service.importSource("team.md");

        assertThat(result.status()).isEqualTo(KnowledgeImportService.Status.IMPORTED);
        verify(store).replaceSource(any(), any(Instant.class));
        verify(store).enqueueVectorJob("team.md", "upsert");
    }

    @Test
    void repeatsSameHashWithoutReplacingOrDuplicatingJob() throws Exception {
        Path root = Files.createTempDirectory("trusted-");
        String source = "# 团队\n\n邀请链接有效期为 72 小时。\n";
        Files.writeString(root.resolve("team.md"), source, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("team.knowledge.json"), candidate(source), StandardCharsets.UTF_8);
        KnowledgeStore store = mock(KnowledgeStore.class);
        when(store.findDocumentBySourceKey("team.md")).thenReturn(Optional.of(
                new DocumentRecord("team", "team.md", "团队", hash(source), Instant.now())));
        KnowledgeImportService service = new KnowledgeImportService(root,
                new KnowledgePackageValidator(new com.fasterxml.jackson.databind.ObjectMapper()), store);

        assertThat(service.importSource("team.md").status()).isEqualTo(KnowledgeImportService.Status.ALREADY_IMPORTED);
        verify(store, never()).replaceSource(any(), any());
        verify(store, never()).enqueueVectorJob(any(), any());
    }

    @Test
    void rejectsTraversalAbsoluteAndNonMarkdownKeys() throws Exception {
        Path root = Files.createTempDirectory("trusted-");
        KnowledgeImportService service = new KnowledgeImportService(root,
                new KnowledgePackageValidator(new com.fasterxml.jackson.databind.ObjectMapper()), mock(KnowledgeStore.class));
        for (String key : new String[]{"../team.md", "nested/team.md", "/tmp/team.md", "team.txt", "..md"}) {
            assertThatThrownBy(() -> service.importSource(key))
                    .isInstanceOf(KnowledgeImportService.ImportException.class)
                    .extracting(exception -> ((KnowledgeImportService.ImportException) exception).code())
                    .isEqualTo("INVALID_SOURCE_KEY");
        }
    }

    @Test
    void explicitDeleteRemovesOnlyRequestedSourceAndEnqueuesDelete() throws Exception {
        Path root = Files.createTempDirectory("trusted-");
        KnowledgeStore store = mock(KnowledgeStore.class);
        KnowledgeImportService service = new KnowledgeImportService(root,
                new KnowledgePackageValidator(new com.fasterxml.jackson.databind.ObjectMapper()), store);

        service.deleteSource("team.md");

        verify(store).deleteSource("team.md");
        verify(store).enqueueVectorJob("team.md", "delete");
    }

    private static String candidate(String source) {
        String hash = hash(source);
        return "{" +
                "\"schemaVersion\":\"1.0\",\"source\":{\"key\":\"team.md\",\"hash\":\"" + hash + "\",\"title\":\"团队\",\"language\":\"zh-CN\"}," +
                "\"document\":{\"id\":\"team\",\"summary\":\"团队摘要\"}," +
                "\"sections\":[{\"id\":\"team.invite\",\"headingPath\":[\"团队\"],\"summary\":\"邀请\",\"content\":\"邀请链接有效期为 72 小时。\",\"conceptIds\":[\"team_invite\"],\"sourceAnchor\":{\"startLine\":1,\"endLine\":3}}]," +
                "\"concepts\":[{\"id\":\"team_invite\",\"name\":\"邀请\",\"type\":\"action\",\"definition\":\"邀请\",\"canonicalAliases\":[],\"queryAliases\":[],\"evidenceSectionIds\":[\"team.invite\"]}],\"relations\":[]}";
    }

    private static String hash(String source) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
