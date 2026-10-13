package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
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
        return resolveCandidate(key, "object key");
    }

    /** 解析本地前缀路径并拒绝 root 本身及符号链接目标。 */
    Path resolvePrefix(ObjectKey prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Path candidate = resolveCandidate(prefix, "object prefix");
        if (candidate.equals(root)) {
            throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                    "object prefix cannot resolve to storage root", null);
        }
        return candidate;
    }

    /** 将原始前缀规范化为安全的相对对象 key。 */
    ObjectKey normalizePrefix(String rawPrefix) {
        if (rawPrefix == null || rawPrefix.isBlank()) {
            throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                    "object prefix is invalid", null);
        }
        String trimmed = rawPrefix;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            return ObjectKey.of(trimmed);
        } catch (IllegalArgumentException exception) {
            throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                    "object prefix is invalid", exception);
        }
    }

    private Path resolveCandidate(ObjectKey key, String label) {
        Path candidate = Path.of(utf8ObjectUri(root, key)).normalize();
        if (!candidate.startsWith(root)) {
            throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                    label + " resolves outside storage root", null);
        }
        verifyParent(candidate);
        if (Files.isSymbolicLink(candidate)) {
            throw failure(FileStorageErrorCode.INVALID_OBJECT_KEY,
                    label + " resolves to a symbolic link", null);
        }
        return candidate;
    }

    /**
     * 将已验证 object key 以 UTF-8 原始字节编码为本地文件 URI。
     *
     * @param root 已真实化的本地存储根目录
     * @param key 已完成安全校验的对象 key
     * @return 保留 UTF-8 文件系统字节的绝对文件 URI
     */
    static URI utf8ObjectUri(Path root, ObjectKey key) {
        String rootUri = root.toUri().toString();
        String separator = rootUri.endsWith("/") ? "" : "/";
        return URI.create(rootUri + separator + encodeUtf8Path(key.value()));
    }

    /**
     * 将相对 key 的 UTF-8 字节编码为 URI 原始路径。
     *
     * @param key 已验证的相对对象 key
     * @return 可拼接到目录 URI 后的百分号编码路径
     */
    private static String encodeUtf8Path(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte value : bytes) {
            appendEncodedByte(encoded, Byte.toUnsignedInt(value));
        }
        return encoded.toString();
    }

    /**
     * 追加 URI 路径中可直接使用或需百分号编码的单个字节。
     *
     * @param encoded 当前 URI 路径缓冲区
     * @param value 无符号字节值
     */
    private static void appendEncodedByte(StringBuilder encoded, int value) {
        if (isPathByte(value)) {
            encoded.append((char) value);
        } else {
            encoded.append('%').append(HEX[value >>> 4]).append(HEX[value & 0x0F]);
        }
    }

    /**
     * 判断字节是否可原样放入 file URI 路径。
     *
     * @param value 无符号字节值
     * @return 可原样写入时为 true
     */
    private static boolean isPathByte(int value) {
        return value == '/' || value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '-' || value == '.' || value == '_'
                || value == '~';
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
                    .digest(key.value().getBytes(StandardCharsets.UTF_8));
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
