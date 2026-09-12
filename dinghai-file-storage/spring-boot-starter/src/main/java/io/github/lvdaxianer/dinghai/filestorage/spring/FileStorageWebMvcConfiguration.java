package io.github.lvdaxianer.dinghai.filestorage.spring;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
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
}
