package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileReference;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.StableFileIdCodec;

import java.util.Objects;

/**
 * 负责当前逻辑存储别名下稳定文件 ID 的生成与解析。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public final class StableFileAccessService {
    /** 当前部署公开的逻辑存储别名。 */
    private final String storageAlias;
    /** 稳定 ID 的认证加密编解码器。 */
    private final StableFileIdCodec codec;

    /**
     * 创建稳定文件读取服务。
     *
     * @param storageAlias 逻辑存储别名
     * @param keyMaterial 部署提供的 ID 密钥材料
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public StableFileAccessService(String storageAlias, byte[] keyMaterial) {
        FileReference reference = FileReference.of(storageAlias, "validation");
        this.storageAlias = reference.storageAlias();
        codec = new StableFileIdCodec(Objects.requireNonNull(keyMaterial, "keyMaterial"));
    }

    /**
     * 为当前逻辑存储中的对象生成稳定 ID。
     *
     * @param key 对象 key
     * @return 不含物理存储信息的稳定 ID
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public String identifier(ObjectKey key) {
        return codec.encode(new FileReference(storageAlias, Objects.requireNonNull(key, "key")));
    }

    /**
     * 验证稳定 ID 并解析当前逻辑存储中的对象 key。
     *
     * @param identifier 稳定 ID
     * @return 已验证对象 key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ObjectKey resolve(String identifier) {
        FileReference reference = codec.decode(identifier);
        if (!storageAlias.equals(reference.storageAlias())) {
            throw invalidIdentifier();
        }
        return reference.key();
    }

    /** 解析 URL 已拆分版本段的稳定 ID 负载。 */
    public ObjectKey resolveVersioned(String version, String payload) {
        return resolve(version + "." + Objects.requireNonNull(payload, "payload"));
    }

    /** 创建不泄漏引用明文的稳定 ID 错误。 */
    private FileStorageException invalidIdentifier() {
        return new FileStorageException(FileStorageErrorCode.INVALID_REQUEST,
                "stable file ID is invalid");
    }
}
