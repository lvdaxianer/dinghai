package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.LocalFileSource;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriUtils;

/**
 * 本地文件存储的受预签名保护 HTTP 端点。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class FileStorageWebController {

    static final String READ_PERMIT_ATTRIBUTE = FileStorageWebController.class.getName() + ".readPermit";

    /** 缓冲区大小。 */
    private static final int DEFAULT_BUFFER_SIZE = 128 * 1024;
    /** HTTP 日期格式化器。 */
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    /** 后端中立的文件存储。 */
    private final FileStorage storage;
    /** 直接访问授权器。 */
    private final FileStorageDirectAccessAuthorizer authorizer;
    /** 当前时间源。 */
    private final Clock clock;
    /** 稳定文件读取服务。 */
    private final StableFileAccessService stableAccess;
    /** 可选非本地 provider 读取缓存。 */
    private final ProviderFileReadCache readCache;
    /** 宿主可选的传输限流 key 解析器。 */
    private final WebTransferRateLimitKeyResolver keyResolver;
    /** 已规范化的真实 key 预览 URL 基础路径。 */
    private final String keyPreviewBasePath;
    /** 已校验的 Web 路由集合。 */
    private final FileStorageWebRoutes routes;
    private final int bufferSize;
    private final boolean sendfileBinaryContentTypesEnabled;
    private final WebTransferLimiter transferLimiter;

    /**
     * 创建后端中立的文件存储端点。
     *
     * @param storage 文件存储
     * @param authorizer 直接访问授权器
     * @param clock 当前时间源
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public FileStorageWebController(FileStorage storage,
                                    FileStorageDirectAccessAuthorizer authorizer, Clock clock) {
        this(storage, authorizer, clock, null);
    }

    /**
     * 创建带稳定 ID 读取能力的文件存储端点。
     *
     * @param storage 文件存储
     * @param authorizer 直接访问授权器
     * @param clock 当前时间源
     * @param stableAccess 稳定文件读取服务
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public FileStorageWebController(FileStorage storage,
                                    FileStorageDirectAccessAuthorizer authorizer, Clock clock,
                                    StableFileAccessService stableAccess) {
        this(storage, authorizer, clock, stableAccess, null);
    }

    /**
     * 创建带稳定 ID 与二级缓存读取能力的文件存储端点。
     *
     * @param storage 文件存储
     * @param authorizer 直接访问授权器
     * @param clock 当前时间源
     * @param stableAccess 稳定文件读取服务
     * @param readCache 非本地 provider 读取缓存
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public FileStorageWebController(FileStorage storage,
                                    FileStorageDirectAccessAuthorizer authorizer, Clock clock,
                                    StableFileAccessService stableAccess,
                                    ProviderFileReadCache readCache) {
        this(storage, authorizer, clock, stableAccess, readCache, DEFAULT_BUFFER_SIZE, 128);
    }

    public FileStorageWebController(FileStorage storage,
                                    FileStorageDirectAccessAuthorizer authorizer, Clock clock,
                                    StableFileAccessService stableAccess,
                                    ProviderFileReadCache readCache,
                                    int bufferSize, int maxConcurrentReads) {
        this(storage, authorizer, clock, stableAccess, readCache, bufferSize, maxConcurrentReads, 16);
    }

    public FileStorageWebController(FileStorage storage,
                                    FileStorageDirectAccessAuthorizer authorizer, Clock clock,
                                    StableFileAccessService stableAccess,
                                    ProviderFileReadCache readCache,
                                    int bufferSize, int maxConcurrentReads,
                                    int maxConcurrentUploads) {
        this(storage, authorizer, clock, stableAccess, readCache,
                new WebTransferConfiguration(bufferSize, maxConcurrentReads, maxConcurrentUploads, 0, null));
    }

    /**
     * 创建带完整 Web 传输配置的文件存储端点。
     *
     * @param storage 文件存储
     * @param authorizer 直接访问授权器
     * @param clock 当前时间源
     * @param stableAccess 稳定文件读取服务
     * @param readCache 非本地 provider 读取缓存
     * @param transferConfiguration Web 传输资源与宿主 key 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    FileStorageWebController(FileStorage storage, FileStorageDirectAccessAuthorizer authorizer, Clock clock,
                             StableFileAccessService stableAccess, ProviderFileReadCache readCache,
                             WebTransferConfiguration transferConfiguration) {
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
        this.authorizer = java.util.Objects.requireNonNull(authorizer, "authorizer");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        transferConfiguration = java.util.Objects.requireNonNull(transferConfiguration,
                "transferConfiguration");
        this.stableAccess = stableAccess;
        this.readCache = readCache;
        if (transferConfiguration.bufferSize() <= 0 || transferConfiguration.maxConcurrentReads() <= 0
                || transferConfiguration.maxConcurrentUploads() <= 0
                || transferConfiguration.maxConcurrentTransfersPerKey() < 0) {
            throw new IllegalArgumentException("web transfer global limits must be positive and per-key limit must not be negative");
        }
        this.bufferSize = transferConfiguration.bufferSize();
        this.keyResolver = transferConfiguration.keyResolver();
        this.keyPreviewBasePath = transferConfiguration.keyPreviewBasePath();
        this.routes = transferConfiguration.routes();
        this.sendfileBinaryContentTypesEnabled = transferConfiguration.sendfileBinaryContentTypesEnabled();
        this.transferLimiter = new WebTransferLimiter(transferConfiguration.maxConcurrentUploads(),
                transferConfiguration.maxConcurrentReads(),
                transferConfiguration.maxConcurrentTransfersPerKey());
    }

    /**
     * 接收并验证预签名上传请求。
     *
     * @param key 原始对象 key
     * @param expires 到期 Unix 秒数
     * @param contentType 已签名内容类型
     * @param maxSize 已签名最大大小
     * @param cacheControl 已签名缓存策略
     * @param signature 预签名
     * @param requestContentType 请求内容类型
     * @param contentLength 请求声明大小
     * @param input 请求体输入流
     * @param principal 当前认证主体
     * @return 已确认的对象元数据与预览地址
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ResponseEntity<WebUploadResult> upload(@PathVariable String key,
                                       @RequestParam(required = false) Long expires,
                                       @RequestParam(required = false) String contentType,
                                       @RequestParam(required = false) Long maxSize,
                                       @RequestParam(required = false) String cacheControl,
                                       @RequestParam(required = false) String signature,
                                       @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false)
                                       String requestContentType,
                                       @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false,
                                               defaultValue = "-1") long contentLength,
                                       InputStream input, Principal principal) {
        try (WebTransferLimiter.Permit ignored = transferLimiter.acquireUpload(rateLimitKey())) {
        ObjectKey objectKey = objectKey(key);
        if (signature == null || signature.isBlank()) {
            requireDirectAccess(principal, objectKey, FileStorageAccessOperation.UPLOAD);
            validateContentType(requestContentType, requestContentType);
            validateContentLength(contentLength, UploadRequest.DEFAULT_MAX_SIZE_BYTES);
            return uploadResult(writeObject(new UploadRequest(objectKey, input, contentLength,
                    requestContentType, CacheControl.privateNoCache())));
        } else {
            if (expires == null || contentType == null || maxSize == null || cacheControl == null) {
                throw invalidPresign();
            }
            PresignUploadRequest request = new PresignUploadRequest(objectKey, expiry(expires),
                    decode(contentType), maxSize, new CacheControl(decode(cacheControl)));
            localStorage().verifyPresignedUpload(request, timestamp(expires), signature, clock.instant());
            validateContentType(request.contentType(), requestContentType);
            validateContentLength(contentLength, request.maxSizeBytes());
            return uploadResult(writeObject(new UploadRequest(objectKey, input, contentLength,
                    request.contentType(), request.cacheControl())));
        }
        }
    }

    /**
     * 下载对象并强制 attachment 响应模式。
     *
     * @param key 原始对象 key
     * @param expires 到期 Unix 秒数
     * @param cacheControl 已签名缓存策略
     * @param signature 预签名
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String key,
                                                            @RequestParam(required = false) Long expires,
                                                            @RequestParam(required = false) String cacheControl,
                                                            @RequestParam(required = false) String signature,
                                                            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                    required = false) String ifNoneMatch,
                                                            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                    required = false) String ifModifiedSince,
                                                            @RequestHeader(value = HttpHeaders.RANGE,
                                                                    required = false) String range) {
        return read(key, expires, cacheControl, signature, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.ATTACHMENT);
    }

    /**
     * 预览对象并强制 inline 响应模式。
     *
     * @param key 原始对象 key
     * @param expires 到期 Unix 秒数
     * @param cacheControl 已签名缓存策略
     * @param signature 预签名
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable String key,
                                                           @RequestParam(required = false) Long expires,
                                                           @RequestParam(required = false) String cacheControl,
                                                           @RequestParam(required = false) String signature,
                                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                   required = false) String ifNoneMatch,
                                                           @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                   required = false) String ifModifiedSince,
                                                           @RequestHeader(value = HttpHeaders.RANGE,
                                                                   required = false) String range) {
        return read(key, expires, cacheControl, signature, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /** 通过真实对象 key 读取对象，保留宿主直接访问或预签名校验。 */
    public ResponseEntity<StreamingResponseBody> keyRead(@PathVariable String key,
                                                           @RequestParam(required = false) Long expires,
                                                           @RequestParam(required = false) String cacheControl,
                                                           @RequestParam(required = false) String signature,
                                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                                           @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSince,
                                                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return read(key, expires, cacheControl, signature, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /** 通过真实对象 key 在线预览对象。 */
    public ResponseEntity<StreamingResponseBody> keyPreview(@PathVariable String key,
                                                              @RequestParam(required = false) Long expires,
                                                              @RequestParam(required = false) String cacheControl,
                                                              @RequestParam(required = false) String signature,
                                                              @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                                              @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSince,
                                                              @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return read(key, expires, cacheControl, signature, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /** 通过真实对象 key 下载对象。 */
    public ResponseEntity<StreamingResponseBody> keyDownload(@PathVariable String key,
                                                               @RequestParam(required = false) Long expires,
                                                               @RequestParam(required = false) String cacheControl,
                                                               @RequestParam(required = false) String signature,
                                                               @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                                               @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSince,
                                                               @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return read(key, expires, cacheControl, signature, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.ATTACHMENT);
    }

    /**
     * 通过稳定 ID 匿名读取对象，响应为内联预览。
     *
     * @param identifier 稳定文件 ID
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ResponseEntity<StreamingResponseBody> stableRead(@PathVariable String identifier,
                                                              @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                      required = false) String ifNoneMatch,
                                                              @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                      required = false) String ifModifiedSince,
                                                              @RequestHeader(value = HttpHeaders.RANGE,
                                                                      required = false) String range) {
        return stableRead(identifier, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /** 通过稳定 ID 的显式版本路径匿名读取对象。 */
    public ResponseEntity<StreamingResponseBody> stableVersionedRead(@PathVariable String identifier,
                                                                       @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                               required = false) String ifNoneMatch,
                                                                       @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                               required = false) String ifModifiedSince,
                                                                       @RequestHeader(value = HttpHeaders.RANGE,
                                                                               required = false) String range) {
        return stableVersionedRead(identifier, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /**
     * 通过稳定 ID 匿名预览对象。
     *
     * @param identifier 稳定文件 ID
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ResponseEntity<StreamingResponseBody> stablePreview(@PathVariable String identifier,
                                                                 @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                         required = false) String ifNoneMatch,
                                                                 @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                         required = false) String ifModifiedSince,
                                                                 @RequestHeader(value = HttpHeaders.RANGE,
                                                                         required = false) String range) {
        return stableRead(identifier, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /** 通过稳定 ID 的显式版本路径匿名预览对象。 */
    public ResponseEntity<StreamingResponseBody> stableVersionedPreview(@PathVariable String identifier,
                                                                          @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                                  required = false) String ifNoneMatch,
                                                                          @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                                  required = false) String ifModifiedSince,
                                                                          @RequestHeader(value = HttpHeaders.RANGE,
                                                                                  required = false) String range) {
        return stableVersionedRead(identifier, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.INLINE);
    }

    /**
     * 通过稳定 ID 匿名下载对象。
     *
     * @param identifier 稳定文件 ID
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    public ResponseEntity<StreamingResponseBody> stableDownload(@PathVariable String identifier,
                                                                  @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                          required = false) String ifNoneMatch,
                                                                  @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                          required = false) String ifModifiedSince,
                                                                  @RequestHeader(value = HttpHeaders.RANGE,
                                                                          required = false) String range) {
        return stableRead(identifier, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.ATTACHMENT);
    }

    /** 通过稳定 ID 的显式版本路径匿名下载对象。 */
    public ResponseEntity<StreamingResponseBody> stableVersionedDownload(@PathVariable String identifier,
                                                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                                   required = false) String ifNoneMatch,
                                                                           @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE,
                                                                                   required = false) String ifModifiedSince,
                                                                           @RequestHeader(value = HttpHeaders.RANGE,
                                                                                   required = false) String range) {
        return stableVersionedRead(identifier, ifNoneMatch, ifModifiedSince, range,
                PresignDownloadRequest.ResponseDisposition.ATTACHMENT);
    }

    /**
     * 验证读取授权、条件缓存和可选字节范围。
     *
     * @param key 原始对象 key
     * @param expires 到期 Unix 秒数
     * @param cacheControl 已签名缓存策略
     * @param signature 预签名
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @param disposition 响应处置模式
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ResponseEntity<StreamingResponseBody> read(String key, Long expires, String cacheControl,
                                                         String signature, String ifNoneMatch,
                                                         String ifModifiedSince, String range,
                                                         PresignDownloadRequest.ResponseDisposition disposition) {
        ObjectKey objectKey = objectKey(key);
        verifyReadAccess(objectKey, expires, cacheControl, signature, disposition);
        return readObject(objectKey, ifNoneMatch, ifModifiedSince, range, disposition);
    }

    /**
     * 解析稳定 ID 后读取对象，不执行组件用户授权。
     *
     * @param identifier 稳定文件 ID
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @param disposition 响应处置模式
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ResponseEntity<StreamingResponseBody> stableRead(String identifier, String ifNoneMatch,
                                                               String ifModifiedSince, String range,
                                                               PresignDownloadRequest.ResponseDisposition disposition) {
        return readObject(stableAccess().resolve(identifier), ifNoneMatch, ifModifiedSince, range,
                disposition);
    }

    /** 解析显式版本段并创建稳定读取响应。 */
    private ResponseEntity<StreamingResponseBody> stableVersionedRead(String identifier,
                                                                        String ifNoneMatch,
                                                                        String ifModifiedSince,
                                                                        String range,
                                                                        PresignDownloadRequest.ResponseDisposition disposition) {
        return readObject(stableAccess().resolveVersioned("v1", identifier), ifNoneMatch,
                ifModifiedSince, range, disposition);
    }

    /**
     * 按对象元数据创建标准的条件和范围读取响应。
     *
     * @param key 已验证对象 key
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @param range 单区间字节范围
     * @param disposition 响应处置模式
     * @return 流式对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ResponseEntity<StreamingResponseBody> readObject(ObjectKey key, String ifNoneMatch,
                                                              String ifModifiedSince, String range,
                                                              PresignDownloadRequest.ResponseDisposition disposition) {
        ObjectMetadata metadata = readMetadata(key).orElseThrow(this::notFound);
        HttpHeaders headers = responseHeaders(metadata, disposition);
        if (isNotModified(metadata, ifNoneMatch, ifModifiedSince)) {
            return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);
        }
        return rangedResponse(key, metadata, headers, range);
    }

    /**
     * 验证下载或预览签名；未提供签名时要求宿主授权。
     *
     * @param key 已验证对象 key
     * @param expires 到期 Unix 秒数
     * @param cacheControl 已签名缓存策略
     * @param signature 预签名
     * @param disposition 端点强制处置模式
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void verifyReadAccess(ObjectKey key, Long expires, String cacheControl, String signature,
                                  PresignDownloadRequest.ResponseDisposition disposition) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        PresignDownloadRequest request = new PresignDownloadRequest(key, expiry(expires), disposition,
                new CacheControl(decode(cacheControl)));
        localStorage().verifyPresignedDownload(request, timestamp(expires), signature, clock.instant());
    }

    /**
     * 创建标准或单范围流式响应。
     *
     * @param key 对象 key
     * @param metadata 对象元数据
     * @param headers 基础响应头
     * @param range 请求范围
     * @return 对象响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ResponseEntity<StreamingResponseBody> rangedResponse(ObjectKey key, ObjectMetadata metadata,
                                                                   HttpHeaders headers, String range) {
        Optional<HttpRange> parsedRange = parseRange(range);
        if (parsedRange.isEmpty()) {
            headers.setContentLength(metadata.contentLength());
            return stream(key, headers, HttpStatus.OK, 0L, metadata.contentLength(), metadata.contentType());
        }
        long start;
        long end;
        try {
            start = parsedRange.get().getRangeStart(metadata.contentLength());
            end = parsedRange.get().getRangeEnd(metadata.contentLength());
        } catch (IllegalArgumentException exception) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + metadata.contentLength());
            return new ResponseEntity<>(null, headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        if (start < 0L || end < start || start >= metadata.contentLength()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + metadata.contentLength());
            return new ResponseEntity<>(null, headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        long length = end - start + 1L;
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/"
                + metadata.contentLength());
        headers.setContentLength(length);
        return stream(key, headers, HttpStatus.PARTIAL_CONTENT, start, length, metadata.contentType());
    }

    /**
     * 打开对象流并创建按范围转发的响应体。
     *
     * @param key 对象 key
     * @param headers 响应头
     * @param status HTTP 状态
     * @param skip 跳过字节数
     * @param length 写出字节数
     * @return 流式响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ResponseEntity<StreamingResponseBody> stream(ObjectKey key, HttpHeaders headers,
                                                          HttpStatus status, long skip, long length,
                                                          String contentType) {
        WebTransferLimiter.Permit permit = transferLimiter.acquireRead(rateLimitKey());
        Optional<Path> localFile = localFile(key);
        Optional<jakarta.servlet.http.HttpServletRequest> request = currentRequest();
        if (localFile.isPresent() && headers.getFirst(HttpHeaders.CONTENT_ENCODING) == null
                && request.map(servletRequest ->
                TomcatSendfileTransfer.register(servletRequest, localFile.get(), skip, length, contentType,
                        sendfileBinaryContentTypesEnabled)).orElse(false)) {
            request.ifPresent(servletRequest -> servletRequest.setAttribute(READ_PERMIT_ATTRIBUTE, permit));
            return new ResponseEntity<>(null, headers, status);
        }
        DownloadObject download;
        try {
            download = readObject(key).orElseThrow(this::notFound);
        } catch (RuntimeException exception) {
            permit.close();
            throw exception;
        }
        StreamingResponseBody body = output -> copyRange(download, output, skip, length, permit);
        return new ResponseEntity<>(body, headers, status);
    }

    /**
     * 设置对象缓存、内容类型、处置和范围响应头。
     *
     * @param metadata 对象元数据
     * @param disposition 响应处置模式
     * @return 基础响应头
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private HttpHeaders responseHeaders(ObjectMetadata metadata,
                                        PresignDownloadRequest.ResponseDisposition disposition) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag('"' + metadata.etag() + '"');
        headers.setLastModified(metadata.lastModified().toEpochMilli());
        headers.set(HttpHeaders.CACHE_CONTROL, metadata.cacheControl().value());
        headers.set(HttpHeaders.EXPIRES, HTTP_DATE.format(clock.instant().atZone(ZoneOffset.UTC)));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentType(mediaType(metadata.contentType()));
        headers.setContentDisposition(responseContentDisposition(metadata, disposition));
        return headers;
    }

    /**
     * 从已校验的 object key 提取最后一段，避免将目录路径暴露到响应头。
     *
     * @param metadata 对象元数据
     * @return 不包含目录路径的响应文件名
     */
    private String responseFileName(ObjectMetadata metadata) {
        String objectKey = metadata.key().value();
        return objectKey.substring(objectKey.lastIndexOf('/') + 1);
    }

    /**
     * 使用 ASCII 兼容格式或 RFC UTF-8 格式创建 Content-Disposition。
     *
     * @param metadata 对象元数据
     * @param disposition 响应处置模式
     * @return 编码后的 Content-Disposition
     */
    private ContentDisposition responseContentDisposition(ObjectMetadata metadata,
                                                           PresignDownloadRequest.ResponseDisposition disposition) {
        ContentDisposition.Builder builder = ContentDisposition.builder(
                disposition.name().toLowerCase(Locale.ROOT));
        String fileName = responseFileName(metadata);
        if (StandardCharsets.US_ASCII.newEncoder().canEncode(fileName)) {
            return builder.filename(fileName).build();
        }
        return builder.filename(fileName, StandardCharsets.UTF_8).build();
    }

    /**
     * 将请求 Range 解析为单区间。
     *
     * @param range Range 请求头
     * @return 单区间；无 Range 时为空
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private Optional<HttpRange> parseRange(String range) {
        if (range == null || range.isBlank()) {
            return Optional.empty();
        }
        try {
            java.util.List<HttpRange> ranges = HttpRange.parseRanges(range);
            if (ranges.size() == 1) {
                return Optional.of(ranges.getFirst());
            }
        } catch (IllegalArgumentException exception) {
            throw invalidRange();
        }
        throw invalidRange();
    }

    /**
     * 将对象范围复制到 HTTP 响应流并关闭底层对象流。
     *
     * @param download 可关闭的对象流
     * @param output HTTP 响应流
     * @param skip 跳过字节数
     * @param length 写出字节数
     * @throws IOException 当对象读取或响应写入失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void copyRange(DownloadObject download, java.io.OutputStream output, long skip, long length,
                           WebTransferLimiter.Permit permit)
            throws IOException {
        try (WebTransferLimiter.Permit ignored = permit; DownloadObject ignoredDownload = download) {
            InputStream input = download.stream();
            input.skipNBytes(skip);
            byte[] buffer = new byte[bufferSize];
            long remaining = length;
            while (remaining > 0L) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    return;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    /**
     * 判断条件请求是否命中当前对象版本。
     *
     * @param metadata 对象元数据
     * @param ifNoneMatch 条件 ETag
     * @param ifModifiedSince 条件修改时间
     * @return 命中时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private boolean isNotModified(ObjectMetadata metadata, String ifNoneMatch, String ifModifiedSince) {
        if (ifNoneMatch != null && ifNoneMatch.contains(metadata.etag())) {
            return true;
        }
        if (ifModifiedSince == null || ifModifiedSince.isBlank()) {
            return false;
        }
        try {
            Instant since = Instant.from(HTTP_DATE.parse(ifModifiedSince));
            return !metadata.lastModified().isAfter(since.plusSeconds(1L));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 验证直接访问的宿主授权决策。
     *
     * @param principal 当前认证主体
     * @param key 已验证对象 key
     * @param operation 请求的对象操作
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void requireDirectAccess(Principal principal, ObjectKey key,
                                     FileStorageAccessOperation operation) {
        if (!authorizer.isAllowed(principal, key, operation)) {
            throw forbidden();
        }
    }

    /**
     * 解析到期 Unix 秒数。
     *
     * @param expires 到期 Unix 秒数
     * @return 签名剩余时长占位值
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private java.time.Duration expiry(Long expires) {
        Instant expiresAt = timestamp(expires);
        java.time.Duration duration = java.time.Duration.between(clock.instant(), expiresAt);
        if (duration.isNegative() || duration.isZero()) {
            throw invalidPresign();
        }
        return duration;
    }

    /**
     * 解析 Unix 秒数为时间点。
     *
     * @param seconds Unix 秒数
     * @return 对应时间点
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private Instant timestamp(Long seconds) {
        if (seconds == null) {
            throw invalidPresign();
        }
        try {
            return Instant.ofEpochSecond(seconds);
        } catch (RuntimeException exception) {
            throw invalidPresign();
        }
    }

    /**
     * 验证请求类型与签名策略一致。
     *
     * @param expected 已签名类型
     * @param actual 请求类型
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void validateContentType(String expected, String actual) {
        if (actual == null || actual.isBlank()) {
            throw invalidRequest("request content type does not match signature");
        }
        try {
            String normalized = MediaType.parseMediaType(actual).toString();
            if (expected != null && !expected.equals(normalized)) {
                throw invalidRequest("request content type does not match signature");
            }
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("request content type is invalid");
        }
    }

    /**
     * 验证声明内容长度未超过签名策略。
     *
     * @param contentLength 请求声明长度
     * @param maxSize 最大允许大小
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private void validateContentLength(long contentLength, long maxSize) {
        if (contentLength > maxSize) {
            throw new FileStorageException(FileStorageErrorCode.OBJECT_TOO_LARGE,
                    "upload exceeds maximum size");
        }
    }

    /** 获取支持旧本地预签名契约的适配器。 */
    private LocalFileStorageAdapter localStorage() {
        if (storage instanceof LocalFileStorageAdapter localStorage) {
            return localStorage;
        }
        throw invalidPresign();
    }

    /** 获取已配置的稳定文件读取服务。 */
    private StableFileAccessService stableAccess() {
        if (stableAccess != null) {
            return stableAccess;
        }
        throw new FileStorageException(FileStorageErrorCode.INVALID_CONFIGURATION,
                "stable file access is unavailable");
    }

    /** 通过可用缓存或原始 provider 查询对象元数据。 */
    private Optional<ObjectMetadata> readMetadata(ObjectKey key) {
        return readCache == null ? storage.stat(key) : readCache.stat(key);
    }

    /** 通过可用缓存或原始 provider 打开对象流。 */
    private Optional<DownloadObject> readObject(ObjectKey key) {
        return readCache == null ? storage.get(key) : readCache.get(key);
    }

    /** 返回已验证的本地对象或远程缓存副本路径。 */
    private Optional<Path> localFile(ObjectKey key) {
        if (readCache != null) {
            return readCache.localFile(key);
        }
        if (storage instanceof LocalFileSource source) {
            return source.localFile(key);
        }
        return Optional.empty();
    }

    /** 获取当前 Servlet 请求；非 Web 调用时不尝试容器传输。 */
    private Optional<jakarta.servlet.http.HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    /**
     * 解析宿主限流 key；未配置、空白或解析异常时只使用全局限流。
     *
     * @return 可选的非空白限流 key
     */
    private String rateLimitKey() {
        if (keyResolver == null) {
            return null;
        }
        try {
            return currentRequest().map(keyResolver::resolve).filter(key -> !key.isBlank()).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 通过缓存协作者写入对象并立即失效旧副本。
     *
     * @param request 上传请求
     * @return provider 确认的对象元数据
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private ObjectMetadata writeObject(UploadRequest request) {
        if (readCache == null) {
            return storage.put(request);
        }
        return readCache.put(request);
    }

    /**
     * 构造上传成功的创建响应。
     *
     * @param metadata provider 确认的对象元数据
     * @return 包含根相对预览地址的 HTTP 创建响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private ResponseEntity<WebUploadResult> uploadResult(ObjectMetadata metadata) {
        WebUploadResult result = WebUploadResult.from(metadata, previewUrl(metadata.key()));
        return ResponseEntity.created(URI.create(result.previewUrl())).body(result);
    }

    /**
     * 构造真实 object key 的根相对预览地址。
     *
     * @param key 已验证的对象 key
     * @return 可供浏览器访问的路径编码预览地址
     * @author lvdaxianer@yeah.net
     * @date 2026-09-12
     */
    private String previewUrl(ObjectKey key) {
        return keyPreviewBasePath + UriUtils.encodePath(key.value(), StandardCharsets.UTF_8);
    }

    /**
     * 将内容类型安全转换为 Spring MediaType。
     *
     * @param contentType 对象内容类型
     * @return 可用 MediaType
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 构造对象不存在异常。
     *
     * @return 对象不存在异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorageException notFound() {
        return new FileStorageException(FileStorageErrorCode.OBJECT_NOT_FOUND, "object not found");
    }

    /**
     * 构造禁止访问异常。
     *
     * @return 禁止访问异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorageException forbidden() {
        return new FileStorageException(FileStorageErrorCode.INVALID_PRESIGN, "file access denied");
    }

    /**
     * 构造无效预签名异常。
     *
     * @return 无效预签名异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorageException invalidPresign() {
        return new FileStorageException(FileStorageErrorCode.INVALID_PRESIGN,
                "presigned request is invalid");
    }

    /**
     * 构造无效范围异常。
     *
     * @return 无效范围异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorageException invalidRange() {
        return new FileStorageException(FileStorageErrorCode.INVALID_REQUEST,
                "requested range is invalid");
    }

    /**
     * 构造无效请求异常。
     *
     * @param message 安全错误消息
     * @return 无效请求异常
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private FileStorageException invalidRequest(String message) {
        return new FileStorageException(FileStorageErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 按 UTF-8 解码查询参数，确保签名载荷使用规范化值。
     *
     * @param value 原始查询参数
     * @return 解码后的查询参数
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private String decode(String value) {
        if (value == null) {
            throw invalidRequest("query parameter is required");
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("query parameter encoding is invalid");
        }
    }

    /**
     * 移除 Spring 多段路径变量附加的单个前导分隔符后创建对象 key。
     *
     * @param pathVariable Spring 绑定的路径变量
     * @return 已验证对象 key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-11
     */
    private ObjectKey objectKey(String pathVariable) {
        if (pathVariable == null) {
            throw invalidRequest("object key is required");
        }
        String key = pathVariable.startsWith("/") ? pathVariable.substring(1) : pathVariable;
        try {
            return ObjectKey.of(key);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("object key is invalid");
        }
    }

    /** 返回此控制器实例对应的动态路由集合。 */
    FileStorageWebRoutes routes() {
        return routes;
    }
}
