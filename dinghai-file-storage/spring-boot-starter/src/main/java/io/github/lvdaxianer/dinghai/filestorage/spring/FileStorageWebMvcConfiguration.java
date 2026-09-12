package io.github.lvdaxianer.dinghai.filestorage.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将文件存储流式响应绑定到专用异步执行器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageWebMvcConfiguration implements WebMvcConfigurer {

    /** 文件存储 Web 异步执行器。 */
    private final ThreadPoolTaskExecutor executor;

    /**
     * 创建 Web MVC 异步配置。
     *
     * @param executor 文件存储 Web 异步执行器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public FileStorageWebMvcConfiguration(ThreadPoolTaskExecutor executor) {
        this.executor = java.util.Objects.requireNonNull(executor, "executor");
    }

    /**
     * 为 StreamingResponseBody 配置专用执行器。
     *
     * @param configurer Spring MVC 异步支持配置器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
    }

    /**
     * 注册异步读取完成后的许可释放拦截器。
     *
     * @param registry MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                        Object handler, Exception exception) {
                releaseReadPermit(request);
            }
        });
    }

    /**
     * 释放 sendfile 响应在请求上下文中保存的读取许可。
     *
     * @param request 已完成的 HTTP 请求
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    static void releaseReadPermit(HttpServletRequest request) {
        Object permit = request.getAttribute(FileStorageWebController.READ_PERMIT_ATTRIBUTE);
        if (permit instanceof WebTransferLimiter.Permit readPermit) {
            readPermit.close();
        }
    }
}
