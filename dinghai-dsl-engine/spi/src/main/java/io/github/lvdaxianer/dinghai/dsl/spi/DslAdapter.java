package io.github.lvdaxianer.dinghai.dsl.spi;

import io.github.lvdaxianer.dinghai.dsl.core.DslContext;
import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;

import java.util.Objects;
import java.util.Set;

/**
 * 单个 DSL 类型和版本的编译前端契约。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public interface DslAdapter {

    /**
     * 返回 DSL 类型。
     *
     * @return DSL 类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    String type();

    /**
     * 返回支持的 DSL 版本。
     *
     * @return DSL 版本集合
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    Set<Integer> supportedVersions();

    /**
     * 判断是否支持请求。
     *
     * @param request DSL 请求
     * @return 是否支持
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    default boolean supports(DslRequest request) {
        Objects.requireNonNull(request, "request");
        return type().equals(request.dslType()) && supportedVersions().contains(request.version());
    }

    /**
     * 解析并编译 DSL 请求。
     *
     * @param request DSL 请求
     * @param context 编译上下文
     * @return 后端无关执行计划
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    ExecutionPlan parse(DslRequest request, DslContext context);
}
