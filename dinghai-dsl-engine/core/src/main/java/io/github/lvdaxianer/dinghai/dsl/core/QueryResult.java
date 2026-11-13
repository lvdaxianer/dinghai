package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
        for (List<Object> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException("row size must match columns size");
            }
        }
    }

    /**
     * 按输出列名生成便于调用方读取的对象化记录。
     *
     * @return 不可变对象化记录，空值字段仍保留为 {@code null}
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public List<Map<String, Object>> records() {
        return rows.stream()
                .map(this::toRecord)
                .toList();
    }

    /**
     * 将单行值映射为按列顺序排列的对象。
     *
     * @param row 结果行
     * @return 不可变对象化记录
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private Map<String, Object> toRecord(List<Object> row) {
        Map<String, Object> record = new LinkedHashMap<>();
        Map<String, Integer> nextSuffixByName = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            String columnName = columns.get(index).name();
            String key = uniqueKey(record, columnName, nextSuffixByName);
            record.put(key, row.get(index));
        }
        return Collections.unmodifiableMap(record);
    }

    /**
     * 为重复列名生成确定性后缀，避免后一个值覆盖前一个值。
     *
     * @param record 当前记录
     * @param columnName 原始列名
     * @param nextSuffixByName 列名后缀计数
     * @return 当前记录中的唯一键
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String uniqueKey(
            Map<String, Object> record,
            String columnName,
            Map<String, Integer> nextSuffixByName) {
        int suffix = nextSuffixByName.getOrDefault(columnName, 1);
        String key = suffix == 1 ? columnName : columnName + "#" + suffix;
        while (record.containsKey(key)) {
            suffix++;
            key = columnName + "#" + suffix;
        }
        nextSuffixByName.put(columnName, suffix + 1);
        return key;
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
