package io.github.lvdaxianer.dinghai.threadpool.governance.server;

/**
 * 注册子控实例的管理请求。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record InstanceRegistrationRequest(String instanceId, String baseUrl, String environment, String version) {
    /** 兼容旧调用方，并使用稳定的 legacy 世代标识。 */
    public InstanceRegistrationRequest(String instanceId, String baseUrl, String environment) {
        this(instanceId, baseUrl, environment, "legacy");
    }
}
