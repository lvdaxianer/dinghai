package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

import com.obs.services.IObsCredentialsProvider;
import com.obs.services.ObsClient;
import com.obs.services.model.AbortMultipartUploadRequest;
import com.obs.services.model.CompleteMultipartUploadRequest;
import com.obs.services.model.GetObjectMetadataRequest;
import com.obs.services.model.GetObjectRequest;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.InitiateMultipartUploadRequest;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ListPartsRequest;
import com.obs.services.model.Multipart;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PartEtag;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import com.obs.services.model.UploadPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** 使用固定 bucket 的华为云 OBS 文件存储适配器。 */
public final class HuaweiObsFileStorageAdapter implements FileStorage, MultipartFileStorage,
        AutoCloseable {
    /** OBS 单页最大对象数。 */
    private static final int LIST_PAGE_SIZE = 1_000;
    /** OBS 单页最大分片数。 */
    private static final int LIST_PART_PAGE_SIZE = 1_000;
    /** 预签名上传 HTTP 方法。 */
    private static final String PUT_METHOD = "PUT";
    /** 预签名下载 HTTP 方法。 */
    private static final String GET_METHOD = "GET";
    private final ObsClient client;
    private final String bucket;

    /** 使用已创建的 OBS client 创建适配器。 */
    public HuaweiObsFileStorageAdapter(ObsClient client, String bucket, String endpoint) {
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = requireText(bucket, "bucket");
        requireText(endpoint, "endpoint");
    }

    /** 根据静态配置创建适配器。 */
    public HuaweiObsFileStorageAdapter(HuaweiObsStorageConfiguration configuration) {
        this(HuaweiObsClientFactory.create(configuration), configuration.bucket(), configuration.endpoint());
    }

    /** 根据可刷新凭证 Provider 创建适配器。 */
    public HuaweiObsFileStorageAdapter(HuaweiObsStorageConfiguration configuration,
                                       IObsCredentialsProvider provider) {
        this(HuaweiObsClientFactory.create(configuration, provider), configuration.bucket(),
                configuration.endpoint());
    }

    /** 上传对象并通过 metadata 确认服务端结果。 */
    @Override
    public io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata put(UploadRequest request) {
        try {
            client.putObject(putRequest(request));
            return confirmedMetadata(request.key());
        } catch (Exception exception) {
            throw HuaweiObsExceptionMapper.map(exception);
        }
    }

    /** 获取对象流并保留底层流的关闭语义。 */
    @Override
    public Optional<DownloadObject> get(ObjectKey key) {
        try {
            ObjectMetadata metadata = client.getObjectMetadata(metadataRequest(key));
            ObsObject object = Objects.requireNonNull(
                    client.getObject(new GetObjectRequest(bucket, key.value())), "object");
            return Optional.of(new DownloadObject(object.getObjectContent(), metadata(key, metadata)));
        } catch (Exception exception) {
            return missingOrThrow(exception);
        }
    }

    /** 查询对象元数据。 */
    @Override
    public Optional<io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata> stat(ObjectKey key) {
        try {
            return Optional.of(metadata(key, client.getObjectMetadata(metadataRequest(key))));
        } catch (Exception exception) {
            return missingOrThrow(exception);
        }
    }

    /** 判断对象是否存在。 */
    @Override
    public boolean exists(ObjectKey key) {
        return stat(key).isPresent();
    }

    /** 删除对象；缺失对象等价于未删除。 */
    @Override
    public boolean delete(ObjectKey key) {
        if (!exists(key)) {
            return false;
        }
        try {
            client.deleteObject(bucket, key.value());
            return true;
        } catch (Exception exception) {
            throw HuaweiObsExceptionMapper.map(exception);
        }
    }

    /** 惰性分页列举固定 bucket 中的对象。 */
    @Override
    public Stream<io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata> list() {
        return StreamSupport.stream(new ObjectSpliterator(), false);
    }

    /** 生成绑定对象和请求头的 PUT 预签名 URL。 */
    @Override
    public PresignedUpload presignUpload(PresignUploadRequest request) {
        Instant expiresAt = Instant.now().plus(request.expiresIn());
        Map<String, String> headers = headers(request.contentType(), request.cacheControl());
        TemporarySignatureResponse response = sign(HttpMethodEnum.PUT, request.key(), expiresAt,
                headers, Map.of());
        return new PresignedUpload(response.getSignedUrl(), PUT_METHOD, expiresAt,
                response.getActualSignedRequestHeaders(), Map.of());
    }

    /** 生成带响应处置和缓存参数的 GET 预签名 URL。 */
    @Override
    public PresignedDownload presignDownload(PresignDownloadRequest request) {
        Instant expiresAt = Instant.now().plus(request.expiresIn());
        Map<String, Object> query = new HashMap<>(2);
        query.put("response-content-disposition", request.disposition().name().toLowerCase());
        query.put("response-cache-control", request.cacheControl().value());
        TemporarySignatureResponse response = sign(HttpMethodEnum.GET, request.key(), expiresAt,
                Map.of(), query);
        return new PresignedDownload(response.getSignedUrl(), GET_METHOD, expiresAt,
                request.disposition(), Map.of("Cache-Control", request.cacheControl().value()));
    }

    /** 初始化 OBS multipart 上传会话。 */
    @Override
    public MultipartUploadSession initiate(MultipartUploadRequest request) {
        try {
            InitiateMultipartUploadRequest initiate = new InitiateMultipartUploadRequest(
                    bucket, request.key().value());
            initiate.setMetadata(objectMetadata(request.contentType(), request.cacheControl(), null));
            String uploadId = client.initiateMultipartUpload(initiate).getUploadId();
            return new MultipartUploadSession(requireText(uploadId, "uploadId"), request,
                    Instant.now().plus(request.expiresIn()));
        } catch (Exception exception) {
            throw HuaweiObsExceptionMapper.map(exception);
        }
    }

    /** 生成绑定 multipart 会话和分片编号的 PUT 预签名 URL。 */
    @Override
    public PresignedUpload presignPart(MultipartUploadSession session, MultipartPartRequest request) {
        Instant expiresAt = session.expiresAt();
        Map<String, Object> query = new HashMap<>(2);
        query.put("uploadId", session.uploadId());
        query.put("partNumber", request.partNumber());
        TemporarySignatureResponse response = sign(HttpMethodEnum.PUT, session.key(), expiresAt,
                Map.of(), query);
        return new PresignedUpload(response.getSignedUrl(), PUT_METHOD, expiresAt,
                response.getActualSignedRequestHeaders(), Map.of());
    }

    /** 通过 OBS SDK 流式上传一个分片。 */
    @Override
    public MultipartPart uploadPart(MultipartUploadSession session, MultipartPartRequest request,
                                    InputStream content, long contentLength) {
        try {
            UploadPartRequest upload = new UploadPartRequest(bucket, session.key().value(),
                    (long) request.partNumber(), content);
            upload.setUploadId(session.uploadId());
            upload.setPartSize(contentLength);
            upload.setAutoClose(false);
            String etag = client.uploadPart(upload).getEtag();
            return new MultipartPart(request.partNumber(), contentLength, requireText(etag, "etag"));
        } catch (Exception exception) {
            throw HuaweiObsExceptionMapper.map(exception);
        }
    }

    /** 分页查询已上传分片并按分片号排序。 */
    @Override
    public List<MultipartPart> listParts(MultipartUploadSession session) {
        try {
            List<MultipartPart> parts = new ArrayList<>(LIST_PART_PAGE_SIZE);
            Integer marker = null;
            boolean truncated;
            do {
                var request = new ListPartsRequest(bucket, session.key().value(),
                        session.uploadId(), LIST_PART_PAGE_SIZE, marker);
                var page = client.listParts(request);
                addParts(parts, page.getMultipartList());
                Integer next = parseMarker(page.getNextPartNumberMarker());
                truncated = page.isTruncated() && next != null && !next.equals(marker);
                marker = next;
            } while (truncated);
            return parts.stream().sorted(java.util.Comparator.comparingInt(MultipartPart::partNumber)).toList();
        } catch (Exception exception) {
            throw HuaweiObsExceptionMapper.map(exception);
        }
    }

    /** 按调用方提交的 ETag 清单完成 multipart 上传。 */
    @Override
    public io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata complete(
            MultipartUploadSession session, MultipartCompletion completion) {
        try {
            List<PartEtag> etags = completion.parts().stream()
                    .map(part -> new PartEtag(part.etag(), part.partNumber())).toList();
            client.completeMultipartUpload(new CompleteMultipartUploadRequest(bucket,
                    session.key().value(), session.uploadId(), etags));
            return confirmedMetadata(session.key());
        } catch (Exception exception) {
            throw HuaweiObsExceptionMapper.map(exception);
        }
    }

    /** 取消 multipart 上传，缺失会话视为幂等成功。 */
    @Override
    public void abort(MultipartUploadSession session) {
        try {
            client.abortMultipartUpload(new AbortMultipartUploadRequest(bucket,
                    session.key().value(), session.uploadId()));
        } catch (Exception exception) {
            if (!HuaweiObsExceptionMapper.isMissingMultipart(exception)) {
                throw HuaweiObsExceptionMapper.map(exception);
            }
        }
    }

    /** 关闭 SDK client 及其底层连接池。 */
    @Override
    public void close() throws Exception {
        client.close();
    }

    /** 构造普通上传请求。 */
    private PutObjectRequest putRequest(UploadRequest request) {
        PutObjectRequest put = new PutObjectRequest(bucket, request.key().value(), request.content());
        put.setMetadata(objectMetadata(request.contentType(), request.cacheControl(),
                request.contentLength() < 0 ? null : request.contentLength()));
        put.setAutoClose(false);
        return put;
    }

    /** 创建 metadata 查询请求。 */
    private GetObjectMetadataRequest metadataRequest(ObjectKey key) {
        return new GetObjectMetadataRequest(bucket, key.value());
    }

    /** 确认已上传对象的最终元数据。 */
    private io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata confirmedMetadata(ObjectKey key) {
        return stat(key).orElseThrow(() -> HuaweiObsExceptionMapper.map(
                new IllegalStateException("uploaded object was not confirmed")));
    }

    /** 绑定缓存和内容类型请求头。 */
    private static Map<String, String> headers(String contentType, CacheControl cacheControl) {
        Map<String, String> headers = new HashMap<>(2);
        headers.put("Content-Type", contentType);
        headers.put("Cache-Control", cacheControl.value());
        return Map.copyOf(headers);
    }

    /** 生成一个 OBS 临时签名请求。 */
    private TemporarySignatureResponse sign(HttpMethodEnum method, ObjectKey key, Instant expiresAt,
                                            Map<String, String> headers,
                                            Map<String, Object> query) {
        long seconds = Math.max(1, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        TemporarySignatureRequest request = new TemporarySignatureRequest(method, bucket,
                key.value(), null, seconds, new Date());
        request.setHeaders(headers);
        request.setQueryParams(query);
        return client.createTemporarySignature(request);
    }

    /** 转换 OBS metadata 为统一对象 metadata。 */
    private static io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata metadata(
            ObjectKey key, ObjectMetadata metadata) {
        long length = Objects.requireNonNull(metadata.getContentLength(), "contentLength");
        String contentType = metadata.getContentType();
        String etag = requireText(metadata.getEtag(), "etag");
        Instant modified = Objects.requireNonNull(metadata.getLastModified(), "lastModified").toInstant();
        CacheControl cache = metadata.getCacheControl() == null
                ? CacheControl.privateNoCache() : new CacheControl(metadata.getCacheControl());
        return new io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata(key, length,
                contentType, etag, modified, cache);
    }

    /** 构造 OBS 上传 metadata。 */
    private static ObjectMetadata objectMetadata(String contentType, CacheControl cacheControl,
                                                 Long contentLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setCacheControl(cacheControl.value());
        if (contentLength != null) {
            metadata.setContentLength(contentLength);
        }
        return metadata;
    }

    /** 将 OBS 异常按缺失对象语义转换为空结果。 */
    private static <T> Optional<T> missingOrThrow(Exception exception) {
        if (HuaweiObsExceptionMapper.isMissingObject(exception)) {
            return Optional.empty();
        }
        throw HuaweiObsExceptionMapper.map(exception);
    }

    /** 追加 OBS 返回的已上传分片。 */
    private static void addParts(List<MultipartPart> target, List<Multipart> source) {
        for (Multipart part : source == null ? List.<Multipart>of() : source) {
            target.add(new MultipartPart(part.getPartNumber(), part.getSize(), part.getEtag()));
        }
    }

    /** 解析 OBS 下一页分片 marker。 */
    private static Integer parseMarker(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    /** 校验并返回非空文本。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** 惰性读取 OBS 对象列表。 */
    private final class ObjectSpliterator extends Spliterators.AbstractSpliterator<io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata> {
        private Iterator<ObsObject> current = List.<ObsObject>of().iterator();
        private String marker;
        private boolean finished;

        /** 创建有序的非空列表游标。 */
        private ObjectSpliterator() {
            super(Long.MAX_VALUE, ORDERED | NONNULL);
        }

        /** 读取一个对象元数据。 */
        @Override
        public boolean tryAdvance(Consumer<? super io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata> action) {
            Objects.requireNonNull(action, "action");
            loadUntilAvailable();
            if (!current.hasNext()) {
                return false;
            }
            ObsObject object = current.next();
            action.accept(metadata(ObjectKey.of(object.getObjectKey()), object.getMetadata()));
            return true;
        }

        /** 加载下一页直到有对象或列举结束。 */
        private void loadUntilAvailable() {
            while (!current.hasNext() && !finished) {
                loadPage();
            }
        }

        /** 请求并缓存下一页对象。 */
        private void loadPage() {
            try {
                ListObjectsRequest request = new ListObjectsRequest(bucket);
                request.setMaxKeys(LIST_PAGE_SIZE);
                request.setMarker(marker);
                ObjectListing page = client.listObjects(request);
                current = page.getObjects() == null
                        ? List.<ObsObject>of().iterator() : page.getObjects().iterator();
                updatePageState(page);
            } catch (Exception exception) {
                throw HuaweiObsExceptionMapper.map(exception);
            }
        }

        /** 更新分页 marker 并防止服务端 token 不推进造成死循环。 */
        private void updatePageState(ObjectListing page) {
            String next = page.getNextMarker();
            finished = !page.isTruncated() || Objects.equals(marker, next);
            marker = next;
        }
    }
}
