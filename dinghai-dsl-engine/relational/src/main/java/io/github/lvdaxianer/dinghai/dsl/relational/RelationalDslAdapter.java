package io.github.lvdaxianer.dinghai.dsl.relational;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.spi.DslAdapter;

import java.util.Set;

/**
 * relational-query v1 的 DSL 适配器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class RelationalDslAdapter implements DslAdapter {

    private final RelationalPlanBuilder planBuilder;

    /**
     * 创建关系 DSL 适配器。
     *
     * @param schemaPolicy 表和字段白名单策略
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public RelationalDslAdapter(RelationalSchemaPolicy schemaPolicy) {
        this.planBuilder = new RelationalPlanBuilder(schemaPolicy);
    }

    /**
     * 使用统一运行时边界创建关系 DSL 适配器。
     *
     * @param schemaPolicy 表和字段白名单策略
     * @param limits 关系查询计划限制
     */
    public RelationalDslAdapter(RelationalSchemaPolicy schemaPolicy, RelationalPlanLimits limits) {
        this.planBuilder = new RelationalPlanBuilder(schemaPolicy, limits);
    }

    /**
     * 返回 DSL 类型。
     *
     * @return relational-query
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public String type() {
        return RelationalQueryParser.DSL_TYPE;
    }

    /**
     * 返回支持的 DSL 版本。
     *
     * @return 版本集合
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public Set<Integer> supportedVersions() {
        return Set.of(RelationalQueryParser.VERSION);
    }

    /**
     * 解析请求并生成关系查询计划。
     *
     * @param request DSL 请求
     * @param context 编译上下文
     * @return 后端无关关系查询计划
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    @Override
    public ExecutionPlan parse(DslRequest request, DslContext context) {
        return planBuilder.build(request, context);
    }
}
