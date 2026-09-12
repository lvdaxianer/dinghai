package io.github.lvdaxianer.dinghai.filestorage.core;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/** 稳定文件 ID 的认证加密测试。 */
public class StableFileIdCodecTest {

    /** 相同逻辑引用必须生成相同且不暴露 key 的 ID。 */
    @Test
    public void createsDeterministicOpaqueId() {
        StableFileIdCodec codec = codec("stable-file-id-test-key-material-32b".getBytes(
                StandardCharsets.UTF_8));
        FileReference reference = FileReference.of("documents", "用户资料/头像.png");

        String first = codec.encode(reference);
        String second = codec.encode(reference);

        assertEquals(first, second);
        assertEquals(reference, codec.decode(first));
        assertFalse(first.contains("documents"));
        assertFalse(first.contains("头像"));
    }

    /** 被篡改的稳定 ID 必须在读取对象前被拒绝。 */
    @Test
    public void rejectsTamperedId() {
        StableFileIdCodec codec = codec("stable-file-id-test-key-material-32b".getBytes(
                StandardCharsets.UTF_8));
        String identifier = codec.encode(FileReference.of("documents", "reports/a.pdf"));
        char replacement = identifier.endsWith("A") ? 'B' : 'A';

        assertInvalid(codec, identifier.substring(0, identifier.length() - 1) + replacement);
    }

    /** 密钥轮换后旧 ID 必须失效。 */
    @Test
    public void rejectsIdEncryptedWithPreviousKey() {
        StableFileIdCodec oldCodec = codec("stable-file-id-test-key-material-32b".getBytes(
                StandardCharsets.UTF_8));
        StableFileIdCodec currentCodec = codec("new-stable-file-id-test-key-material".getBytes(
                StandardCharsets.UTF_8));

        assertInvalid(currentCodec, oldCodec.encode(FileReference.of("documents", "reports/a.pdf")));
    }

    /** 创建测试编解码器。 */
    private static StableFileIdCodec codec(byte[] key) {
        return new StableFileIdCodec(key);
    }

    /** 断言 ID 解码失败。 */
    private static void assertInvalid(StableFileIdCodec codec, String identifier) {
        try {
            codec.decode(identifier);
            fail("expected invalid stable file ID");
        } catch (FileStorageException expected) {
            assertEquals(FileStorageErrorCode.INVALID_REQUEST, expected.errorCode());
        }
    }
}
