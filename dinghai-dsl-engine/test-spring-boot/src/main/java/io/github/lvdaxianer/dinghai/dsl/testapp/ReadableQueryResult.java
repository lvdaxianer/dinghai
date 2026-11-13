package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.QueryColumn;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;

import java.util.List;
import java.util.Map;

/**
 * 测试 HTTP 接口的可读查询结果视图。
 *
 * @param columns 输出列描述
 * @param rows 兼容已有调用方的数组行
 * @param records 按输出列名组织的对象行
 * @param rowCount 返回行数
 * @param truncated 是否因资源限制截断
 */
public record ReadableQueryResult(
        List<QueryColumn> columns,
        List<List<Object>> rows,
        List<Map<String, Object>> records,
        long rowCount,
        boolean truncated) {

    /**
     * 将核心查询结果转换为 HTTP 可读视图。
     *
     * @param result 核心查询结果
     * @return HTTP 可读查询结果
     */
    public static ReadableQueryResult from(QueryResult result) {
        return new ReadableQueryResult(
                result.columns(),
                result.rows(),
                result.records(),
                result.rowCount(),
                result.truncated());
    }
}
