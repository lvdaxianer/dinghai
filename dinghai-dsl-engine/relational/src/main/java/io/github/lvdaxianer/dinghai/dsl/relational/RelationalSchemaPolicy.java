package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;

import java.util.Optional;

/**
 * 宿主应用提供的关系 schema 白名单策略。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface RelationalSchemaPolicy {

    /**
     * 解析允许访问的逻辑表。
     *
     * @param logicalName DSL 中的逻辑表名
     * @param context DSL 编译上下文
     * @return 允许访问的表定义
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    Optional<RelationalTableDefinition> resolveTable(String logicalName, DslContext context);
}
