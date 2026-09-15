package io.github.lvdaxianer.dinghai.filestorage.minio;

import com.google.common.collect.Multimap;
import io.minio.AbortMultipartUploadResponse;
import io.minio.CreateMultipartUploadResponse;
import io.minio.ListPartsResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.UploadPartResponse;
import io.minio.messages.Part;

/** Exposes MinIO SDK multipart primitives to the storage adapter. */
final class MinioMultipartGateway extends io.minio.MinioAsyncClient {
    MinioMultipartGateway(MinioClient delegate) {
        super(asyncClient(delegate));
    }

    private static io.minio.MinioAsyncClient asyncClient(MinioClient delegate) {
        try {
            java.lang.reflect.Field field = MinioClient.class.getDeclaredField("asyncClient");
            field.setAccessible(true);
            return (io.minio.MinioAsyncClient) field.get(delegate);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new IllegalStateException("minio multipart client is unavailable", exception);
        }
    }

    CreateMultipartUploadResponse initiate(String bucket, String object, String contentType,
                                           Multimap<String, String> headers,
                                           Multimap<String, String> query) throws Exception {
        return createMultipartUpload(bucket, object, contentType, headers, query);
    }

    UploadPartResponse upload(String bucket, String object, String uploadId, Object content,
                              long length, String contentType, int partNumber,
                              Multimap<String, String> headers,
                              Multimap<String, String> query) throws Exception {
        return uploadPart(bucket, object, uploadId, content, length, contentType, partNumber, headers, query);
    }

    ListPartsResponse list(String bucket, String object, String uploadId, int maxParts,
                           int marker, Multimap<String, String> headers,
                           Multimap<String, String> query) throws Exception {
        return listParts(bucket, object, uploadId, maxParts, marker, null, headers, query);
    }

    ObjectWriteResponse complete(String bucket, String object, String uploadId, Part[] parts,
                                 Multimap<String, String> headers,
                                 Multimap<String, String> query) throws Exception {
        return completeMultipartUpload(bucket, object, uploadId, null, parts, headers, query);
    }

    AbortMultipartUploadResponse abort(String bucket, String object, String uploadId,
                                       Multimap<String, String> headers,
                                       Multimap<String, String> query) throws Exception {
        return abortMultipartUpload(bucket, object, uploadId, null, headers, query);
    }
}
