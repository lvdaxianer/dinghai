package io.github.lvdaxianer.dinghai.filestorage.rustfs;

import java.time.Duration;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * RustFS 独立 S3 客户端工厂测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-10-13
 */
public class RustfsClientFactoryTest {

    /** RustFS 工厂必须创建 AWS S3 client 与 presigner，不能依赖 MinIO client。 */
    @Test
    public void createsIndependentS3Clients() {
        RustfsStorageConfiguration configuration = new RustfsStorageConfiguration(
                "https://rustfs.example", "uploads", "access", "secret",
                Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(4));

        try (RustfsClients clients = RustfsClientFactory.create(configuration)) {
            assertNotNull(clients.s3Client());
            assertNotNull(clients.presigner());
        }
    }
}
