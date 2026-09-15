package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.minio.MinioFileStorageAdapter;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 使用固定 bucket 的 RustFS 文件存储适配器。
 *
 * RustFS 提供 S3 兼容 API，因此对象协议委托给已验证的 S3 适配器；本类保留 RustFS 独立的
 * 配置入口和稳定错误名称，不创建或修改 bucket。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-13
 */
public final class RustfsFileStorageAdapter implements FileStorage, MultipartFileStorage {
    private final FileStorage delegate;

    /**
     * 使用 RustFS 连接配置创建固定 bucket 适配器。
     *
     * @param configuration 已校验 RustFS 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    public RustfsFileStorageAdapter(RustfsStorageConfiguration configuration) {
        this(createDelegate(configuration));
    }

    /**
     * 使用后端中立委托创建适配器，仅供同包契约测试使用。
     *
     * @param delegate S3 兼容对象存储委托
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    RustfsFileStorageAdapter(FileStorage delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * 写入或覆盖 RustFS 对象。
     *
     * @param request 已校验上传请求
     * @return 服务端确认的对象元数据
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public ObjectMetadata put(UploadRequest request) {
        return invoke(() -> delegate.put(request));
    }

    /**
     * 获取 RustFS 对象流。
     *
     * @param key 已校验对象 key
     * @return 存在时的可关闭对象流
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public Optional<DownloadObject> get(ObjectKey key) {
        return invoke(() -> delegate.get(key));
    }

    /**
     * 查询 RustFS 对象元数据。
     *
     * @param key 已校验对象 key
     * @return 存在时的对象元数据
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public Optional<ObjectMetadata> stat(ObjectKey key) {
        return invoke(() -> delegate.stat(key));
    }

    /**
     * 判断 RustFS 对象是否存在。
     *
     * @param key 已校验对象 key
     * @return 存在时为 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public boolean exists(ObjectKey key) {
        return invoke(() -> delegate.exists(key));
    }

    /**
     * 删除 RustFS 对象。
     *
     * @param key 已校验对象 key
     * @return 删除既有对象时为 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public boolean delete(ObjectKey key) {
        return invoke(() -> delegate.delete(key));
    }

    /**
     * 惰性枚举 RustFS bucket 中的对象元数据。
     *
     * @return 调用方必须关闭的对象元数据流
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public Stream<ObjectMetadata> list() {
        return invoke(delegate::list);
    }

    /**
     * 生成 RustFS 原生 S3 POST 预签名上传。
     *
     * @param request 预签名上传请求
     * @return 服务端签发的 POST 表单
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public PresignedUpload presignUpload(PresignUploadRequest request) {
        return invoke(() -> delegate.presignUpload(request));
    }

    /**
     * 生成 RustFS 原生 S3 GET 预签名下载。
     *
     * @param request 预签名下载请求
     * @return 服务端签发的下载地址
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    @Override
    public PresignedDownload presignDownload(PresignDownloadRequest request) {
        return invoke(() -> delegate.presignDownload(request));
    }

    /** 创建 RustFS 原生 S3 multipart 会话。 */
    @Override
    public MultipartUploadSession initiate(MultipartUploadRequest request) {
        return invoke(() -> multipart().initiate(request));
    }

    /** 生成绑定 RustFS multipart 会话与分片号的上传授权。 */
    @Override
    public PresignedUpload presignPart(MultipartUploadSession session, MultipartPartRequest request) {
        return invoke(() -> multipart().presignPart(session, request));
    }

    /** 通过 RustFS S3 multipart API 上传单个分片。 */
    @Override
    public MultipartPart uploadPart(MultipartUploadSession session, MultipartPartRequest request,
                                    java.io.InputStream content, long contentLength) {
        return invoke(() -> multipart().uploadPart(session, request, content, contentLength));
    }

    /** 查询 RustFS 已确认的 multipart 分片。 */
    @Override
    public List<MultipartPart> listParts(MultipartUploadSession session) {
        return invoke(() -> multipart().listParts(session));
    }

    /** 提交 RustFS multipart 分片清单。 */
    @Override
    public ObjectMetadata complete(MultipartUploadSession session, MultipartCompletion completion) {
        return invoke(() -> multipart().complete(session, completion));
    }

    /** 取消 RustFS multipart 会话。 */
    @Override
    public void abort(MultipartUploadSession session) {
        invoke(() -> {
            multipart().abort(session);
            return null;
        });
    }

    /**
     * 创建仅针对预配置 bucket 的 S3 协议委托。
     *
     * @param configuration RustFS 连接配置
     * @return 不会创建 bucket 的统一存储委托
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static FileStorage createDelegate(RustfsStorageConfiguration configuration) {
        return new MinioFileStorageAdapter(RustfsClientFactory.create(configuration),
                configuration.bucket(), configuration.endpoint());
    }

    /** 获取 S3 兼容 multipart 委托。 */
    private MultipartFileStorage multipart() {
        if (delegate instanceof MultipartFileStorage multipart) {
            return multipart;
        }
        throw new FileStorageException(io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode.STORAGE_UNAVAILABLE,
                "RustFS operation failed");
    }

    /**
     * 执行对象操作并将底层公开错误归一为 RustFS 名称。
     *
     * @param operation 对象存储操作
     * @param <T> 操作返回类型
     * @return 操作结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-13
     */
    private static <T> T invoke(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (FileStorageException exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }
}
