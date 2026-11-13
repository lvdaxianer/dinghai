package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.List;
import java.util.Objects;

/**
 * DSL 解析、校验或编译失败异常。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class DslCompilationException extends RuntimeException {

    private final List<DslDiagnostic> diagnostics;

    /**
     * 创建带诊断列表的编译异常。
     *
     * @param diagnostics 诊断列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DslCompilationException(List<DslDiagnostic> diagnostics) {
        super(requireDiagnostics(diagnostics).get(0).message());
        this.diagnostics = List.copyOf(diagnostics);
    }

    /**
     * 返回不可变诊断列表。
     *
     * @return 诊断列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public List<DslDiagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * 校验诊断列表。
     *
     * @param diagnostics 诊断列表
     * @return 原始诊断列表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static List<DslDiagnostic> requireDiagnostics(List<DslDiagnostic> diagnostics) {
        List<DslDiagnostic> values = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        // 没有诊断内容时无法构造稳定且可脱敏的异常消息。
        if (values.isEmpty()) {
            throw new IllegalArgumentException("diagnostics must not be empty");
        }
        return values;
    }
}
