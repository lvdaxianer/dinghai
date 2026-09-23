package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.lang.reflect.Method;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 在 MVC 初始化完成后为文件控制器注册已校验的动态路由。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-23
 */
final class FileStorageWebRouteRegistrar implements SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final FileStorageWebController controller;
    private final FileStorageWebRoutes routes;

    /**
     * 创建文件 Web 路由注册器。
     *
     * @param mappings MVC 路由映射器提供者
     * @param controller 文件 Web 业务控制器
     * @param routes 已校验的文件路由
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    FileStorageWebRouteRegistrar(ObjectProvider<RequestMappingHandlerMapping> mappings,
                                 FileStorageWebController controller, FileStorageWebRoutes routes) {
        this.mappings = mappings;
        this.controller = controller;
        this.routes = routes;
    }

    /**
     * 在 MVC 单例完成初始化后注册所有文件路由。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    @Override
    public void afterSingletonsInstantiated() {
        mappings.ifAvailable(mapping -> register(mapping, controller, routes));
    }

    /**
     * 向给定 MVC 映射器注册文件控制器业务方法。
     *
     * @param mapping MVC 映射器
     * @param controller 文件 Web 业务控制器
     * @param routes 已校验的文件路由
     * @author lvdaxianer@yeah.net
     * @date 2026-09-23
     */
    static void register(RequestMappingHandlerMapping mapping, FileStorageWebController controller,
                         FileStorageWebRoutes routes) {
        registerUpload(mapping, controller, routes);
        registerObjectReads(mapping, controller, routes);
        registerStableReads(mapping, controller, routes);
        if (routes.legacyRead() != null) {
            registerGet(mapping, controller, "keyRead", routes.legacyRead());
        }
    }

    /** 注册上传映射。 */
    private static void registerUpload(RequestMappingHandlerMapping mapping,
                                       FileStorageWebController controller, FileStorageWebRoutes routes) {
        RequestMappingInfo info = RequestMappingInfo.paths(routes.upload()).methods(RequestMethod.PUT)
                .produces(MediaType.APPLICATION_JSON_VALUE).build();
        register(mapping, info, controller, "upload");
    }

    /** 注册下载、预览和真实对象 key 读取映射。 */
    private static void registerObjectReads(RequestMappingHandlerMapping mapping,
                                            FileStorageWebController controller, FileStorageWebRoutes routes) {
        registerGet(mapping, controller, "download", routes.download());
        registerGet(mapping, controller, "preview", routes.preview());
        registerGet(mapping, controller, "keyRead", routes.keyRead());
        registerGet(mapping, controller, "keyPreview", routes.keyPreview());
        registerGet(mapping, controller, "keyDownload", routes.keyDownload());
    }

    /** 注册稳定文件 ID 的版本化和无版本读取映射。 */
    private static void registerStableReads(RequestMappingHandlerMapping mapping,
                                            FileStorageWebController controller, FileStorageWebRoutes routes) {
        registerGet(mapping, controller, "stableVersionedRead", routes.stableVersionedRead());
        registerGet(mapping, controller, "stableVersionedPreview", routes.stableVersionedPreview());
        registerGet(mapping, controller, "stableVersionedDownload", routes.stableVersionedDownload());
        if (!routes.suppressUnversionedStableRoutes()) {
            registerGet(mapping, controller, "stableRead", routes.stableRead());
            registerGet(mapping, controller, "stablePreview", routes.stablePreview());
            registerGet(mapping, controller, "stableDownload", routes.stableDownload());
        }
    }

    /** 注册 GET 路由。 */
    private static void registerGet(RequestMappingHandlerMapping mapping,
                                    FileStorageWebController controller, String method, String path) {
        register(mapping, RequestMappingInfo.paths(path).methods(RequestMethod.GET).build(), controller, method);
    }

    /** 注册一个已解析业务方法。 */
    private static void register(RequestMappingHandlerMapping mapping, RequestMappingInfo info,
                                 FileStorageWebController controller, String methodName) {
        Method method = findMethod(methodName);
        mapping.registerMapping(info, controller, method);
    }

    /** 按名称定位唯一的公开控制器方法。 */
    private static Method findMethod(String methodName) {
        for (Method method : FileStorageWebController.class.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new IllegalStateException("file storage web handler method is missing");
    }
}
