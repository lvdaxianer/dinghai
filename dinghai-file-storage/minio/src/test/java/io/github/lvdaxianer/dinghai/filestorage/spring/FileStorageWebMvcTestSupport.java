package io.github.lvdaxianer.dinghai.filestorage.spring;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 为无注解动态路由控制器创建独立 MVC 测试客户端。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
final class FileStorageWebMvcTestSupport {

    private FileStorageWebMvcTestSupport() {
    }

    /**
     * 创建包含配置化文件路由的独立 MVC 构建器。
     *
     * @param controller 文件 Web 业务控制器
     * @return 已注册动态文件映射的 MVC 构建器
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    static StandaloneMockMvcBuilder standaloneSetup(FileStorageWebController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomHandlerMapping(() -> new TestRouteMapping(controller));
    }

    /**
     * 在独立测试环境完成默认初始化后注册动态文件路由。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-10-13
     */
    private static final class TestRouteMapping extends RequestMappingHandlerMapping {

        private final FileStorageWebController controller;

        private TestRouteMapping(FileStorageWebController controller) {
            this.controller = controller;
        }

        @Override
        public void afterPropertiesSet() {
            super.afterPropertiesSet();
            FileStorageWebRouteRegistrar.register(this, controller, controller.routes());
        }
    }
}
