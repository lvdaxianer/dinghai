package io.github.lvdaxianer.projectiq.retrieval;

import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.projectiq.knowledge.repository.RelationRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CurrentHydrationTest {
    @Test
    void discardsMissingAndStaleVectorHits() throws Exception {
        KnowledgeStore store = mock(KnowledgeStore.class);
        when(store.findSectionById("current")).thenReturn(Optional.of(
                new SectionRecord("current", "doc", "[]", "摘要", "当前正文", 1, 1)));
        when(store.findDocumentById("doc")).thenReturn(Optional.of(
                new io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord(
                        "doc", "team.md", "团队", "sha256:new", java.time.Instant.now())));

        List<HydratedCandidate> candidates = new CurrentCandidateHydrator(store).hydrate(List.of(
                new VectorMatch("current", "doc", "team.md", "sha256:new", 0.9f),
                new VectorMatch("stale", "doc", "team.md", "sha256:old", 0.8f)));

        assertThat(candidates).extracting(HydratedCandidate::sectionId).containsExactly("current");
    }

    @Test
    void discardsVectorHitWhoseDocumentIdDoesNotOwnSection() throws Exception {
        KnowledgeStore store = mock(KnowledgeStore.class);
        when(store.findSectionById("current")).thenReturn(Optional.of(
                new SectionRecord("current", "doc", "[]", "摘要", "当前正文", 1, 1)));
        when(store.findDocumentById("doc")).thenReturn(Optional.of(
                new io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord(
                        "doc", "team.md", "团队", "sha256:new", java.time.Instant.now())));

        List<HydratedCandidate> candidates = new CurrentCandidateHydrator(store).hydrate(List.of(
                new VectorMatch("current", "other-doc", "team.md", "sha256:new", 0.9f)));

        assertThat(candidates).isEmpty();
    }

    @Test
    void reasonerExpandsOnlyStrongRelationsWithinDocumentLimit() throws Exception {
        KnowledgeStore store = mock(KnowledgeStore.class);
        SectionRecord seedSection = new SectionRecord("seed", "doc1", "[]", "摘要", "正文", 1, 1);
        SectionRecord billingSection = new SectionRecord("billing", "doc2", "[]", "计费", "规则", 1, 1);
        when(store.findConceptIdsBySectionId("seed")).thenReturn(List.of("invite"));
        when(store.findRelationsFromConcept("invite")).thenReturn(List.of(
                new RelationRecord(1, "invite", "creates", "pending"),
                new RelationRecord(2, "invite", "related_to", "unrelated")));
        when(store.findRelationsFromConcept("pending")).thenReturn(List.of(
                new RelationRecord(3, "pending", "governed_by", "billing")));
        when(store.findSectionsByConceptId("pending")).thenReturn(List.of(
                new SectionRecord("pending", "doc1", "[]", "状态", "待处理", 1, 1)));
        when(store.findSectionsByConceptId("billing")).thenReturn(List.of(billingSection));

        List<OntologyCandidate> expanded = new OntologyReasoner().expand(List.of(
                new HydratedCandidate(seedSection, new VectorMatch("seed", "doc1", "team.md", "sha256:a", 1.0f))), store);

        assertThat(expanded).extracting(OntologyCandidate::conceptId)
                .containsExactlyInAnyOrder("invite", "pending", "billing");
    }
}
