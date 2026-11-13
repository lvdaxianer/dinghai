package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.relational.ConstraintPlacement;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalColumnDefinition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalConstraint;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalPlanExtension;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalSchemaPolicy;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalTableDefinition;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.ComparisonOperator;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryAst.RelationalField;
import io.github.lvdaxianer.dinghai.dsl.relational.RelationalQueryPlan;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanPolicy;
import io.github.lvdaxianer.dinghai.dsl.spi.PlanExtensionContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 真实数据库测试应用的服务端白名单、权限和计划扩展示例。
 */
@Configuration
public class TestQueryConfiguration {

    private static final Map<String, RelationalTableDefinition> TABLES = tables();

    /**
     * 提供固定表字段白名单；请求只能使用这些逻辑名称。
     *
     * @return schema 白名单策略
     */
    @Bean
    public RelationalSchemaPolicy testSchemaPolicy() {
        return (logicalName, context) -> Optional.ofNullable(TABLES.get(logicalName));
    }

    /**
     * 验证服务端身份和数据域已经进入可信上下文。
     *
     * @return 计划权限策略
     */
    @Bean
    public PlanPolicy testPlanPolicy() {
        return (plan, context) -> {
            requireAttribute(context, "userId");
            requireAttribute(context, "domainId");
        };
    }

    /**
     * API 边界拒绝未声明字段，避免客户端静默传入 adapter、SQL 或身份字段。
     *
     * @return Jackson 反序列化约束
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer rejectUnknownRequestFields() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * 通过代码 Bean 注入逻辑删除和数据域约束，不从请求或 YAML 读取规则。
     *
     * @param properties 测试应用可信身份配置
     * @return 关系计划扩展
     */
    @Bean
    public RelationalPlanExtension testScopePlanExtension(TestQueryProperties properties) {
        return new RelationalPlanExtension() {
            @Override
            public String id() {
                return "test-active-domain-scope";
            }

            @Override
            public int order() {
                return 100;
            }

            @Override
            public List<RelationalConstraint> constraints(
                    RelationalQueryPlan plan, PlanExtensionContext context) {
                String domainId = context.queryContext().attributes().get("domainId");
                if (domainId == null || domainId.isBlank()) {
                    throw new TestAuthorizationException("DATA_SCOPE_REQUIRED", "server data scope is missing");
                }
                List<RelationalConstraint> constraints = new java.util.ArrayList<>();
                String fromAlias = plan.query().from().alias();
                for (Map.Entry<String, RelationalTableDefinition> entry : plan.tablesByAlias().entrySet()) {
                    RelationalTableDefinition table = entry.getValue();
                    String alias = entry.getKey();
                    ConstraintPlacement placement = alias.equals(fromAlias)
                            ? ConstraintPlacement.WHERE : ConstraintPlacement.JOIN_ON;
                    if (table.columns().containsKey("active")) {
                        constraints.add(RelationalConstraint.of(
                                new RelationalField(alias, "active"),
                                ComparisonOperator.EQ,
                                table.columns().get("active").logicalType(),
                                1,
                                placement));
                    }
                    if (table.columns().containsKey("domain_id")) {
                        constraints.add(RelationalConstraint.of(
                                new RelationalField(alias, "domain_id"),
                                ComparisonOperator.EQ,
                                table.columns().get("domain_id").logicalType(),
                                domainId,
                                placement));
                    }
                }
                return List.copyOf(constraints);
            }
        };
    }

    private static void requireAttribute(QueryContext context, String name) {
        String value = context.attributes().get(name);
        if (value == null || value.isBlank()) {
            throw new TestAuthorizationException("DATA_SCOPE_REQUIRED", "server query scope is missing");
        }
    }

    private static Map<String, RelationalTableDefinition> tables() {
        Map<String, RelationalTableDefinition> tables = new LinkedHashMap<>();
        tables.put("fact_wireless_outage_timeline", table(
                "fact_wireless_outage_timeline",
                Map.of(
                        "outage_id", column("outage_id", "long"),
                        "event_date", column("event_date", "date"),
                        "status", column("status", "string"),
                        "severity", column("severity", "integer"),
                        "asset_id", column("asset_id", "long"),
                        "active", column("active", "integer"),
                        "domain_id", column("domain_id", "string"))));
        tables.put("outage_asset", table(
                "outage_asset",
                Map.of(
                        "asset_id", column("asset_id", "long"),
                        "asset_name", column("asset_name", "string"),
                        "active", column("active", "integer"),
                        "domain_id", column("domain_id", "string"))));
        return Map.copyOf(tables);
    }

    private static RelationalTableDefinition table(
            String physicalName, Map<String, RelationalColumnDefinition> columns) {
        return new RelationalTableDefinition(physicalName, physicalName, columns);
    }

    private static RelationalColumnDefinition column(String physicalName, String logicalType) {
        return new RelationalColumnDefinition(physicalName, physicalName, logicalType);
    }
}
