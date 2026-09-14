package io.github.lvdaxianer.dinghai.filestorage.aliyunoss;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 使用 OSS Java SDK V2 的固定 bucket 阿里云 OSS 文件存储适配器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public final class AliyunOssFileStorageAdapter implements FileStorage {
    /** 单次列举的最大对象数。 */
    private static final long LIST_PAGE_SIZE = 1_000L;
    /** 上传预签名方法。 */
    private static final String PUT_METHOD = "PUT";
    /** 下载预签名方法。 */
    private static final String GET_METHOD = "GET";
    /** HTTP 日期解析格式。 */
    private static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    /** V2 SDK client。 */
    private final OSSClient client;
    /** 适配器唯一允许访问的 bucket。 */
    private final String bucket;

    /**
     * 使用已校验配置创建适配器。
     *
     * @param configuration 阿里云 OSS 连接配置
     */
    public AliyunOssFileStorageAdapter(AliyunOssStorageConfiguration configuration) {
        this(AliyunOssClientFactory.create(configuration), configuration.bucket());
    }

    /**
     * 使用受管 V2 client 创建适配器。
     *
     * @param client 阿里云 OSS V2 client
     * @param bucket 预先创建的 bucket
     */
    public AliyunOssFileStorageAdapter(OSSClient client, String bucket) {
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = requireText(bucket, "bucket");
    }

    /** {@inheritDoc} */
    @Override
    public ObjectMetadata put(UploadRequest request) {
        putObject(request);
        return confirmedMetadata(request.key());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<DownloadObject> get(ObjectKey key) {
        Optional<ObjectMetadata> metadata = stat(key);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        return getObject(key, metadata.get());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ObjectMetadata> stat(ObjectKey key) {
        try {
            return Optional.of(metadata(key, head(key)));
        } catch (Exception exception) {
            return missingOrThrow(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists(ObjectKey key) {
        return stat(key).isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean delete(ObjectKey key) {
        if (!exists(key)) {
            return false;
        }
        try {
            client.deleteObject(com.aliyun.sdk.service.oss2.models.DeleteObjectRequest.newBuilder()
                    .bucket(bucket).key(key.value()).build());
            return true;
        } catch (Exception exception) {
            throw AliyunOssExceptionMapper.map(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Stream<ObjectMetadata> list() {
        return StreamSupport.stream(new ObjectMetadataSpliterator(), false);
    }

    /** {@inheritDoc} */
    @Override
    public PresignedUpload presignUpload(PresignUploadRequest request) {
        Instant expiresAt = Instant.now().plus(request.expiresIn());
        PutObjectRequest signed = putRequest(request.key(), request.contentType(),
                request.cacheControl().value(), null);
        PresignResult result = client.presign(signed, PresignOptions.newBuilder().expiration(expiresAt).build());
        return new PresignedUpload(result.url(), PUT_METHOD, result.expiration().orElse(expiresAt),
                result.signedHeaders().orElse(Map.of()), Map.of());
    }

    /** {@inheritDoc} */
    @Override
    public PresignedDownload presignDownload(PresignDownloadRequest request) {
        Instant expiresAt = Instant.now().plus(request.expiresIn());
        GetObjectRequest signed = GetObjectRequest.newBuilder().bucket(bucket).key(request.key().value())
                .responseCacheControl(request.cacheControl().value())
                .responseContentDisposition(request.disposition().name().toLowerCase()).build();
        PresignResult result = client.presign(signed, PresignOptions.newBuilder().expiration(expiresAt).build());
        return new PresignedDownload(result.url(), GET_METHOD, result.expiration().orElse(expiresAt),
                request.disposition(), Map.of("Cache-Control", request.cacheControl().value()));
    }

    /** 上传对象到固定 bucket。 */
    private void putObject(UploadRequest request) {
        try {
            client.putObject(putRequest(request.key(), request.contentType(), request.cacheControl().value(),
                    request.contentLength() < 0 ? BinaryData.fromStream(request.content())
                            : BinaryData.fromStream(request.content(), request.contentLength())));
        } catch (Exception exception) {
            throw AliyunOssExceptionMapper.map(exception);
        }
    }

    /** 构建 OSS V2 写入请求。 */
    private PutObjectRequest putRequest(ObjectKey key, String contentType, String cacheControl,
                                        BinaryData body) {
        PutObjectRequest.Builder builder = PutObjectRequest.newBuilder().bucket(bucket).key(key.value())
                .contentType(contentType).cacheControl(cacheControl);
        return body == null ? builder.build() : builder.body(body).build();
    }

    /** 获取经确认的对象元数据。 */
    private ObjectMetadata confirmedMetadata(ObjectKey key) {
        return stat(key).orElseThrow(() -> AliyunOssExceptionMapper.map(
                new NoSuchElementException("uploaded object was not confirmed")));
    }

    /** 调用 V2 HeadObject。 */
    private HeadObjectResult head(ObjectKey key) {
        return client.headObject(HeadObjectRequest.newBuilder().bucket(bucket).key(key.value()).build());
    }

    /** 获取对象流，处理查询与读取之间的删除竞态。 */
    private Optional<DownloadObject> getObject(ObjectKey key, ObjectMetadata metadata) {
        try {
            return Optional.of(new DownloadObject(client.getObject(GetObjectRequest.newBuilder()
                    .bucket(bucket).key(key.value()).build()).body(), metadata));
        } catch (Exception exception) {
            return missingOrThrow(exception);
        }
    }

    /** 将缺失对象转换为空结果，其他失败保持稳定异常。 */
    private static <T> Optional<T> missingOrThrow(Exception exception) {
        if (AliyunOssExceptionMapper.isMissing(exception)) {
            return Optional.empty();
        }
        throw AliyunOssExceptionMapper.map(exception);
    }

    /** 转换 V2 元数据结果。 */
    private static ObjectMetadata metadata(ObjectKey key, HeadObjectResult result) {
        String contentType = requireText(result.contentType(), "contentType");
        Long contentLength = Objects.requireNonNull(result.contentLength(), "contentLength");
        return new ObjectMetadata(key, contentLength, contentType, result.eTag(), lastModified(result),
                result.cacheControl() == null ? CacheControl.privateNoCache() : new CacheControl(result.cacheControl()));
    }

    /** 解析 OSS 返回的 HTTP 最后修改时间。 */
    private static Instant lastModified(HeadObjectResult result) {
        String value = requireText(result.lastModified(), "lastModified");
        return ZonedDateTime.parse(value, HTTP_DATE_FORMATTER).toInstant();
    }

    /** 校验并标准化文本值。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** 按 continuation token 惰性列举对象。 */
    private final class ObjectMetadataSpliterator extends Spliterators.AbstractSpliterator<ObjectMetadata> {
        private Iterator<com.aliyun.sdk.service.oss2.models.ObjectSummary> current =
                List.<com.aliyun.sdk.service.oss2.models.ObjectSummary>of().iterator();
        private String token;
        private boolean finished;

        /** 创建有序、非空的惰性分页迭代器。 */
        private ObjectMetadataSpliterator() {
            super(Long.MAX_VALUE, ORDERED | NONNULL);
        }

        /** {@inheritDoc} */
        @Override
        public boolean tryAdvance(Consumer<? super ObjectMetadata> action) {
            Objects.requireNonNull(action, "action");
            while (!current.hasNext() && !finished) {
                loadPage();
            }
            if (!current.hasNext()) {
                return false;
            }
            ObjectKey key = ObjectKey.of(current.next().key());
            action.accept(confirmedMetadata(key));
            return true;
        }

        /** 加载下一页并防止不可推进的 continuation token 死循环。 */
        private void loadPage() {
            ListObjectsV2Result page = client.listObjectsV2(listRequest());
            current = page.contents().iterator();
            String nextToken = page.nextContinuationToken();
            finished = !Boolean.TRUE.equals(page.isTruncated()) || nextToken == null || nextToken.equals(token);
            token = nextToken;
        }

        /**
         * 构建下一页请求，首个请求不携带 continuation token。
         *
         * @return 已设置固定 bucket 与页大小的列表请求
         * @author lvdaxianer@yeah.net
         * @date 2026-09-15
         */
        private ListObjectsV2Request listRequest() {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.newBuilder().bucket(bucket)
                    .maxKeys(LIST_PAGE_SIZE);
            if (token != null) {
                // 后续页必须携带 OSS 返回的 continuation token。
                return builder.continuationToken(token).build();
            }
            // 首次列举请求不向 SDK builder 传入空 token。
            return builder.build();
        }
    }
}
