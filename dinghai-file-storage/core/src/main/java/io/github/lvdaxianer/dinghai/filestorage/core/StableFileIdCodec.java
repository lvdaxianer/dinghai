package io.github.lvdaxianer.dinghai.filestorage.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import com.google.crypto.tink.subtle.AesSiv;

/**
 * 使用 AES-SIV 确定性认证加密的稳定文件 ID 编解码器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public final class StableFileIdCodec {
    private static final String VERSION = "v1";
    private static final String PREFIX = VERSION + ".";
    private static final int MAX_FIELD_BYTES = 1024;
    private static final byte[] ASSOCIATED_DATA = VERSION.getBytes(StandardCharsets.US_ASCII);
    private final AesSiv cipher;

    /** 从部署提供的密钥材料创建编解码器。 */
    public StableFileIdCodec(byte[] keyMaterial) {
        cipher = createCipher(keyMaterial);
    }

    /** 将逻辑引用编码为稳定且不泄漏明文的 ID。 */
    public String encode(FileReference reference) {
        Objects.requireNonNull(reference, "reference");
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypt(plaintext(reference)));
    }

    /** 验证并解码稳定 ID。 */
    public FileReference decode(String identifier) {
        return reference(decrypt(ciphertext(identifier)));
    }

    /** 使用 AES-SIV 加密引用序列化结果。 */
    private byte[] encrypt(byte[] plaintext) {
        try {
            return cipher.encryptDeterministically(plaintext, ASSOCIATED_DATA);
        } catch (GeneralSecurityException exception) {
            throw invalidId(exception);
        }
    }

    /** 使用 AES-SIV 验证并解密密文。 */
    private byte[] decrypt(byte[] ciphertext) {
        try {
            return cipher.decryptDeterministically(ciphertext, ASSOCIATED_DATA);
        } catch (GeneralSecurityException exception) {
            throw invalidId(exception);
        }
    }

    /** 构造二进制长度前缀明文，避免字段拼接歧义。 */
    private static byte[] plaintext(FileReference reference) {
        byte[] alias = reference.storageAlias().getBytes(StandardCharsets.UTF_8);
        byte[] key = reference.key().value().getBytes(StandardCharsets.UTF_8);
        validateLength(alias.length);
        validateLength(key.length);
        return ByteBuffer.allocate(Integer.BYTES * 2 + alias.length + key.length)
                .putInt(alias.length).put(alias).putInt(key.length).put(key).array();
    }

    /** 验证并取出 URL-safe 密文。 */
    private static byte[] ciphertext(String identifier) {
        if (identifier == null || !identifier.startsWith(PREFIX)) {
            throw invalidId(null);
        }
        try {
            return Base64.getUrlDecoder().decode(identifier.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw invalidId(exception);
        }
    }

    /** 从二进制明文恢复逻辑引用。 */
    private static FileReference reference(byte[] plaintext) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(plaintext);
            String alias = field(buffer);
            String key = field(buffer);
            if (buffer.hasRemaining()) {
                throw invalidId(null);
            }
            return FileReference.of(alias, key);
        } catch (RuntimeException exception) {
            throw invalidId(exception);
        }
    }

    /** 读取一个长度受限的 UTF-8 字段。 */
    private static String field(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length < 0 || length > MAX_FIELD_BYTES || length > buffer.remaining()) {
            throw invalidId(null);
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 将任意部署密钥材料派生成 AES-SIV 所需的 64 字节密钥。 */
    private static AesSiv createCipher(byte[] keyMaterial) {
        Objects.requireNonNull(keyMaterial, "keyMaterial");
        if (keyMaterial.length < 32) {
            throw new IllegalArgumentException("stable file ID key must be at least 32 bytes");
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-512").digest(keyMaterial.clone());
            return new AesSiv(key);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-SIV is unavailable", exception);
        }
    }

    /** 验证序列化字段长度。 */
    private static void validateLength(int length) {
        if (length > MAX_FIELD_BYTES) {
            throw new IllegalArgumentException("stable file reference field is too long");
        }
    }

    /** 创建不泄漏解密或认证细节的稳定错误。 */
    private static FileStorageException invalidId(Throwable cause) {
        return new FileStorageException(FileStorageErrorCode.INVALID_REQUEST,
                "stable file ID is invalid", cause);
    }
}
