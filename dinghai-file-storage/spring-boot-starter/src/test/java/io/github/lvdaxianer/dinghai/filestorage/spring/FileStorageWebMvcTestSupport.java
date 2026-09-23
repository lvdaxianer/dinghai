package io.github.lvdaxianer.dinghai.filestorage.spring;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 为无注解动态路由控制器创建独立 MVC 测试客户端。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-23
 */
final class FileStorageWebMvcTestSupport {

    private FileStorageWebMvcTestSupport() {
    }

    /**
     * 创建包含配置化文件路由的独立 MVC 构建器。
     *
     * @param controller 文件 Web 业务控制器
     * @param otherControllers 额外的注解控制器
     * @return 已注册动态文件映射的 MVC 构建器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    static StandaloneMockMvcBuilder standaloneSetup(FileStorageWebController controller,
                                                     Object... otherControllers) {
        Object[] controllers = new Object[otherControllers.length + 1];
        controllers[0] = controller;
        System.arraycopy(otherControllers, 0, controllers, 1, otherControllers.length);
        return MockMvcBuilders.standaloneSetup(controllers)
                .setCustomHandlerMapping(() -> new TestRouteMapping(controller));
    }

    /**
     * 在独立测试环境完成默认初始化后注册动态文件路由。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
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
