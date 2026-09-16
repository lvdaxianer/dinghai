package io.github.lvdaxianer.projectiq.knowledge.infrastructure.sqlite;
import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage;
import io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;
import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.VectorIndexJobRecord;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
/**
 * 基于 SQLite JDBC 的知识事实仓储。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class SqliteKnowledgeStore implements KnowledgeStore {
    private static final String SCHEMA_RESOURCE = "/db/migration/V1__knowledge_schema.sql";
    private static final int SECTION_QUERY_LIMIT = 10_000;
    private static final int JOB_QUERY_LIMIT = 1_000;
    private static final List<String> INDEX_OPERATIONS = List.of("upsert", "delete");
    private final DataSource dataSource;
    /**
     * 创建仓储并初始化 SQLite schema。
     *
     * @param dataSource SQLite 数据源
     * @throws SQLException schema 初始化失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public SqliteKnowledgeStore(DataSource dataSource) throws SQLException {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource 不能为空");
        initializeSchema();
    }
    /**
     * 在单个事务中替换来源拥有的事实。
     *
     * @param knowledgePackage 待保存知识包
     * @param updatedAt 文档更新时间
     * @throws SQLException SQLite 写入失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Override
    public void replaceSource(KnowledgePackage knowledgePackage, Instant updatedAt) throws SQLException {
        Objects.requireNonNull(knowledgePackage, "knowledgePackage 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                replaceFacts(connection, knowledgePackage, updatedAt);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
    /**
     * 执行来源事实替换的事务内写入步骤。
     *
     * @param connection 当前事务连接
     * @param knowledgePackage 待保存知识包
     * @param updatedAt 文档更新时间
     * @throws SQLException SQLite 写入失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void replaceFacts(Connection connection, KnowledgePackage knowledgePackage, Instant updatedAt)
            throws SQLException {
        deleteSourceFacts(connection, knowledgePackage.source().key());
        insertDocument(connection, knowledgePackage, updatedAt);
        insertConcepts(connection, knowledgePackage.concepts());
        insertSections(connection, knowledgePackage);
        insertSectionConcepts(connection, knowledgePackage.sections());
        insertAliases(connection, knowledgePackage.concepts());
        insertRelations(connection, knowledgePackage);
    }
    /**
     * 显式删除来源事实，并保留仍有其他证据的全局关系。
     *
     * @param sourceKey 来源文件键
     * @throws SQLException SQLite 删除失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Override
    public void deleteSource(String sourceKey) throws SQLException {
        Objects.requireNonNull(sourceKey, "sourceKey 不能为空");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                deleteSourceFacts(connection, sourceKey);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
    private void deleteSourceFacts(Connection connection, String sourceKey) throws SQLException {
        String documentId = findDocumentId(connection, sourceKey);
        if (documentId != null) {
            executeUpdate(connection, "DELETE FROM documents WHERE document_id = ?", documentId);
            executeUpdate(connection, "DELETE FROM relations WHERE relation_id NOT IN "
                    + "(SELECT relation_id FROM relation_evidence)");
        }
    }
    private String findDocumentId(Connection connection, String sourceKey) throws SQLException {
        String sql = "SELECT document_id FROM documents WHERE source_key = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }
    private void insertDocument(Connection connection, KnowledgePackage knowledgePackage, Instant updatedAt)
            throws SQLException {
        String sql = "INSERT INTO documents(document_id, source_key, title, content_hash, updated_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, knowledgePackage.document().id());
            statement.setString(2, knowledgePackage.source().key());
            statement.setString(3, knowledgePackage.source().title());
            statement.setString(4, knowledgePackage.source().hash());
            statement.setString(5, updatedAt.toString());
            statement.executeUpdate();
        }
    }
    private void insertConcepts(Connection connection, List<KnowledgePackage.Concept> concepts) throws SQLException {
        String sql = "INSERT INTO concepts(concept_id, name, concept_type, definition) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(concept_id) DO UPDATE SET name = excluded.name, "
                + "concept_type = excluded.concept_type, definition = excluded.definition";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (KnowledgePackage.Concept concept : concepts) {
                statement.setString(1, concept.id());
                statement.setString(2, concept.name());
                statement.setString(3, concept.type());
                statement.setString(4, concept.definition());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
    private void insertSections(Connection connection, KnowledgePackage knowledgePackage) throws SQLException {
        String sql = "INSERT INTO sections(section_id, document_id, heading_path_json, summary, content, "
                + "source_start_line, source_end_line) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (KnowledgePackage.Section section : knowledgePackage.sections()) {
                statement.setString(1, section.id());
                statement.setString(2, knowledgePackage.document().id());
                statement.setString(3, toJsonArray(section.headingPath()));
                statement.setString(4, section.summary());
                statement.setString(5, section.content());
                statement.setInt(6, section.sourceAnchor().startLine());
                statement.setInt(7, section.sourceAnchor().endLine());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
    private void insertSectionConcepts(Connection connection, List<KnowledgePackage.Section> sections)
            throws SQLException {
        String sql = "INSERT INTO section_concepts(section_id, concept_id, role) VALUES (?, ?, 'mentioned')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (KnowledgePackage.Section section : sections) {
                for (String conceptId : section.conceptIds()) {
                    statement.setString(1, section.id());
                    statement.setString(2, conceptId);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }
    private void insertAliases(Connection connection, List<KnowledgePackage.Concept> concepts) throws SQLException {
        String sql = "INSERT OR IGNORE INTO aliases(concept_id, alias, alias_kind) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (KnowledgePackage.Concept concept : concepts) {
                addAliases(statement, concept.id(), concept.canonicalAliases(), "canonical");
                addAliases(statement, concept.id(), concept.queryAliases(), "query");
            }
            statement.executeBatch();
        }
    }
    private void addAliases(PreparedStatement statement, String conceptId, List<String> aliases, String aliasKind)
            throws SQLException {
        for (String alias : aliases) {
            statement.setString(1, conceptId);
            statement.setString(2, alias);
            statement.setString(3, aliasKind);
            statement.addBatch();
        }
    }
    private void insertRelations(Connection connection, KnowledgePackage knowledgePackage) throws SQLException {
        for (KnowledgePackage.Relation relation : knowledgePackage.relations()) {
            if (!"retire".equals(relation.operation())) {
                insertRelation(connection, knowledgePackage, relation);
            }
        }
    }
    private void insertRelation(Connection connection, KnowledgePackage knowledgePackage,
                                KnowledgePackage.Relation relation) throws SQLException {
        String sql = "INSERT INTO relations(from_concept_id, relation_type, to_concept_id) VALUES (?, ?, ?) "
                + "ON CONFLICT(from_concept_id, relation_type, to_concept_id) DO NOTHING";
        executeUpdate(connection, sql, relation.from(), relation.type(), relation.to());
        long relationId = findRelationId(connection, relation);
        if (relation.evidence() != null) {
            insertRelationEvidence(connection, knowledgePackage, relationId, relation.evidence());
        }
    }
    private void insertRelationEvidence(Connection connection, KnowledgePackage knowledgePackage, long relationId,
                                        KnowledgePackage.Relation.Evidence evidence) throws SQLException {
        String sql = "INSERT OR IGNORE INTO relation_evidence(relation_id, document_id, section_id, "
                + "source_quote, source_hash) VALUES (?, ?, ?, ?, ?)";
        executeUpdate(connection, sql, relationId, knowledgePackage.document().id(), evidence.sectionId(),
                evidence.quote(), knowledgePackage.source().hash());
    }
    private long findRelationId(Connection connection, KnowledgePackage.Relation relation) throws SQLException {
        String sql = "SELECT relation_id FROM relations WHERE from_concept_id = ? AND relation_type = ? "
                + "AND to_concept_id = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, relation.from());
            statement.setString(2, relation.type());
            statement.setString(3, relation.to());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        throw new SQLException("关系写入后未找到关系 ID");
    }
    @Override
    public Optional<DocumentRecord> findDocumentBySourceKey(String sourceKey) throws SQLException {
        String sql = "SELECT document_id, source_key, title, content_hash, updated_at FROM documents "
                + "WHERE source_key = ? LIMIT 1";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(new DocumentRowMapper().mapRow(resultSet, 1))
                        : Optional.empty();
            }
        }
    }
    @Override
    public List<SectionRecord> findSectionsBySourceKey(String sourceKey) throws SQLException {
        String sql = "SELECT s.section_id, s.document_id, s.heading_path_json, s.summary, s.content, "
                + "s.source_start_line, s.source_end_line FROM sections s JOIN documents d "
                + "ON d.document_id = s.document_id WHERE d.source_key = ? ORDER BY s.section_id LIMIT ?";
        List<SectionRecord> sections = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceKey);
            statement.setInt(2, SECTION_QUERY_LIMIT);
            try (ResultSet resultSet = statement.executeQuery()) {
                SectionRowMapper mapper = new SectionRowMapper();
                while (resultSet.next()) {
                    sections.add(mapper.mapRow(resultSet, sections.size() + 1));
                }
            }
        }
        return sections;
    }
    /** 创建向量索引任务。 */
    @Override
    public void enqueueVectorJob(String sourceKey, String operation) throws SQLException {
        validateOperation(operation);
        try (Connection connection = connection()) {
            executeUpdate(connection, "INSERT INTO vector_index_jobs(source_key, operation) VALUES (?, ?)",
                    sourceKey, operation);
        }
    }
    /** 查询待处理或失败待重试的索引任务。 */
    @Override
    public List<VectorIndexJobRecord> findPendingVectorJobs(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, JOB_QUERY_LIMIT));
        String sql = "SELECT job_id, source_key, operation, status, retry_count, error_message "
                + "FROM vector_index_jobs WHERE status IN ('pending', 'failed') ORDER BY job_id LIMIT ?";
        List<VectorIndexJobRecord> jobs = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jobs.add(new VectorIndexJobRecord(resultSet.getLong("job_id"), resultSet.getString("source_key"),
                            resultSet.getString("operation"), resultSet.getString("status"),
                            resultSet.getInt("retry_count"), resultSet.getString("error_message")));
                }
            }
        }
        return jobs;
    }
    private void validateOperation(String operation) {
        if (!INDEX_OPERATIONS.contains(operation)) throw new IllegalArgumentException("不支持的索引操作: " + operation);
    }
    private void executeUpdate(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
    private void initializeSchema() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String sql : schema().split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }
    private Connection connection() throws SQLException {
        Connection connection = dataSource.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            return connection;
        } catch (SQLException exception) { connection.close(); throw exception; }
    }
    private static String toJsonArray(List<String> values) {
        return "[" + String.join(",", values.stream().map(SqliteKnowledgeStore::quoteJson).toList()) + "]";
    }
    private static String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    private static String schema() throws SQLException {
        try (InputStream input = SqliteKnowledgeStore.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) throw new SQLException("SQLite schema 资源不存在");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SQLException("SQLite schema 读取失败", exception);
        }
    }
}
