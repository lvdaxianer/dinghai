package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import java.util.Objects;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** RustFS 使用的 AWS S3 client 与 presigner 生命周期容器。 */
final class RustfsClients implements AutoCloseable {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    /** 创建客户端生命周期容器。 */
    RustfsClients(S3Client s3Client, S3Presigner presigner) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.presigner = Objects.requireNonNull(presigner, "presigner");
    }

    /** 获取 AWS S3 client。 */
    S3Client s3Client() {
        return s3Client;
    }

    /** 获取 AWS S3 presigner。 */
    S3Presigner presigner() {
        return presigner;
    }

    @Override
    public void close() {
        presigner.close();
        s3Client.close();
    }
}
