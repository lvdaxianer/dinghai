package io.github.lvdaxianer.dinghai.dsl.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 提供 DSL 输入模型使用的递归不可变值复制能力。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-11-13
 */
final class ImmutableValues {

    /**
     * 禁止创建工具类实例。
     */
    private ImmutableValues() {
    }

    /**
     * 复制并冻结键值对象。
     *
     * @param source 原始键值对象
     * @return 不可变键值对象
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    static Map<String, Object> copyMap(Map<String, Object> source) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> copied = new LinkedHashMap<>(capacity(source.size()));
        source.forEach((key, value) -> copied.put(
                Objects.requireNonNull(key, "map key"), copyValue(value)));
        return Collections.unmodifiableMap(copied);
    }

    /**
     * 递归复制并冻结 DSL 值。
     *
     * @param value 原始值
     * @return 不可变值
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            // Map 需要递归冻结，避免调用方通过嵌套对象修改请求。
            return copyGenericMap(map);
        }
        if (value instanceof List<?> list) {
            // List 允许 JSON null，因此使用不拒绝 null 的不可变包装。
            return Collections.unmodifiableList(new ArrayList<>(list.stream()
                    .map(ImmutableValues::copyValue)
                    .toList()));
        }
        if (value instanceof Set<?> set) {
            // Set 同样需要递归冻结，并保持输入值的 null 约束由调用方决定。
            return Collections.unmodifiableSet(new LinkedHashSet<>(set.stream()
                    .map(ImmutableValues::copyValue)
                    .toList()));
        }
        return value;
    }

    /**
     * 复制非泛型键值对象。
     *
     * @param source 原始键值对象
     * @return 不可变键值对象
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static Map<String, Object> copyGenericMap(Map<?, ?> source) {
        Map<String, Object> copied = new LinkedHashMap<>(capacity(source.size()));
        source.forEach((key, value) -> {
            // JSON 对象键必须是字符串，禁止隐式转换造成字段歧义。
            if (!(key instanceof String textKey)) {
                throw new IllegalArgumentException("map key must be a string");
            }
            copied.put(Objects.requireNonNull(textKey, "map key"), copyValue(value));
        });
        return Collections.unmodifiableMap(copied);
    }

    /**
     * 计算有界集合的合理初始容量。
     *
     * @param size 元素数量
     * @return 集合初始容量
     * @author lvdaxianer@yeah.net
     * @date 2026-11-13
     */
    private static int capacity(int size) {
        return Math.max(4, (int) (size / 0.75f) + 1);
    }
}
