package io.github.lvdaxianer.projectiq.knowledge.infrastructure.sqlite;

import io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * 将 documents 查询行映射为不可变领域记录。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class DocumentRowMapper implements RowMapper<DocumentRecord> {
    /**
     * 映射当前结果集行。
     *
     * @param resultSet 查询结果
     * @param rowNumber 行号
     * @return 文档记录
     * @throws SQLException 读取字段失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Override
    public DocumentRecord mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DocumentRecord(resultSet.getString("document_id"), resultSet.getString("source_key"),
                resultSet.getString("title"), resultSet.getString("content_hash"),
                Instant.parse(resultSet.getString("updated_at")));
    }
}
