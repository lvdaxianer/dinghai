package io.github.lvdaxianer.projectiq.knowledge.infrastructure.sqlite;

import io.github.lvdaxianer.projectiq.knowledge.repository.SectionRecord;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 将 sections 查询行映射为不可变领域记录。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class SectionRowMapper implements RowMapper<SectionRecord> {
    /**
     * 映射当前结果集行。
     *
     * @param resultSet 查询结果
     * @param rowNumber 行号
     * @return 章节记录
     * @throws SQLException 读取字段失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Override
    public SectionRecord mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SectionRecord(resultSet.getString("section_id"), resultSet.getString("document_id"),
                resultSet.getString("heading_path_json"), resultSet.getString("summary"),
                resultSet.getString("content"), resultSet.getInt("source_start_line"),
                resultSet.getInt("source_end_line"));
    }
}
