package io.github.lvdaxianer.dinghai.threadpool.spring;

import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolDefinition;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 Spring 配置转换为受管线程池注册。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public final class ThreadPoolRegistrar {

    /** 从全部配置池创建受控执行器。 */
    public void register(ThreadPoolGovernor governor, Map<String, ThreadPoolProperties.Pool> pools) {
        Map<String, ThreadPoolDefinition> definitions = definitions(pools);
        validateMinimumBudget(governor.workerBudget(), definitions);
        definitions.forEach((name, definition) -> governor.register(allocated(governor.workerBudget(), definitions, definition)));
    }

    private ThreadPoolDefinition definition(String name, ThreadPoolProperties.Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("thread pool configuration is required");
        }
        return new ThreadPoolDefinition(name, pool.getMinimumSize(), pool.getMaximumSize(), pool.getWeight(),
                priority(pool.getPriority()), reliability(pool.getReliability()), pool.getQueueCapacity(),
                pool.getOwner());
    }

    /** 将配置优先级转换为稳定的领域枚举。 */
    private ThreadPoolDefinition.Priority priority(String value) {
        return enumValue(value, ThreadPoolDefinition.Priority::valueOf, "invalid thread pool priority");
    }

    /** 将配置可靠性转换为稳定的领域枚举。 */
    private ThreadPoolDefinition.Reliability reliability(String value) {
        return enumValue(value, ThreadPoolDefinition.Reliability::valueOf, "invalid thread pool reliability");
    }

    /** 将外部字符串安全转换为指定的线程池策略枚举。 */
    private <T extends Enum<T>> T enumValue(String value, EnumParser<T> parser, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            return parser.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private Map<String, ThreadPoolDefinition> definitions(Map<String, ThreadPoolProperties.Pool> pools) {
        if (pools == null) {
            throw new IllegalArgumentException("thread pool configurations are required");
        }
        Map<String, ThreadPoolDefinition> definitions = new LinkedHashMap<>();
        pools.forEach((name, pool) -> definitions.put(name, definition(name, pool)));
        return definitions;
    }

    private void validateMinimumBudget(int budget, Map<String, ThreadPoolDefinition> definitions) {
        int minimum = definitions.values().stream().mapToInt(ThreadPoolDefinition::minimumSize).sum();
        if (minimum > budget) {
            throw new IllegalArgumentException("thread pool minimum sizes exceed worker budget");
        }
    }

    private ThreadPoolDefinition allocated(int budget, Map<String, ThreadPoolDefinition> definitions,
                                           ThreadPoolDefinition definition) {
        int minimum = definitions.values().stream().mapToInt(ThreadPoolDefinition::minimumSize).sum();
        int weight = definitions.values().stream().mapToInt(ThreadPoolDefinition::weight).sum();
        int extra = (budget - minimum) * definition.weight() / weight;
        int maximum = Math.min(definition.maximumSize(), definition.minimumSize() + extra);
        return new ThreadPoolDefinition(definition.name(), definition.minimumSize(), maximum, definition.weight(),
                definition.priority(), definition.reliability(), definition.queueCapacity(), definition.owner());
    }

    /** 解析外部线程池策略字符串。 */
    @FunctionalInterface
    private interface EnumParser<T extends Enum<T>> {
        /** @param value 外部配置值 @return 解析后的枚举 */
        T parse(String value);
    }
}
