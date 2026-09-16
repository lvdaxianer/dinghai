package io.github.lvdaxianer.dinghai.projectiq.knowledge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.lvdaxianer.dinghai.projectiq.knowledge.application.KnowledgeImportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class KnowledgeAdminControllerTest {
    @Test
    void refusesMissingAdminKeyBeforeCallingImporter() {
        KnowledgeImportService importer = mock(KnowledgeImportService.class);
        KnowledgeAdminController controller = new KnowledgeAdminController(importer, "secret");

        var response = controller.importSource(null, new KnowledgeAdminController.ImportRequest("team.md"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(importer);
    }

    @Test
    void allowsAuthorizedImport() {
        KnowledgeImportService importer = mock(KnowledgeImportService.class);
        when(importer.importSource("team.md")).thenReturn(
                new KnowledgeImportService.ImportResult(KnowledgeImportService.Status.IMPORTED,
                        "team.md", "sha256:abc"));
        KnowledgeAdminController controller = new KnowledgeAdminController(importer, "secret");

        var response = controller.importSource("secret", new KnowledgeAdminController.ImportRequest("team.md"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(importer).importSource("team.md");
    }
}
