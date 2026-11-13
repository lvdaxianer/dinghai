package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.Objects;

/**
 * 可安全返回给调用方的 DSL 诊断信息。
 *
 * @param severity 诊断级别
 * @param code 稳定错误码
 * @param path DSL 节点路径
 * @param message 脱敏诊断消息
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record DslDiagnostic(
        DiagnosticSeverity severity,
        String code,
        String path,
        String message) {

    /**
     * 校验诊断字段。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DslDiagnostic {
        severity = Objects.requireNonNull(severity, "severity");
        code = requireText(code, "code");
        path = requireText(path, "path");
        message = requireText(message, "message");
    }

    /**
     * 校验诊断文本。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 非空文本值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
