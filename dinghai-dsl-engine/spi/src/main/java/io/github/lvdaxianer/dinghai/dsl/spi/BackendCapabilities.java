package io.github.lvdaxianer.dinghai.dsl.spi;

import java.util.Objects;
import java.util.Set;

/**
 * 后端能力快照。
 *
 * @param backendType 后端类型
 * @param capabilityVersion 能力配置版本
 * @param features 支持的能力标识
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
public record BackendCapabilities(String backendType, String capabilityVersion, Set<String> features) {

    /**
     * 使用默认能力版本创建能力快照。
     *
     * @param backendType 后端类型
     * @param features 支持的能力标识
     */
    public BackendCapabilities(String backendType, Set<String> features) {
        this(backendType, "1", features);
    }

    /**
     * 校验并冻结能力集合。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    public BackendCapabilities {
        // 后端类型必须由注册表明确识别，避免能力快照指向未知后端。
        if (backendType == null || backendType.isBlank()) {
            throw new IllegalArgumentException("backendType must not be blank");
        }
        if (capabilityVersion == null || capabilityVersion.isBlank()) {
            throw new IllegalArgumentException("capabilityVersion must not be blank");
        }
        features = Set.copyOf(Objects.requireNonNull(features, "features"));
    }
}
