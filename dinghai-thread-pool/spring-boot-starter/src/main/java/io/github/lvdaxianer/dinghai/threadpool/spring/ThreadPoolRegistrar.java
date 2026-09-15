package io.github.lvdaxianer.dinghai.threadpool.spring;

import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolDefinition;
import io.github.lvdaxianer.dinghai.threadpool.core.ThreadPoolGovernor;
import java.util.Map;
import java.util.LinkedHashMap;

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
        return new ThreadPoolDefinition(name, pool.getMinimumSize(), pool.getMaximumSize(), pool.getWeight(),
                ThreadPoolDefinition.Priority.valueOf(pool.getPriority()),
                ThreadPoolDefinition.Reliability.valueOf(pool.getReliability()), pool.getQueueCapacity());
    }

    private Map<String, ThreadPoolDefinition> definitions(Map<String, ThreadPoolProperties.Pool> pools) {
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
                definition.priority(), definition.reliability(), definition.queueCapacity());
    }
}
