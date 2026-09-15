package io.github.lvdaxianer.dinghai.filestorage.minio;

import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import io.minio.messages.Part;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** 使用固定 bucket 的 MinIO 文件存储适配器。 */
public final class MinioFileStorageAdapter implements FileStorage, MultipartFileStorage {
    private static final long DEFAULT_PART_SIZE = 10L * 1024 * 1024;
    private final MinioClient client;
    private final String bucket;
    private final String endpoint;
    private MinioMultipartGateway multipart;

    /** 创建固定 bucket 的适配器，不会创建或修改 bucket。 */
    public MinioFileStorageAdapter(MinioClient client, String bucket, String endpoint) {
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = requireText(bucket, "bucket");
        this.endpoint = requireText(endpoint, "endpoint").replaceAll("/+$", "");
    }

    /** 使用包含超时约束的配置创建适配器。 */
    public MinioFileStorageAdapter(MinioStorageConfiguration configuration) {
        this(MinioClientFactory.create(configuration), configuration.bucket(), configuration.endpoint());
    }

    /** 上传对象并映射统一元数据。 */
    @Override public ObjectMetadata put(UploadRequest request) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(request.key().value())
                    .stream(request.content(), request.contentLength(), DEFAULT_PART_SIZE)
                    .contentType(request.contentType()).headers(Map.of("Cache-Control", request.cacheControl().value())).build());
            return stat(request.key()).orElseThrow();
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** 获取对象流及元数据。 */
    @Override public Optional<DownloadObject> get(ObjectKey key) {
        Optional<ObjectMetadata> metadata = stat(key);
        if (metadata.isEmpty()) return Optional.empty();
        try { return Optional.of(new DownloadObject(client.getObject(GetObjectArgs.builder().bucket(bucket).object(key.value()).build()), metadata.get())); }
        catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** 查询对象元数据。 */
    @Override public Optional<ObjectMetadata> stat(ObjectKey key) {
        try { return Optional.of(metadata(key, client.statObject(StatObjectArgs.builder().bucket(bucket).object(key.value()).build()))); }
        catch (ErrorResponseException exception) { if (MinioExceptionMapper.isMissing(exception)) return Optional.empty(); throw MinioExceptionMapper.map(exception); }
        catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** 判断对象存在。 */
    @Override public boolean exists(ObjectKey key) { return stat(key).isPresent(); }

    /** 删除对象；缺失对象等价于已删除。 */
    @Override public boolean delete(ObjectKey key) {
        if (!exists(key)) return false;
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key.value()).build()); return true; }
        catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** 惰性枚举固定 bucket 中的对象元数据。 */
    @Override public Stream<ObjectMetadata> list() {
        Iterable<io.minio.Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket).recursive(true).build());
        return StreamSupport.stream(results.spliterator(), false).filter(this::isObject)
                .map(this::objectKey).map(this::requiredMetadata);
    }

    /** 生成绑定对象、类型和长度的原生 POST policy。 */
    @Override public PresignedUpload presignUpload(PresignUploadRequest request) {
        try {
            Instant expires = Instant.now().plus(request.expiresIn());
            PostPolicy policy = new PostPolicy(bucket, expires.atZone(ZoneOffset.UTC));
            policy.addEqualsCondition("key", request.key().value());
            policy.addEqualsCondition("Content-Type", request.contentType());
            policy.addEqualsCondition("Cache-Control", request.cacheControl().value());
            policy.addContentLengthRangeCondition(0, request.maxSizeBytes());
            Map<String, String> formFields = new HashMap<>(client.getPresignedPostFormData(policy));
            formFields.put("bucket", bucket);
            formFields.put("key", request.key().value());
            formFields.put("Content-Type", request.contentType());
            formFields.put("Cache-Control", request.cacheControl().value());
            return new PresignedUpload(endpoint + "/" + bucket, "POST", expires,
                    Map.of("Cache-Control", request.cacheControl().value()), formFields);
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** 生成带响应缓存和处置策略的原生 GET URL。 */
    @Override public PresignedDownload presignDownload(PresignDownloadRequest request) {
        try {
            int seconds = Math.toIntExact(request.expiresIn().toSeconds());
            Map<String, String> response = Map.of("response-content-disposition", request.disposition().name().toLowerCase(), "response-cache-control", request.cacheControl().value());
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucket).object(request.key().value()).method(Method.GET).expiry(seconds, TimeUnit.SECONDS).extraQueryParams(response).build());
            return new PresignedDownload(url, "GET", Instant.now().plusSeconds(seconds), request.disposition(), Map.of("Cache-Control", request.cacheControl().value()));
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** Creates an S3 multipart upload session. */
    @Override public MultipartUploadSession initiate(MultipartUploadRequest request) {
        try {
            var response = multipart().initiate(bucket, request.key().value(), request.contentType(),
                    com.google.common.collect.ArrayListMultimap.create(), com.google.common.collect.ArrayListMultimap.create());
            return new MultipartUploadSession(response.result().uploadId(), request,
                    Instant.now().plus(request.expiresIn()));
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** Signs an S3 UploadPart request bound to upload id and part number. */
    @Override public PresignedUpload presignPart(MultipartUploadSession session, MultipartPartRequest request) {
        try {
            int seconds = Math.toIntExact(session.expiresAt().getEpochSecond() - Instant.now().getEpochSecond());
            Map<String, String> query = Map.of("uploadId", session.uploadId(), "partNumber", String.valueOf(request.partNumber()));
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucket)
                    .object(session.key().value()).method(Method.PUT).expiry(Math.max(1, seconds))
                    .extraQueryParams(query).build());
            return new PresignedUpload(url, "PUT", session.expiresAt(), Map.of(), Map.of());
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** Uploads a part through the native S3 API. */
    @Override public MultipartPart uploadPart(MultipartUploadSession session, MultipartPartRequest request,
                                              java.io.InputStream content, long contentLength) {
        try {
            var response = multipart().upload(bucket, session.key().value(), session.uploadId(), content,
                    contentLength, session.contentType(), request.partNumber(),
                    com.google.common.collect.ArrayListMultimap.create(), com.google.common.collect.ArrayListMultimap.create());
            return new MultipartPart(request.partNumber(), contentLength, response.etag());
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** Lists currently committed S3 parts. */
    @Override public java.util.List<MultipartPart> listParts(MultipartUploadSession session) {
        try {
            var parts = multipart().list(bucket, session.key().value(), session.uploadId(), session.partCount(), 0,
                    com.google.common.collect.ArrayListMultimap.create(), com.google.common.collect.ArrayListMultimap.create())
                    .result().partList();
            return parts.stream().map(part -> new MultipartPart(part.partNumber(), part.partSize(), part.etag())).toList();
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** Completes an S3 multipart upload after submitting the client manifest. */
    @Override public ObjectMetadata complete(MultipartUploadSession session, MultipartCompletion completion) {
        try {
            Part[] parts = completion.parts().stream().map(part -> new Part(part.partNumber(), part.etag())).toArray(Part[]::new);
            multipart().complete(bucket, session.key().value(), session.uploadId(), parts,
                    com.google.common.collect.ArrayListMultimap.create(), com.google.common.collect.ArrayListMultimap.create());
            return stat(session.key()).orElseThrow();
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** Aborts an S3 multipart upload; provider reports missing uploads as idempotent success. */
    @Override public void abort(MultipartUploadSession session) {
        try {
            multipart().abort(bucket, session.key().value(), session.uploadId(),
                    com.google.common.collect.ArrayListMultimap.create(), com.google.common.collect.ArrayListMultimap.create());
        } catch (ErrorResponseException exception) {
            if (!"NoSuchUpload".equals(exception.errorResponse().code())) throw MinioExceptionMapper.map(exception);
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    private static ObjectMetadata metadata(ObjectKey key, StatObjectResponse stat) {
        String cache = stat.headers().get("Cache-Control");
        return new ObjectMetadata(key, stat.size(), stat.contentType(), stat.etag(), stat.lastModified().toInstant(), cache == null ? CacheControl.privateNoCache() : new CacheControl(cache));
    }

    private MinioMultipartGateway multipart() {
        if (multipart == null) {
            multipart = new MinioMultipartGateway(client);
        }
        return multipart;
    }
    /** 判断列举结果是否为可迁移的普通对象。 */
    private boolean isObject(io.minio.Result<Item> result) {
        try {
            Item item = result.get();
            return !item.isDir() && !item.isDeleteMarker();
        } catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** 从列举结果提取经过统一校验的对象 key。 */
    private ObjectKey objectKey(io.minio.Result<Item> result) {
        try { return ObjectKey.of(result.get().objectName()); }
        catch (Exception exception) { throw MinioExceptionMapper.map(exception); }
    }

    /** 查询完整元数据，确保离线迁移保留内容类型和缓存策略。 */
    private ObjectMetadata requiredMetadata(ObjectKey key) {
        return stat(key).orElseThrow(() -> MinioExceptionMapper.map(
                new IllegalStateException("listed object disappeared")));
    }
    private static String requireText(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value; }
}
