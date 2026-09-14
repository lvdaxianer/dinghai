package io.github.lvdaxianer.dinghai.filestorage.oss;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.PolicyConditions;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import io.github.lvdaxianer.dinghai.filestorage.core.CacheControl;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 使用固定 bucket 的阿里云 OSS 文件存储适配器。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-14
 */
public final class OssFileStorageAdapter implements FileStorage {
    /** OSS 单次列举的最大对象数。 */
    private static final int LIST_PAGE_SIZE = 1_000;
    /** OSS POST 表单字段名称。 */
    private static final String FORM_KEY = "key";
    /** OSS POST 表单内容类型字段名称。 */
    private static final String FORM_CONTENT_TYPE = "Content-Type";
    /** OSS POST 表单缓存策略字段名称。 */
    private static final String FORM_CACHE_CONTROL = "Cache-Control";
    /** OSS POST 表单访问密钥字段名称。 */
    private static final String FORM_ACCESS_KEY = "OSSAccessKeyId";
    /** OSS POST 表单策略字段名称。 */
    private static final String FORM_POLICY = "policy";
    /** OSS POST 表单签名字段名称。 */
    private static final String FORM_SIGNATURE = "Signature";
    /** POST 方法名称。 */
    private static final String POST_METHOD = "POST";
    /** GET 方法名称。 */
    private static final String GET_METHOD = "GET";
    /** OSS 公有服务 endpoint 后缀。 */
    private static final String ALIYUN_OSS_HOST_SUFFIX = ".aliyuncs.com";
    /** 未知上传长度约定。 */
    private static final long UNKNOWN_CONTENT_LENGTH = -1L;
    /** POST policy 允许的最小对象长度。 */
    private static final long MIN_CONTENT_LENGTH = 0L;
    /** endpoint 不合法时的稳定诊断消息。 */
    private static final String INVALID_ENDPOINT_MESSAGE =
            "endpoint must be an absolute URI without query or fragment";
    /**
     * OSS SDK 客户端。
     *
     * 由配置构造器创建的客户端将在 Starter 中随 Spring 生命周期关闭；
     * 由测试或直接构造器传入时，关闭责任仍属于调用方。
     * 该客户端引用在适配器构造完成后保持不变。
     */
    private final OSS client;
    /**
     * 本适配器唯一允许访问的预先创建 bucket。
     *
     * 不从任何单次请求读取 bucket，避免调用方越过存储隔离边界。
     */
    private final String bucket;
    /**
     * 用于构造 POST policy action 的服务 endpoint。
     *
     * SDK 本身也使用此 endpoint 完成对象生命周期调用和签名。
     */
    private final String endpoint;
    /**
     * 仅用于 OSS POST policy 表单字段的固定 AccessKey。
     *
     * 适配器从不记录或公开该字段，签名始终由 SDK 计算。
     */
    private final String accessKey;

    /**
     * 使用包含超时约束的配置创建适配器。
     *
     * @param configuration 已校验的 OSS 连接配置
     */
    public OssFileStorageAdapter(OssStorageConfiguration configuration) {
        // 配置记录已校验 endpoint、bucket、凭据和全部网络超时。
        this(OssClientFactory.create(configuration), configuration.bucket(), configuration.endpoint(),
                configuration.accessKey());
    }

    /**
     * 使用既有 OSS 客户端创建固定 bucket 适配器。
     *
     * @param client 已配置的 OSS SDK 客户端
     * @param bucket 已预先创建的 bucket
     * @param endpoint OSS 服务 endpoint
     * @param accessKey 用于 POST policy 的固定 AccessKey
     */
    public OssFileStorageAdapter(OSS client, String bucket, String endpoint, String accessKey) {
        // 直接构造器供 Starter 注入受管 SDK 客户端及单元测试替身使用。
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = requireText(bucket, "bucket");
        this.endpoint = requireText(endpoint, "endpoint").replaceAll("/+$", "");
        this.accessKey = requireText(accessKey, "accessKey");
    }

    /**
     * 上传对象并读取服务端确认后的元数据。
     *
     * @param request 已校验的上传请求
     * @return OSS 服务端确认后的对象元数据
     */
    @Override
    public ObjectMetadata put(UploadRequest request) {
        try {
            com.aliyun.oss.model.ObjectMetadata metadata = new com.aliyun.oss.model.ObjectMetadata();
            // 未知长度不写入 -1，避免传递 OSS 不接受的内容长度。
            if (request.contentLength() > UNKNOWN_CONTENT_LENGTH) {
                metadata.setContentLength(request.contentLength());
            }
            metadata.setContentType(request.contentType());
            metadata.setCacheControl(request.cacheControl().value());
            client.putObject(bucket, request.key().value(), request.content(), metadata);
        } catch (Exception exception) {
            throw OssExceptionMapper.map(exception);
        }
        return stat(request.key()).orElseThrow(() -> OssExceptionMapper.map(
                new NoSuchElementException("uploaded object was not confirmed")));
    }

    /**
     * 获取对象流及元数据。
     *
     * @param key 对象 key
     * @return 对象不存在时为空，否则返回调用方负责关闭的流
     */
    @Override
    public Optional<DownloadObject> get(ObjectKey key) {
        Optional<ObjectMetadata> metadata = stat(key);
        // 先统一处理缺失语义，避免 getObject 的服务端差异泄露到契约外。
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        try {
            OSSObject object = client.getObject(bucket, key.value());
            return Optional.of(new DownloadObject(object.getObjectContent(), metadata.get()));
        } catch (OSSException exception) {
            // stat 与 getObject 间被删除的对象仍符合公共契约的缺失语义。
            if (OssExceptionMapper.isMissing(exception)) {
                return Optional.empty();
            }
            throw OssExceptionMapper.map(exception);
        } catch (Exception exception) {
            throw OssExceptionMapper.map(exception);
        }
    }

    /**
     * 查询对象元数据。
     *
     * @param key 对象 key
     * @return 对象不存在时为空
     */
    @Override
    public Optional<ObjectMetadata> stat(ObjectKey key) {
        try {
            return Optional.of(metadata(key, client.getObjectMetadata(bucket, key.value())));
        } catch (OSSException exception) {
            // OSS 的缺失错误是正常查询结果，不应当作为系统失败抛出。
            if (OssExceptionMapper.isMissing(exception)) {
                return Optional.empty();
            }
            throw OssExceptionMapper.map(exception);
        } catch (Exception exception) {
            throw OssExceptionMapper.map(exception);
        }
    }

    /**
     * 判断对象存在。
     *
     * @param key 对象 key
     * @return bucket 中存在该对象时返回 true
     */
    @Override
    public boolean exists(ObjectKey key) {
        return stat(key).isPresent();
    }

    /**
     * 删除对象；缺失对象等价于已删除。
     *
     * @param key 对象 key
     * @return 实际删除对象时返回 true
     */
    @Override
    public boolean delete(ObjectKey key) {
        // 先读取使 delete 满足公共契约中“缺失返回 false”的语义。
        if (!exists(key)) {
            return false;
        }
        try {
            client.deleteObject(bucket, key.value());
            return true;
        } catch (Exception exception) {
            throw OssExceptionMapper.map(exception);
        }
    }

    /**
     * 惰性分页枚举 bucket 中的完整对象元数据。
     *
     * @return 由调用方负责关闭的对象元数据流
     */
    @Override
    public Stream<ObjectMetadata> list() {
        // Spliterator 仅在消费者请求下一元素时读取下一页，不提前加载 bucket 内容。
        return StreamSupport.stream(new ObjectMetadataSpliterator(), false);
    }

    /**
     * 生成绑定对象、类型、缓存策略和长度的 OSS 原生 POST policy。
     *
     * @param request 预签名上传请求
     * @return OSS 所需的 POST action、表单字段和缓存头
     */
    @Override
    public PresignedUpload presignUpload(PresignUploadRequest request) {
        try {
            Instant expires = Instant.now().plus(request.expiresIn());
            PolicyConditions conditions = uploadConditions(request);
            String policy = client.generatePostPolicy(Date.from(expires), conditions);
            return new PresignedUpload(postUrl(), POST_METHOD, expires,
                    Map.of(FORM_CACHE_CONTROL, request.cacheControl().value()),
                    uploadFormFields(request, policy));
        } catch (Exception exception) {
            throw OssExceptionMapper.map(exception);
        }
    }

    /**
     * 生成带响应缓存和处置策略的 OSS 原生 GET URL。
     *
     * @param request 预签名下载请求
     * @return OSS 原生 GET URL 及响应覆盖约束
     */
    @Override
    public PresignedDownload presignDownload(PresignDownloadRequest request) {
        try {
            Instant expires = Instant.now().plus(request.expiresIn());
            GeneratePresignedUrlRequest signed = downloadRequest(request, expires);
            return new PresignedDownload(client.generatePresignedUrl(signed).toExternalForm(),
                    GET_METHOD, expires, request.disposition(),
                    Map.of(FORM_CACHE_CONTROL, request.cacheControl().value()));
        } catch (Exception exception) {
            throw OssExceptionMapper.map(exception);
        }
    }

    /**
     * 将 OSS 元数据转换为公共契约对象。
     *
     * @param key 对象 key
     * @param metadata OSS 服务端元数据
     * @return 后端中立的对象元数据
     */
    private ObjectMetadata metadata(ObjectKey key, com.aliyun.oss.model.ObjectMetadata metadata) {
        String cacheControl = metadata.getCacheControl();
        String contentType = metadata.getContentType();
        Date lastModified = metadata.getLastModified();
        // 缺少契约必填字段不能伪造默认值，交由外层转换为稳定失败。
        if (contentType == null || contentType.isBlank() || lastModified == null) {
            throw new IllegalStateException("OSS metadata is incomplete");
        }
        return new ObjectMetadata(key, metadata.getContentLength(), contentType, metadata.getETag(),
                lastModified.toInstant(), cacheControl == null ? CacheControl.privateNoCache()
                        : new CacheControl(cacheControl));
    }

    /**
     * 计算 OSS POST policy 的 action URL。
     *
     * @return 已按 OSS 虚拟主机规则处理的上传地址
     */
    private String postUrl() {
        /*
         * 公有 OSS endpoint 使用虚拟主机风格，POST action 必须包含 bucket 前缀。
         * 自定义域名通常已经绑定目标 bucket，继续添加前缀会破坏 CNAME 路由。
         * 该分支只影响浏览器 POST action，不改变 SDK 的 endpoint 配置。
         */
        URI uri = URI.create(endpoint);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        // 签名 action 不允许携带不受适配器控制的 query 或 fragment。
        if (scheme == null || host == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(INVALID_ENDPOINT_MESSAGE);
        }
        String bucketHost = host.endsWith(ALIYUN_OSS_HOST_SUFFIX) && !host.startsWith(bucket + ".")
                ? bucket + "." + host : host;
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        return scheme + "://" + bucketHost + port;
    }

    /**
     * 校验适配器构造参数。
     *
     * @param value 原始文本值
     * @param name 参数名称
     * @return 去除首尾空白后的文本值
     */
    private static String requireText(String value, String name) {
        // bucket、endpoint 和 AccessKey 均为签名和路由必需信息。
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /**
     * 按 marker 拉取 OSS 列表并惰性转换完整元数据。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-14
     */
    private final class ObjectMetadataSpliterator
            extends Spliterators.AbstractSpliterator<ObjectMetadata> {
        private Iterator<OSSObjectSummary> current = List.<OSSObjectSummary>of().iterator();
        private String marker;
        private boolean finished;

        /**
         * 创建有序且非空元素的惰性分页迭代器。
         */
        private ObjectMetadataSpliterator() {
            super(Long.MAX_VALUE, ORDERED | NONNULL);
        }

        /**
         * 提供下一个完整元数据对象。
         *
         * @param action 对下一元素的消费动作
         * @return 仍有元素时返回 true
         */
        @Override
        public boolean tryAdvance(Consumer<? super ObjectMetadata> action) {
            Objects.requireNonNull(action, "action");
            // 当前页耗尽时才请求下一页，保持流的惰性与分页边界。
            while (!current.hasNext() && !finished) {
                loadPage();
            }
            // OSS 已明确无下一页时终止流。
            if (!current.hasNext()) {
                return false;
            }
            ObjectKey key = ObjectKey.of(current.next().getKey());
            action.accept(completeMetadata(key));
            return true;
        }

        /**
         * 拉取下一页 OSS 对象摘要。
         */
        private void loadPage() {
            try {
                ObjectListing page = client.listObjects(new ListObjectsRequest(bucket)
                        .withMarker(marker).withMaxKeys(LIST_PAGE_SIZE));
                current = page.getObjectSummaries().iterator();
                // 缺失或重复 marker 表示继续请求将无法安全推进，终止避免死循环。
                if (!page.isTruncated() || page.getNextMarker() == null
                        || page.getNextMarker().equals(marker)) {
                    finished = true;
                } else {
                    marker = page.getNextMarker();
                }
            } catch (Exception exception) {
                throw OssExceptionMapper.map(exception);
            }
        }
    }

    /**
     * 创建限制上传属性的 OSS POST policy 条件。
     *
     * @param request 预签名上传请求
     * @return 固定对象属性和长度范围的 policy 条件
     */
    private static PolicyConditions uploadConditions(PresignUploadRequest request) {
        PolicyConditions conditions = new PolicyConditions();
        conditions.addConditionItem(PolicyConditions.COND_KEY, request.key().value());
        conditions.addConditionItem(PolicyConditions.COND_CONTENT_TYPE, request.contentType());
        conditions.addConditionItem(PolicyConditions.COND_CACHE_CONTROL, request.cacheControl().value());
        conditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, MIN_CONTENT_LENGTH,
                request.maxSizeBytes());
        return conditions;
    }

    /**
     * 创建 OSS POST policy 所需的固定表单字段。
     *
     * @param request 预签名上传请求
     * @param policy SDK 生成的 policy
     * @return 包含签名的表单字段
     */
    private Map<String, String> uploadFormFields(PresignUploadRequest request, String policy) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FORM_KEY, request.key().value());
        fields.put(FORM_CONTENT_TYPE, request.contentType());
        fields.put(FORM_CACHE_CONTROL, request.cacheControl().value());
        fields.put(FORM_ACCESS_KEY, accessKey);
        fields.put(FORM_POLICY, policy);
        fields.put(FORM_SIGNATURE, client.calculatePostSignature(policy));
        return fields;
    }

    /**
     * 创建带响应缓存和内容处置覆盖的 OSS GET 请求。
     *
     * @param request 预签名下载请求
     * @param expires URL 到期时间
     * @return 已完成响应头配置的 SDK 请求
     */
    private GeneratePresignedUrlRequest downloadRequest(PresignDownloadRequest request,
                                                         Instant expires) {
        GeneratePresignedUrlRequest signed = new GeneratePresignedUrlRequest(bucket,
                request.key().value(), HttpMethod.GET);
        signed.setExpiration(Date.from(expires));
        ResponseHeaderOverrides response = new ResponseHeaderOverrides();
        response.setCacheControl(request.cacheControl().value());
        response.setContentDisposition(request.disposition().name().toLowerCase(Locale.ROOT));
        signed.setResponseHeaders(response);
        return signed;
    }

    /**
     * 读取列举对象的完整元数据。
     *
     * @param key OSS 列表摘要中的对象 key
     * @return 可用于迁移的完整元数据
     */
    private ObjectMetadata completeMetadata(ObjectKey key) {
        /*
         * OSS 摘要缺失缓存策略；按设计以单次 stat 换取迁移语义完整性。
         * 此远端调用位于惰性消费路径，受调用方消费数量与 SDK 分页大小约束。
         * 若未来 SDK 提供完整摘要，可移除此查询但不得降低 metadata 契约。
         */
        return stat(key).orElseThrow(() -> OssExceptionMapper.map(
                new NoSuchElementException("listed object disappeared")));
    }
}
