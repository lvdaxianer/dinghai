package io.github.lvdaxianer.dinghai.filestorage.local;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 生成和验证本地文件存储的 HMAC-SHA-256 预签名地址。 */
public final class LocalFileStorageSigner {
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1";
    private final byte[] signingKey;
    private final Duration maximumExpiry;
    private final Clock clock;

    /** 创建本地签名器。 */
    public LocalFileStorageSigner(byte[] signingKey, Duration maximumExpiry, Clock clock) {
        if (signingKey == null || signingKey.length == 0) {
            throw new IllegalArgumentException("signing key must not be empty");
        }
        this.signingKey = signingKey.clone();
        this.maximumExpiry = requirePositive(maximumExpiry, "maximumExpiry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 生成受限的预签名上传地址。 */
    public PresignedUpload presignUpload(PresignUploadRequest request, String pathPrefix) {
        Objects.requireNonNull(request, "request");
        Instant expiresAt = expiry(request.expiresIn());
        String payload = uploadPayload(request, expiresAt);
        String signature = sign(payload);
        String url = buildUrl(pathPrefix, "upload", request.key(),
                "expires=" + expiresAt.getEpochSecond(),
                "contentType=" + encode(request.contentType()),
                "maxSize=" + request.maxSizeBytes(),
                "cacheControl=" + encode(request.cacheControl().value()),
                "signature=" + signature);
        return new PresignedUpload(url, "PUT", expiresAt,
                Map.of("Content-Type", request.contentType()), Map.of());
    }

    /** 生成受限的预签名下载地址。 */
    public PresignedDownload presignDownload(PresignDownloadRequest request, String pathPrefix) {
        Objects.requireNonNull(request, "request");
        Instant expiresAt = expiry(request.expiresIn());
        String payload = downloadPayload(request, expiresAt);
        String signature = sign(payload);
        String operation = request.disposition() == PresignDownloadRequest.ResponseDisposition.INLINE
                ? "preview" : "download";
        String url = buildUrl(pathPrefix, operation, request.key(),
                "expires=" + expiresAt.getEpochSecond(),
                "disposition=" + request.disposition().name().toLowerCase(),
                "cacheControl=" + encode(request.cacheControl().value()),
                "signature=" + signature);
        return new PresignedDownload(url, "GET", expiresAt, request.disposition(),
                Map.of("Cache-Control", request.cacheControl().value()));
    }

    /** 验证预签名上传地址的有效期、策略和签名。 */
    public void verifyUpload(PresignUploadRequest request, Instant expiresAt,
                             String signature, Instant now) {
        verifyUpload("PUT", request, expiresAt, signature, now);
    }

    /** 验证请求方法、预签名上传策略和签名。 */
    public void verifyUpload(String method, PresignUploadRequest request, Instant expiresAt,
                             String signature, Instant now) {
        if (!"PUT".equals(method)) {
            throw invalidPresign();
        }
        verify(request, expiresAt, signature, now, uploadPayload(request, expiresAt));
    }

    /** 验证预签名下载地址的有效期、策略和签名。 */
    public void verifyDownload(PresignDownloadRequest request, Instant expiresAt,
                               String signature, Instant now) {
        verify(request, expiresAt, signature, now, downloadPayload(request, expiresAt));
    }

    private void verify(Object request, Instant expiresAt, String signature,
                        Instant now, String payload) {
        if (expiresAt == null || now == null || !expiresAt.isAfter(now)
                || expiresAt.isAfter(now.plus(maximumExpiry))) {
            throw invalidPresign();
        }
        byte[] expected = decodeSignature(sign(payload));
        byte[] actual = decodeSignature(signature);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalidPresign();
        }
    }

    private Instant expiry(Duration requested) {
        Duration duration = requirePositive(requested, "expiresIn");
        if (duration.compareTo(maximumExpiry) > 0) {
            throw invalidPresign();
        }
        try {
            return clock.instant().plus(duration);
        } catch (ArithmeticException exception) {
            throw invalidPresign();
        }
    }

    private String uploadPayload(PresignUploadRequest request, Instant expiresAt) {
        return String.join("\n", VERSION, "PUT", request.key().value(),
                Long.toString(expiresAt.getEpochSecond()), request.contentType(),
                Long.toString(request.maxSizeBytes()), request.cacheControl().value());
    }

    private String downloadPayload(PresignDownloadRequest request, Instant expiresAt) {
        return String.join("\n", VERSION, "GET", request.key().value(),
                Long.toString(expiresAt.getEpochSecond()), request.disposition().name(),
                request.cacheControl().value());
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, SIGNATURE_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new FileStorageException(FileStorageErrorCode.INTERNAL_ERROR,
                    "local signing is unavailable", exception);
        }
    }

    private static byte[] decodeSignature(String signature) {
        if (signature == null || signature.isBlank()) {
            throw invalidPresign();
        }
        try {
            return Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException exception) {
            throw invalidPresign();
        }
    }

    private static String buildUrl(String pathPrefix, String operation, ObjectKey key,
                                   String... parameters) {
        String prefix = validatePathPrefix(pathPrefix);
        String encodedKey = encodeKey(key);
        return prefix + "/" + operation + "/" + encodedKey + "?" + String.join("&", parameters);
    }

    private static String encodeKey(ObjectKey key) {
        return java.util.Arrays.stream(key.value().split("/", -1))
                .map(LocalFileStorageSigner::encode).reduce((left, right) -> left + "/" + right)
                .orElseThrow(LocalFileStorageSigner::invalidPresign);
    }

    static String validatePathPrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank() || pathPrefix.indexOf('?') >= 0
                || pathPrefix.indexOf('#') >= 0 || pathPrefix.indexOf('\n') >= 0
                || pathPrefix.indexOf('\r') >= 0) {
            throw new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION,
                    "local URL path prefix is invalid");
        }
        String prefix = pathPrefix.startsWith("/") ? pathPrefix : "/" + pathPrefix;
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%7E", "~");
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static FileStorageException invalidPresign() {
        return new FileStorageException(FileStorageErrorCode.INVALID_PRESIGN,
                "presigned request is invalid");
    }
}
