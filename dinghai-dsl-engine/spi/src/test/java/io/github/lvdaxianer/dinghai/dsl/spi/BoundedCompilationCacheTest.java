package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.GenericExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.core.QueryColumn;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证计划和编译模板缓存的容量、TTL、版本键和敏感值隔离。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
class BoundedCompilationCacheTest {

    private static final CacheKey KEY = new CacheKey(
            "relational-query", 1, "hash-1", "mysql", "adapter-1", "compiler-1", "capability-1", "schema-1", "policy-1");

    /**
     * 验证计划缓存按容量淘汰且版本变化不会命中旧条目。
     */
    @Test
    void evictsOldPlansAndSeparatesVersions() {
        BoundedPlanCache cache = new BoundedPlanCache(1, Duration.ofMinutes(1));
        GenericExecutionPlan first = new GenericExecutionPlan("relational-query", 1, Map.of("id", 1));
        GenericExecutionPlan second = new GenericExecutionPlan("relational-query", 1, Map.of("id", 2));

        cache.put(KEY, first);
        cache.put(new CacheKey(
                "relational-query", 1, "hash-2", "mysql", "adapter-1", "compiler-1", "capability-1", "schema-1", "policy-1"), second);

        assertTrue(cache.get(KEY).isEmpty());
        assertEquals(second, cache.get(new CacheKey(
                "relational-query", 1, "hash-2", "mysql", "adapter-1", "compiler-1", "capability-1", "schema-1", "policy-1")).orElseThrow());
    }

    /**
     * 验证缓存 TTL 到期后返回 miss。
     */
    @Test
    void expiresEntriesAfterTtl() {
        BoundedPlanCache cache = new BoundedPlanCache(2, Duration.ZERO);
        cache.put(KEY, new GenericExecutionPlan("relational-query", 1, Map.of()));

        assertTrue(cache.get(KEY).isEmpty());
    }

    /**
     * 验证后端能力版本变化不会复用旧缓存条目。
     */
    @Test
    void separatesBackendCapabilityVersions() {
        BoundedPlanCache cache = new BoundedPlanCache(2, Duration.ofMinutes(1));
        cache.put(KEY, new GenericExecutionPlan("relational-query", 1, Map.of("id", 1)));

        CacheKey changedCapabilities = new CacheKey(
                "relational-query", 1, "hash-1", "mysql", "adapter-1", "compiler-1", "capability-2", "schema-1", "policy-1");

        assertTrue(cache.get(changedCapabilities).isEmpty());
    }

    /**
     * 验证编译模板不携带参数值，绑定时才注入当前请求值。
     */
    @Test
    void keepsCompiledTemplateFreeOfParameterValues() {
        CompiledCommandTemplate template = new CompiledCommandTemplate(
                "sql", "SELECT ?", List.of(new ParameterTemplate("status", "string")),
                List.of(new QueryColumn("status", "string")));

        assertNull(template.parameterSchema().getFirst().emptyBinding().value());
        CompiledCommand command = template.bind(List.of("OPEN"));

        assertEquals("OPEN", command.parameters().getFirst().value());
        assertEquals("SELECT ?", command.commandBody());
    }

    /**
     * 验证编译模板缓存同样有容量边界。
     */
    @Test
    void boundsCompiledTemplateCache() {
        BoundedCompiledTemplateCache cache = new BoundedCompiledTemplateCache(1, Duration.ofMinutes(1));
        CompiledCommandTemplate template = new CompiledCommandTemplate(
                "sql", "SELECT 1", List.of(), List.of());
        CacheKey other = new CacheKey(
                "relational-query", 1, "other", "mysql", "adapter-1", "compiler-1", "capability-1", "schema-1", "policy-1");

        cache.put(KEY, template);
        cache.put(other, template);

        assertFalse(cache.get(KEY).isPresent());
        assertEquals(1, cache.size());
    }

    /**
     * 验证 AST 缓存可以清空并返回 miss。
     */
    @Test
    void invalidatesAstCache() {
        BoundedAstCache<String> cache = new BoundedAstCache<>(2, Duration.ofMinutes(1));
        cache.put(KEY, "safe-ast");

        assertEquals("safe-ast", cache.get(KEY).orElseThrow());
        cache.invalidateAll();
        assertTrue(cache.get(KEY).isEmpty());
    }

    /**
     * 验证缓存读写故障只绕过缓存，不阻断主流程。
     */
    @Test
    void bypassesCacheFailures() {
        assertTrue(CacheAccess.readOrMiss(() -> {
            throw new IllegalStateException("cache down");
        }).isEmpty());

        CacheAccess.writeOrIgnore(() -> {
            throw new IllegalStateException("cache down");
        });
    }
}
