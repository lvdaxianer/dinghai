package io.github.lvdaxianer.projectiq.knowledge.repository;

import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage;
import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage.Concept;
import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage.Document;
import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage.Relation;
import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage.Section;
import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage.Source;
import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage.SourceAnchor;
import io.github.lvdaxianer.projectiq.knowledge.infrastructure.sqlite.SqliteKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteKnowledgeStoreTest {

    @Test
    void createsSchemaAndPersistsPackageFacts() throws Exception {
        SQLiteDataSource dataSource = dataSource();
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(dataSource);
        KnowledgePackage knowledge = knowledge("team.md", "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "doc_team", "team_section");

        store.replaceSource(knowledge, Instant.parse("2026-09-16T00:00:00Z"));
        store.enqueueVectorJob("team.md", "upsert");

        assertThat(store.findDocumentBySourceKey("team.md")).get().satisfies(document -> {
            assertThat(document.documentId()).isEqualTo("doc_team");
            assertThat(document.contentHash()).startsWith("sha256:");
        });
        assertThat(store.findSectionsBySourceKey("team.md")).extracting(SectionRecord::sectionId)
                .containsExactly("team_section");
        assertThat(count(dataSource, "concepts")).isEqualTo(2);
        assertThat(count(dataSource, "aliases")).isEqualTo(2);
        assertThat(count(dataSource, "section_concepts")).isEqualTo(2);
        assertThat(count(dataSource, "relations")).isEqualTo(1);
        assertThat(count(dataSource, "relation_evidence")).isEqualTo(1);
        assertThat(count(dataSource, "vector_index_jobs")).isEqualTo(1);
    }

    @Test
    void enforcesUniqueKeysAndForeignKeys() throws Exception {
        SQLiteDataSource dataSource = dataSource();
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(dataSource);
        KnowledgePackage knowledge = knowledge("team.md", "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "doc_team", "team_section");
        store.replaceSource(knowledge, Instant.now());

        assertThatThrownBy(() -> insert(dataSource,
                "INSERT INTO aliases(concept_id, alias, alias_kind) VALUES (?, ?, 'canonical')",
                "member_invitation", "invite"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insert(dataSource,
                "INSERT INTO section_concepts(section_id, concept_id, role) VALUES (?, ?, 'mentioned')",
                "missing_section", "member_invitation"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void replacementIsScopedToSourceAndRelationSurvivesWhileOtherEvidenceRemains() throws Exception {
        SQLiteDataSource dataSource = dataSource();
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(dataSource);
        store.replaceSource(knowledge("one.md", "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", "doc_one", "one_section"), Instant.now());
        store.replaceSource(knowledge("two.md", "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd", "doc_two", "two_section"), Instant.now());
        assertThat(count(dataSource, "relation_evidence")).isEqualTo(2);
        assertThat(count(dataSource, "relations")).isEqualTo(1);

        store.replaceSource(knowledgeWithoutRelation("one.md", "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "doc_one", "one_new"), Instant.now());
        assertThat(store.findSectionsBySourceKey("one.md")).extracting(SectionRecord::sectionId).containsExactly("one_new");
        assertThat(store.findSectionsBySourceKey("two.md")).extracting(SectionRecord::sectionId).containsExactly("two_section");
        assertThat(count(dataSource, "relation_evidence")).isEqualTo(1);
        assertThat(count(dataSource, "relations")).isEqualTo(1);

        store.deleteSource("two.md");
        assertThat(count(dataSource, "relation_evidence")).isZero();
        assertThat(count(dataSource, "relations")).isZero();
    }

    @Test
    void schemaUsesForeignKeys() throws Exception {
        SQLiteDataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(statement.executeQuery("PRAGMA foreign_keys").getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void exposesPendingVectorJobsAndRejectsUnknownOperations() throws Exception {
        SQLiteDataSource dataSource = dataSource();
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(dataSource);

        store.enqueueVectorJob("team.md", "delete");

        assertThat(store.findPendingVectorJobs(10)).singleElement().satisfies(job -> {
            assertThat(job.sourceKey()).isEqualTo("team.md");
            assertThat(job.operation()).isEqualTo("delete");
            assertThat(job.status()).isEqualTo("pending");
        });
        assertThatThrownBy(() -> store.enqueueVectorJob("team.md", "rebuild"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SQLiteDataSource dataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDb());
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        dataSource.setConfig(config);
        return dataSource;
    }

    private static String tempDb() {
        try {
            return Files.createTempFile("productiq-", ".db").toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long count(SQLiteDataSource dataSource, String table) throws SQLException {
        List<String> tables = List.of("documents", "sections", "concepts", "aliases", "section_concepts",
                "relations", "relation_evidence", "vector_index_jobs");
        if (!tables.contains(table)) {
            throw new IllegalArgumentException("unsupported table");
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private static void insert(SQLiteDataSource dataSource, String sql, String first, String second)
            throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first);
            statement.setString(2, second);
            statement.executeUpdate();
        }
    }

    private static KnowledgePackage knowledge(String sourceKey, String hash, String documentId, String sectionId) {
        Source source = new Source(sourceKey, hash, "团队", "zh-CN");
        Section section = new Section(sectionId, List.of("团队"), "邀请摘要", "邀请成员并创建待处理邀请。", List.of("member_invitation", "pending_invitation"), new SourceAnchor(1, 3));
        Concept member = new Concept("member_invitation", "成员邀请", "action", "邀请成员", List.of("invite"), List.of(), List.of(sectionId));
        Concept pending = new Concept("pending_invitation", "待处理邀请", "state", "等待接受", List.of("pending"), List.of(), List.of(sectionId));
        Relation relation = new Relation("add", "member_invitation", "creates", "pending_invitation", new Relation.Evidence(sectionId, "邀请成员并创建待处理邀请"));
        return new KnowledgePackage("1.0", source, new Document(documentId, "团队摘要"), List.of(section), List.of(member, pending), List.of(relation));
    }

    private static KnowledgePackage knowledgeWithoutRelation(String sourceKey, String hash, String documentId, String sectionId) {
        Source source = new Source(sourceKey, hash, "团队", "zh-CN");
        Section section = new Section(sectionId, List.of("团队"), "新摘要", "只有成员资料。", List.of("member_invitation"), new SourceAnchor(1, 1));
        Concept member = new Concept("member_invitation", "成员邀请", "action", "邀请成员", List.of("invite"), List.of(), List.of(sectionId));
        return new KnowledgePackage("1.0", source, new Document(documentId, "团队摘要"), List.of(section), List.of(member), List.of());
    }
}
