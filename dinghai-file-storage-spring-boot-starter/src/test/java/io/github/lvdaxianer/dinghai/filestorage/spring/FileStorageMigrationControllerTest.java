package io.github.lvdaxianer.dinghai.filestorage.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lvdaxianer.dinghai.filestorage.core.DownloadObject;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorage;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectKey;
import io.github.lvdaxianer.dinghai.filestorage.core.ObjectMetadata;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignDownloadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignUploadRequest;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedDownload;
import io.github.lvdaxianer.dinghai.filestorage.core.PresignedUpload;
import io.github.lvdaxianer.dinghai.filestorage.core.UploadRequest;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 离线迁移管理接口授权测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-12
 */
public class FileStorageMigrationControllerTest {
    /** 临时迁移目录。 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 拒绝策略必须在导出前阻断管理操作。 */
    @Test
    public void shouldRejectExportWhenHostPolicyDenies() throws Exception {
        FileStorageMigrationController controller = controller((principal, operation) -> false);

        try {
            controller.export(() -> "operator", "release-001");
            fail("expected migration access denial");
        } catch (FileStorageException expected) {
            assertEquals(FileStorageErrorCode.AUTHENTICATION_FAILED, expected.errorCode());
        }
    }

    /** 创建配置完整且不可执行实际写入的迁移控制器。 */
    private FileStorageMigrationController controller(FileStorageMigrationAccessPolicy policy)
            throws Exception {
        Path root = temporaryFolder.newFolder("exports").toPath();
        FileStorageMigrationService service = new FileStorageMigrationService(new EmptyFileStorage(),
                "documents", root, new ObjectMapper());
        FileStorageProperties properties = new FileStorageProperties();
        properties.getMigration().setImportRoot(temporaryFolder.newFolder("imports").toPath());
        properties.getMigration().setCompletedRoot(temporaryFolder.newFolder("completed").toPath());
        properties.getMigration().setFailedRoot(temporaryFolder.newFolder("failed").toPath());
        return new FileStorageMigrationController(service, properties, policy);
    }

    /** 不包含对象的迁移存储替身。 */
    private static final class EmptyFileStorage implements FileStorage {
        /** 枚举空对象集合。 */
        @Override
        public Stream<ObjectMetadata> list() {
            return Stream.empty();
        }

        /** 查询不到任何对象。 */
        @Override
        public Optional<DownloadObject> get(ObjectKey key) {
            return Optional.empty();
        }

        /** 查询不到任何对象元数据。 */
        @Override
        public Optional<ObjectMetadata> stat(ObjectKey key) {
            return Optional.empty();
        }

        /** 本测试替身不支持写入。 */
        @Override
        public ObjectMetadata put(UploadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 对象始终不存在。 */
        @Override
        public boolean exists(ObjectKey key) {
            return false;
        }

        /** 本测试替身不支持删除。 */
        @Override
        public boolean delete(ObjectKey key) {
            throw new UnsupportedOperationException();
        }

        /** 本测试替身不支持预签名上传。 */
        @Override
        public PresignedUpload presignUpload(PresignUploadRequest request) {
            throw new UnsupportedOperationException();
        }

        /** 本测试替身不支持预签名下载。 */
        @Override
        public PresignedDownload presignDownload(PresignDownloadRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
