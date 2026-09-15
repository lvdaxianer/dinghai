package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartFileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPartRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartUploadSession;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import java.io.InputStream;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 multipart provider 会话绑定到宿主授权主体并执行 Starter 资源校验。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
final class MultipartUploadService {

    private final MultipartFileStorage multipartStorage;
    private final FileStorageAccessPolicy accessPolicy;
    private final FileStorageProperties.Multipart properties;
    private final long maximumSize;
    private final Clock clock;
    private final ConcurrentHashMap<String, OwnedSession> sessions = new ConcurrentHashMap<>();

    /**
     * 创建受宿主策略保护的 multipart 会话服务。
     *
     * @param storage 已配置文件存储
     * @param accessPolicy 宿主对象访问策略
     * @param properties 文件存储配置
     * @param clock 当前时间源
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    MultipartUploadService(FileStorage storage, FileStorageAccessPolicy accessPolicy,
                           FileStorageProperties properties, Clock clock) {
        this.multipartStorage = storage instanceof MultipartFileStorage multipart
                ? multipart : null;
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        FileStorageProperties configuredProperties = Objects.requireNonNull(properties, "properties");
        this.properties = configuredProperties.getMultipart();
        this.maximumSize = configuredProperties.getUpload().getMaxSize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 创建并绑定 multipart 会话。
     *
     * @param principal 当前认证主体
     * @param request 已验证的创建请求
     * @return provider 创建的上传会话
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    MultipartUploadSession initiate(Principal principal, MultipartUploadRequest request) {
        Objects.requireNonNull(request, "request");
        String principalSubject = subject(principal);
        authorize(principal, request.key());
        validateRequest(request);
        MultipartUploadSession session = storage().initiate(request);
        sessions.put(session.uploadId(), new OwnedSession(principalSubject, session));
        return session;
    }

    /**
     * 为会话分片生成上传授权。
     *
     * @param principal 当前认证主体
     * @param uploadId 上传会话标识
     * @param partNumber 分片编号
     * @return provider 分片上传授权
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    PresignedUpload presignPart(Principal principal, String uploadId, int partNumber) {
        MultipartUploadSession session = session(principal, uploadId);
        return storage().presignPart(session, partRequest(session, partNumber));
    }

    /**
     * 返回服务端确认的已上传分片。
     *
     * @param principal 当前认证主体
     * @param uploadId 上传会话标识
     * @return 已确认分片清单
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    List<MultipartPart> listParts(Principal principal, String uploadId) {
        return storage().listParts(session(principal, uploadId));
    }

    /**
     * 接收 Local provider 的分片请求体。
     *
     * @param principal 当前认证主体
     * @param uploadId 上传会话标识
     * @param partNumber 分片编号
     * @param content 分片输入流
     * @param contentLength 请求体长度
     * @return 已确认分片
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    MultipartPart uploadPart(Principal principal, String uploadId, int partNumber,
                             InputStream content, long contentLength) {
        MultipartUploadSession session = session(principal, uploadId);
        return storage().uploadPart(session, partRequest(session, partNumber), content, contentLength);
    }

    /**
     * 判断当前 provider 是否需要 Starter 接收本地分片数据。
     *
     * @return Local provider 时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    boolean acceptsLocalPartUpload() {
        return multipartStorage instanceof io.github.lvdaxianer.dinghai.filestorage.local.LocalFileStorageAdapter;
    }

    /**
     * 完成会话；相同会话重复完成返回首次确认的对象元数据。
     *
     * @param principal 当前认证主体
     * @param uploadId 上传会话标识
     * @param completion 客户端完成清单
     * @return 已完成对象元数据
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    ObjectMetadata complete(Principal principal, String uploadId, MultipartCompletion completion) {
        OwnedSession owned = ownedSession(principal, uploadId);
        synchronized (owned) {
            if (owned.completed != null) {
                return owned.completed;
            }
            session(principal, uploadId);
            owned.completed = storage().complete(owned.session, completion);
            return owned.completed;
        }
    }

    /**
     * 取消会话并清理主体绑定；重复取消保持幂等。
     *
     * @param principal 当前认证主体
     * @param uploadId 上传会话标识
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    void abort(Principal principal, String uploadId) {
        OwnedSession owned = ownedSession(principal, uploadId);
        synchronized (owned) {
            if (!owned.aborted && owned.completed == null) {
                storage().abort(owned.session);
                owned.aborted = true;
            }
        }
    }

    private void validateRequest(MultipartUploadRequest request) {
        if (request.totalSize() > maximumSize || request.partSize() != properties.getPartSize().toBytes()
                || request.partCount() > properties.getMaxParts()
                || request.expiresIn().compareTo(properties.getSessionTtl()) > 0) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_INVALID,
                    "multipart request exceeds configured limits");
        }
    }

    private MultipartFileStorage storage() {
        if (multipartStorage == null) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_INVALID,
                    "multipart upload is not supported by the selected provider");
        }
        return multipartStorage;
    }

    private MultipartUploadSession session(Principal principal, String uploadId) {
        OwnedSession owned = ownedSession(principal, uploadId);
        if (!owned.session.expiresAt().isAfter(clock.instant())) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_EXPIRED,
                    "multipart session has expired");
        }
        if (owned.aborted) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_NOT_FOUND,
                    "multipart session was cancelled");
        }
        if (owned.completed != null) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_CONFLICT,
                    "multipart session has already completed");
        }
        return owned.session;
    }

    private OwnedSession ownedSession(Principal principal, String uploadId) {
        OwnedSession owned = sessions.get(uploadId);
        if (owned == null || !owned.subject.equals(subject(principal))) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_NOT_FOUND,
                    "multipart session not found");
        }
        authorize(principal, owned.session.key());
        return owned;
    }

    private MultipartPartRequest partRequest(MultipartUploadSession session, int partNumber) {
        try {
            return new MultipartPartRequest(session.uploadId(), partNumber, session.partCount());
        } catch (IllegalArgumentException exception) {
            throw new FileStorageException(FileStorageErrorCode.MULTIPART_INVALID,
                    "multipart part number is invalid", exception);
        }
    }

    private void authorize(Principal principal, ObjectKey key) {
        if (!accessPolicy.isAllowed(principal, key, FileStorageAccessOperation.MULTIPART_UPLOAD)) {
            throw new FileStorageException(FileStorageErrorCode.INVALID_PRESIGN, "file access denied");
        }
    }

    private static String subject(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new FileStorageException(FileStorageErrorCode.INVALID_PRESIGN, "file access denied");
        }
        return principal.getName();
    }

    /** 已授权主体持有的 provider 会话与幂等完成状态。 */
    private static final class OwnedSession {
        private final String subject;
        private final MultipartUploadSession session;
        private ObjectMetadata completed;
        private boolean aborted;

        private OwnedSession(String subject, MultipartUploadSession session) {
            this.subject = subject;
            this.session = session;
        }
    }
}
