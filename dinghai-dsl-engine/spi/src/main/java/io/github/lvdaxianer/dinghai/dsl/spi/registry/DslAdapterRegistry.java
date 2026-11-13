package io.github.lvdaxianer.dinghai.dsl.spi.registry;

import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.spi.DslAdapter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 启动时构建并冻结的 DSL 适配器注册表。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class DslAdapterRegistry {

    private final Map<String, Map<Integer, DslAdapter>> adapters;

    /**
     * 校验并冻结 DSL 适配器声明。
     *
     * @param candidates 应用启动时发现的 DSL 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DslAdapterRegistry(Collection<? extends DslAdapter> candidates) {
        Map<String, Map<Integer, DslAdapter>> mutable = new LinkedHashMap<>();
        for (DslAdapter adapter : Objects.requireNonNull(candidates, "candidates")) {
            register(mutable, adapter);
        }
        this.adapters = freeze(mutable);
    }

    /**
     * 按 DSL 类型和版本精确解析适配器。
     *
     * @param request DSL 请求
     * @return 唯一匹配的 DSL 适配器
     * @throws AdapterRegistryException 类型、版本或支持关系不匹配
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DslAdapter resolve(DslRequest request) {
        DslRequest value = Objects.requireNonNull(request, "request");
        Map<Integer, DslAdapter> versions = adapters.get(value.dslType());
        if (versions == null) {
            throw missingType(value.dslType());
        }
        DslAdapter adapter = requireVersion(versions, value);
        // 允许适配器补充上下文无关的结构支持判断，但不允许跨类型或跨版本回退。
        if (!adapter.supports(value)) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DSL_REQUEST_NOT_SUPPORTED,
                    "DSL adapter rejected the registered request shape");
        }
        return adapter;
    }

    /**
     * 返回已注册 DSL 类型。
     *
     * @return 不可变 DSL 类型集合
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public Set<String> registeredTypes() {
        return adapters.keySet();
    }

    /**
     * 返回注册的类型版本组合数量。
     *
     * @return 注册数量
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public int size() {
        return adapters.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * 注册并校验单个 DSL 适配器。
     *
     * @param target 可变注册表
     * @param adapter DSL 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void register(Map<String, Map<Integer, DslAdapter>> target, DslAdapter adapter) {
        DslAdapter value = Objects.requireNonNull(adapter, "adapter");
        String type = requireCanonicalText(value.type(), "adapter.type");
        Set<Integer> versions = Objects.requireNonNull(value.supportedVersions(), "supportedVersions");
        if (versions.isEmpty()) {
            throw invalid("DSL adapter must declare at least one version: " + type);
        }
        Map<Integer, DslAdapter> versionMap = target.computeIfAbsent(type, key -> new LinkedHashMap<>());
        for (Integer version : versions) {
            registerVersion(versionMap, value, type, version);
        }
    }

    /**
     * 解析已注册的 DSL 版本。
     *
     * @param versions 类型对应的版本映射
     * @param request DSL 请求
     * @return DSL 适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static DslAdapter requireVersion(Map<Integer, DslAdapter> versions, DslRequest request) {
        DslAdapter adapter = versions.get(request.version());
        if (adapter == null) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DSL_VERSION_NOT_SUPPORTED,
                    "No DSL adapter version is registered for type: "
                            + request.dslType() + ", version: " + request.version());
        }
        return adapter;
    }

    /**
     * 注册单个 DSL 版本。
     *
     * @param target 类型版本映射
     * @param adapter DSL 适配器
     * @param type DSL 类型
     * @param version DSL 版本
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void registerVersion(
            Map<Integer, DslAdapter> target, DslAdapter adapter, String type, Integer version) {
        if (version == null || version < 1) {
            throw invalid("DSL adapter version must be positive: " + type);
        }
        if (target.putIfAbsent(version, adapter) != null) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DUPLICATE_REGISTRATION,
                    "Duplicate DSL adapter registration: " + type + ", version: " + version);
        }
    }

    /**
     * 构造 DSL 类型缺失异常。
     *
     * @param type DSL 类型
     * @return 注册异常
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static AdapterRegistryException missingType(String type) {
        return new AdapterRegistryException(
                AdapterRegistryException.DSL_TYPE_NOT_FOUND,
                "No DSL adapter is registered for type: " + type);
    }

    /**
     * 冻结嵌套注册表。
     *
     * @param source 可变注册表
     * @return 不可变注册表
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Map<String, Map<Integer, DslAdapter>> freeze(
            Map<String, Map<Integer, DslAdapter>> source) {
        Map<String, Map<Integer, DslAdapter>> frozen = new LinkedHashMap<>();
        source.forEach((type, versions) -> frozen.put(type, Map.copyOf(versions)));
        return Map.copyOf(frozen);
    }

    /**
     * 构造非法适配器声明异常。
     *
     * @param message 脱敏错误消息
     * @return 注册异常
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static AdapterRegistryException invalid(String message) {
        return new AdapterRegistryException(AdapterRegistryException.INVALID_ADAPTER, message);
    }

    /**
     * 校验规范化文本。
     *
     * @param value 文本值
     * @param name 字段名
     * @return 规范化文本
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireCanonicalText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || !normalized.equals(value)) {
            throw invalid(name + " must be a non-blank canonical value");
        }
        return normalized;
    }
}
