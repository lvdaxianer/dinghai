package io.github.lvdaxianer.dinghai.filestorage.spring;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 由宿主应用提供 Web 文件传输限流 key 的扩展点。
 *
 * <p>实现应仅使用可信、轻量的宿主身份上下文；组件不会推导、记录或回显该 key。
 * 未注册实现、返回空白值或抛出运行时异常时，组件会降级为仅使用全局并发限制。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
@FunctionalInterface
public interface WebTransferRateLimitKeyResolver {

    /**
     * 根据当前请求解析宿主定义的限流 key。
     *
     * @param request 当前 HTTP 请求
     * @return 非空白限流 key；返回空值或空白值时仅应用全局限流
     */
    String resolve(HttpServletRequest request);
}
