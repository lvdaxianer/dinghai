package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** 将对象 key 安全解析为本地内容和元数据路径。 */
final class LocalObjectPathResolver {
    private static final String METADATA_DIRECTORY_SUFFIX = ".dinghai-metadata";
    private final Path root;
    private final Path metadataRoot;

    /** 创建根目录封闭的路径解析器。 */
    LocalObjectPathResolver(Path configuredRoot) {
        Objects.requireNonNull(configuredRoot, "root");
        try {
            Files.createDirectories(configuredRoot);
            root = configuredRoot.toRealPath();
            metadataRoot = root.resolveSibling(root.getFileName() + METADATA_DIRECTORY_SUFFIX);
            Files.createDirectories(metadataRoot);
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "local storage root is unavailable", exception);
        }
    }

    /** 解析可读写的对象路径并校验父目录未越出根目录。 */
    Path resolve(ObjectKey key) {
        Objects.requireNonNull(key, "key");
        Path candidate = root.resolve(key.value()).normalize();
        if (!candidate.startsWith(root)) {
            throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                    "object key resolves outside storage root", null);
        }
        verifyParent(candidate);
        if (Files.isSymbolicLink(candidate)) {
            throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                    "object key resolves to a symbolic link", null);
        }
        return candidate;
    }

    /** 解析不可由对象 key 直接访问的元数据文件。 */
    Path metadata(ObjectKey key) {
        String digest = digestKey(key);
        Path candidate = metadataRoot.resolve(digest + ".properties").normalize();
        if (!candidate.startsWith(metadataRoot)) {
            throw failure(FileStorageErrorCode.INTERNAL_ERROR,
                    "metadata path is invalid", null);
        }
        return candidate;
    }

    /** 获取标准化后的存储根目录。 */
    Path root() {
        return root;
    }

    private void verifyParent(Path candidate) {
        Path parent = findExistingParent(candidate.getParent());
        try {
            if (!parent.toRealPath().startsWith(root)) {
                throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                        "object key resolves outside storage root", null);
            }
        } catch (IOException exception) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "object parent cannot be inspected", exception);
        }
    }

    private static Path findExistingParent(Path candidate) {
        Path current = candidate;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current == null) {
            throw failure(FileStorageErrorCode.STORAGE_UNAVAILABLE,
                    "object parent cannot be inspected", null);
        }
        return current;
    }

    private static String digestKey(ObjectKey key) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(key.value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw failure(FileStorageErrorCode.INTERNAL_ERROR,
                    "metadata digest is unavailable", exception);
        }
    }

    private static FileStorageException failure(FileStorageErrorCode code,
                                                String message, Throwable cause) {
        return new FileStorageException(code, message, cause);
    }
}
