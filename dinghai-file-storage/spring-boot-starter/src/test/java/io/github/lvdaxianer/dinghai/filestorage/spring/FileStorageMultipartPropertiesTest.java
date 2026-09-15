package io.github.lvdaxianer.dinghai.filestorage.spring;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.Test;
import org.springframework.util.unit.DataSize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * multipart 上传配置的边界测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public class FileStorageMultipartPropertiesTest {

    /**
     * 默认配置必须适配对象存储 multipart 约束。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void providesBoundedMultipartDefaults() {
        FileStorageProperties.Multipart multipart = new FileStorageProperties().getMultipart();

        assertEquals(DataSize.ofMegabytes(10L), multipart.getPartSize());
        assertEquals(10_000, multipart.getMaxParts());
        assertEquals(Duration.ofHours(24L), multipart.getSessionTtl());
        assertEquals(8, multipart.getMaxConcurrentUploads());
        assertNotNull(multipart.getLocalRoot());
    }

    /**
     * 小于 provider 共同最小值的分片大小必须在校验阶段失败。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void rejectsPartSizeBelowProviderMinimum() {
        FileStorageProperties.Multipart multipart = new FileStorageProperties().getMultipart();
        multipart.setPartSize(DataSize.ofMegabytes(1L));

        assertInvalid(multipart);
    }

    /**
     * 超出 provider 共同上限的分片数量必须被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void rejectsPartCountAboveProviderMaximum() {
        FileStorageProperties.Multipart multipart = new FileStorageProperties().getMultipart();
        multipart.setMaxParts(10_001);

        assertInvalid(multipart);
    }

    /**
     * 非正会话保留时间必须被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void rejectsNonPositiveSessionTtl() {
        FileStorageProperties.Multipart multipart = new FileStorageProperties().getMultipart();
        multipart.setSessionTtl(Duration.ZERO);

        assertInvalid(multipart);
    }

    /**
     * 缺失本地临时根目录必须被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void rejectsMissingLocalRoot() {
        FileStorageProperties.Multipart multipart = new FileStorageProperties().getMultipart();
        multipart.setLocalRoot((Path) null);

        assertInvalid(multipart);
    }

    /**
     * 非正并发上传上限必须被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    @Test
    public void rejectsNonPositiveConcurrentUploadLimit() {
        FileStorageProperties.Multipart multipart = new FileStorageProperties().getMultipart();
        multipart.setMaxConcurrentUploads(0);

        assertInvalid(multipart);
    }

    /**
     * 断言 multipart 属性校验以稳定错误失败。
     *
     * @param multipart 待校验的 multipart 配置
     * @author lvdaxianer@yeah.net
     * @date 2026-09-15
     */
    private static void assertInvalid(FileStorageProperties.Multipart multipart) {
        try {
            multipart.validate();
            fail("expected multipart configuration validation failure");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
