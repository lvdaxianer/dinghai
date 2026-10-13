package io.github.lvdaxianer.dinghai.filestorage.rustfs;

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
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Stream;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * 使用 AWS SDK v2 S3 API 实现的 RustFS 文件存储。
 *
 * RustFS 与 MinIO 都兼容 S3 协议，但该实现不依赖 MinIO SDK，保证选择 RustFS 时不会
 * 将 MinIO jar 带入应用运行时。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
final class RustfsS3FileStorage implements FileStorage, MultipartFileStorage, AutoCloseable {
    private final RustfsClients clients;
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    /** 创建绑定固定 bucket 的 RustFS S3 存储。 */
    RustfsS3FileStorage(RustfsClients clients, RustfsStorageConfiguration configuration) {
        this.clients = Objects.requireNonNull(clients, "clients");
        this.client = clients.s3Client();
        this.presigner = clients.presigner();
        this.bucket = Objects.requireNonNull(configuration, "configuration").bucket();
    }

    @Override
    public ObjectMetadata put(UploadRequest request) {
        try {
            PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(request.key().value())
                    .contentType(request.contentType()).cacheControl(request.cacheControl().value()).build();
            client.putObject(put, requestBody(request));
            return stat(request.key()).orElseThrow();
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public Optional<DownloadObject> get(ObjectKey key) {
        Optional<ObjectMetadata> metadata = stat(key);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key.value()).build();
            ResponseInputStream<GetObjectResponse> stream = client.getObject(request);
            return Optional.of(new DownloadObject(stream, metadata.get()));
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public Optional<ObjectMetadata> stat(ObjectKey key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(key.value()).build();
            return Optional.of(metadata(key, client.headObject(request)));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw RustfsExceptionMapper.map(exception);
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public boolean exists(ObjectKey key) {
        return stat(key).isPresent();
    }

    @Override
    public boolean delete(ObjectKey key) {
        if (stat(key).isEmpty()) {
            return false;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key.value()).build());
            return true;
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public Stream<ObjectMetadata> list() {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).build();
            return client.listObjectsV2Paginator(request).contents().stream()
                    .map(object -> requiredMetadata(ObjectKey.of(object.key())));
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public PresignedUpload presignUpload(PresignUploadRequest request) {
        try {
            Instant expires = Instant.now().plus(request.expiresIn());
            PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(request.key().value())
                    .contentType(request.contentType()).cacheControl(request.cacheControl().value()).build();
            PresignedPutObjectRequest signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(request.expiresIn()).putObjectRequest(put).build());
            return new PresignedUpload(signed.url().toString(), "PUT", expires,
                    Map.of("Content-Type", request.contentType(),
                            "Cache-Control", request.cacheControl().value()), Map.of());
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public PresignedDownload presignDownload(PresignDownloadRequest request) {
        try {
            Instant expires = Instant.now().plus(request.expiresIn());
            GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(request.key().value())
                    .responseContentDisposition(request.disposition().name().toLowerCase())
                    .responseCacheControl(request.cacheControl().value()).build();
            PresignedGetObjectRequest signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(request.expiresIn()).getObjectRequest(get).build());
            return new PresignedDownload(signed.url().toString(), "GET", expires,
                    request.disposition(), Map.of("Cache-Control", request.cacheControl().value()));
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public MultipartUploadSession initiate(MultipartUploadRequest request) {
        try {
            CreateMultipartUploadRequest create = CreateMultipartUploadRequest.builder().bucket(bucket)
                    .key(request.key().value()).contentType(request.contentType())
                    .cacheControl(request.cacheControl().value()).build();
            String uploadId = client.createMultipartUpload(create).uploadId();
            return new MultipartUploadSession(uploadId, request, Instant.now().plus(request.expiresIn()));
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public PresignedUpload presignPart(MultipartUploadSession session, MultipartPartRequest request) {
        try {
            Duration signatureDuration = remaining(session);
            UploadPartRequest uploadPart = UploadPartRequest.builder().bucket(bucket)
                    .key(session.key().value()).uploadId(session.uploadId())
                    .partNumber(request.partNumber()).build();
            var signed = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                    .signatureDuration(signatureDuration).uploadPartRequest(uploadPart).build());
            return new PresignedUpload(signed.url().toString(), "PUT", session.expiresAt(), Map.of(), Map.of());
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public MultipartPart uploadPart(MultipartUploadSession session, MultipartPartRequest request,
                                    InputStream content, long contentLength) {
        try {
            UploadPartRequest uploadPart = UploadPartRequest.builder().bucket(bucket)
                    .key(session.key().value()).uploadId(session.uploadId())
                    .partNumber(request.partNumber()).contentLength(contentLength).build();
            var response = client.uploadPart(uploadPart, RequestBody.fromInputStream(content, contentLength));
            return new MultipartPart(request.partNumber(), contentLength, response.eTag());
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public List<MultipartPart> listParts(MultipartUploadSession session) {
        try {
            var response = client.listParts(ListPartsRequest.builder().bucket(bucket)
                    .key(session.key().value()).uploadId(session.uploadId()).build());
            return response.parts().stream()
                    .map(part -> new MultipartPart(part.partNumber(), part.size(), part.eTag())).toList();
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public ObjectMetadata complete(MultipartUploadSession session, MultipartCompletion completion) {
        try {
            List<CompletedPart> parts = completion.parts().stream()
                    .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.etag()).build())
                    .toList();
            CompletedMultipartUpload upload = CompletedMultipartUpload.builder().parts(parts).build();
            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket)
                    .key(session.key().value()).uploadId(session.uploadId()).multipartUpload(upload).build());
            return stat(session.key()).orElseThrow();
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public void abort(MultipartUploadSession session) {
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket)
                    .key(session.key().value()).uploadId(session.uploadId()).build());
        } catch (S3Exception exception) {
            if (!"NoSuchUpload".equals(errorCode(exception))) {
                throw RustfsExceptionMapper.map(exception);
            }
        } catch (Exception exception) {
            throw RustfsExceptionMapper.map(exception);
        }
    }

    @Override
    public void close() {
        clients.close();
    }

    private RequestBody requestBody(UploadRequest request) {
        if (request.contentLength() >= 0) {
            return RequestBody.fromInputStream(request.content(), request.contentLength());
        }
        return RequestBody.fromContentProvider(() -> request.content(), request.contentType());
    }

    private ObjectMetadata requiredMetadata(ObjectKey key) {
        return stat(key).orElseThrow(() -> RustfsExceptionMapper.map(
                new IllegalStateException("listed object disappeared")));
    }

    private static ObjectMetadata metadata(ObjectKey key, HeadObjectResponse response) {
        String contentType = response.contentType();
        String etag = response.eTag() == null ? "" : response.eTag();
        Instant lastModified = response.lastModified() == null ? Instant.EPOCH : response.lastModified();
        String cacheControl = response.cacheControl();
        return new ObjectMetadata(key, response.contentLength(), contentType, etag, lastModified,
                cacheControl == null ? CacheControl.privateNoCache() : new CacheControl(cacheControl));
    }

    private static Duration remaining(MultipartUploadSession session) {
        Duration remaining = Duration.between(Instant.now(), session.expiresAt());
        return remaining.isNegative() || remaining.isZero() ? Duration.ofSeconds(1) : remaining;
    }

    private static String errorCode(S3Exception exception) {
        return exception.awsErrorDetails() == null ? null : exception.awsErrorDetails().errorCode();
    }
}
