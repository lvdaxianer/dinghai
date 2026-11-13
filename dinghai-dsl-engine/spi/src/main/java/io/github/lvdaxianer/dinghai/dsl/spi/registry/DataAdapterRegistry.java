package io.github.lvdaxianer.dinghai.dsl.spi.registry;

import io.github.lvdaxianer.dinghai.dsl.core.ExecutionPlan;
import io.github.lvdaxianer.dinghai.dsl.spi.BackendCapabilities;
import io.github.lvdaxianer.dinghai.dsl.spi.DataAdapter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 启动时构建并冻结的数据适配器注册表。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public final class DataAdapterRegistry {

    private final Map<String, Registration> adapters;

    /**
     * 校验并冻结数据适配器声明。
     *
     * @param candidates 应用启动时发现的数据适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DataAdapterRegistry(Collection<? extends DataAdapter> candidates) {
        Map<String, Registration> mutable = new LinkedHashMap<>();
        for (DataAdapter adapter : Objects.requireNonNull(candidates, "candidates")) {
            register(mutable, adapter);
        }
        this.adapters = Map.copyOf(mutable);
    }

    /**
     * 按后端类型精确解析数据适配器。
     *
     * @param type 后端类型
     * @return 唯一匹配的数据适配器
     * @throws AdapterRegistryException 类型未注册
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DataAdapter resolve(String type) {
        String normalized = requireLookupType(type);
        Registration registration = adapters.get(normalized);
        if (registration == null) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DATA_ADAPTER_TYPE_NOT_FOUND,
                    "No data adapter is registered for type: " + normalized);
        }
        return registration.adapter();
    }

    /**
     * 按后端类型和计划类型精确解析数据适配器。
     *
     * @param type 后端类型
     * @param plan 执行计划
     * @return 可执行该计划的数据适配器
     * @throws AdapterRegistryException 类型未注册或计划不支持
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public DataAdapter resolve(String type, ExecutionPlan plan) {
        Registration registration = registration(type);
        ExecutionPlan value = Objects.requireNonNull(plan, "plan");
        if (!registration.planTypes().contains(value.planType())) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DATA_ADAPTER_PLAN_NOT_SUPPORTED,
                    "Data adapter does not support plan type: " + value.planType());
        }
        return registration.adapter();
    }

    /**
     * 返回已注册后端类型。
     *
     * @return 不可变后端类型集合
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public Set<String> registeredTypes() {
        return adapters.keySet();
    }

    /**
     * 返回已注册数据适配器数量。
     *
     * @return 注册数量
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public int size() {
        return adapters.size();
    }

    /**
     * 获取已校验的数据适配器注册信息。
     *
     * @param type 后端类型
     * @return 注册信息
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private Registration registration(String type) {
        String normalized = requireLookupType(type);
        Registration registration = adapters.get(normalized);
        if (registration == null) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DATA_ADAPTER_TYPE_NOT_FOUND,
                    "No data adapter is registered for type: " + normalized);
        }
        return registration;
    }

    /**
     * 注册并校验单个数据适配器。
     *
     * @param target 可变注册表
     * @param adapter 数据适配器
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static void register(Map<String, Registration> target, DataAdapter adapter) {
        DataAdapter value = Objects.requireNonNull(adapter, "adapter");
        String type = requireCanonicalText(value.type(), "adapter.type");
        if (target.containsKey(type)) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DUPLICATE_REGISTRATION,
                    "Duplicate data adapter registration: " + type);
        }
        Set<String> planTypes = copyPlanTypes(value.supportedPlanTypes(), type);
        BackendCapabilities capabilities = Objects.requireNonNull(value.capabilities(), "capabilities");
        if (!type.equals(capabilities.backendType())) {
            throw invalid("Data adapter capability type does not match adapter type: " + type);
        }
        target.put(type, new Registration(value, planTypes));
    }

    /**
     * 复制并校验计划类型集合。
     *
     * @param source 原始计划类型集合
     * @param adapterType 适配器类型
     * @return 不可变计划类型集合
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Set<String> copyPlanTypes(Set<String> source, String adapterType) {
        Set<String> values = new LinkedHashSet<>();
        for (String planType : Objects.requireNonNull(source, "supportedPlanTypes")) {
            String normalized = requireCanonicalText(planType, "supportedPlanType");
            values.add(normalized);
        }
        if (values.isEmpty()) {
            throw invalid("Data adapter must declare at least one plan type: " + adapterType);
        }
        return Set.copyOf(values);
    }

    /**
     * 校验查找类型。
     *
     * @param type 后端类型
     * @return 规范化后端类型
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static String requireLookupType(String type) {
        String normalized = Objects.requireNonNull(type, "type").trim();
        if (normalized.isEmpty()) {
            throw new AdapterRegistryException(
                    AdapterRegistryException.DATA_ADAPTER_TYPE_NOT_FOUND,
                    "Data adapter type must not be blank");
        }
        return normalized;
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
     * 已校验的数据适配器注册信息。
     *
     * @param adapter 数据适配器
     * @param planTypes 计划类型快照
     */
    private record Registration(DataAdapter adapter, Set<String> planTypes) {
    }
}
