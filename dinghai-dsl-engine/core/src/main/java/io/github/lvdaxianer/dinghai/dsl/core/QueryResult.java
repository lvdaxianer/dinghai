package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 数据适配器返回的统一查询结果。
 *
 * @param columns 输出列描述
 * @param rows 结果行
 * @param rowCount 返回行数
 * @param truncated 是否因资源限制截断
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record QueryResult(
        List<QueryColumn> columns,
        List<List<Object>> rows,
        long rowCount,
        boolean truncated) {

    /**
     * 校验并冻结查询结果。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public QueryResult {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        rows = Objects.requireNonNull(rows, "rows").stream()
                .map(QueryResult::copyRow)
                .toList();
        // 负数行数会破坏分页、审计和资源限制统计。
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount must not be negative");
        }
    }

    /**
     * 复制结果行。
     *
     * @param row 原始结果行
     * @return 不可变结果行
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<Object> copyRow(List<Object> row) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(row, "row")));
    }
}
