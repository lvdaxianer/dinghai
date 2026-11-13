package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.QueryContext;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 为后端执行任务注入宿主侧上下文传播逻辑。
 *
 * <p>公共 SPI 不依赖具体日志或线程上下文实现，宿主可以通过 Bean 注册 MDC、租户上下文
 * 或其他安全上下文装饰器。装饰器必须在任务结束后恢复或清理线程状态。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
@FunctionalInterface
public interface QueryTaskDecorator {

    /**
     * 装饰一个将在受控线程池中执行的查询任务。
     *
     * @param task 原始查询任务
     * @param context 服务端查询上下文
     * @return 装饰后的查询任务
     */
    Callable<QueryResult> decorate(Callable<QueryResult> task, QueryContext context);

    /**
     * 返回不改变上下文的默认装饰器。
     *
     * @return 空操作装饰器
     */
    static QueryTaskDecorator identity() {
        return (task, context) -> Objects.requireNonNull(task, "task");
    }
}
