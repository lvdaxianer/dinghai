package io.github.lvdaxianer.dinghai.filestorage.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 不含 provider 物理信息的逻辑文件引用。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public record FileReference(String storageAlias, ObjectKey key) {
    private static final Pattern STORAGE_ALIAS = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** 创建并校验逻辑文件引用。 */
    public FileReference {
        storageAlias = requireAlias(storageAlias);
        key = Objects.requireNonNull(key, "key");
    }

    /** 从逻辑别名和原始对象 key 创建引用。 */
    public static FileReference of(String storageAlias, String key) {
        return new FileReference(storageAlias, ObjectKey.of(key));
    }

    /** 校验逻辑别名不会泄漏物理 provider 信息。 */
    private static String requireAlias(String value) {
        if (value == null || !STORAGE_ALIAS.matcher(value).matches()) {
            throw new IllegalArgumentException("storageAlias is invalid");
        }
        return value;
    }
}
