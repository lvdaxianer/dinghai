package io.github.lvdaxianer.dinghai.filestorage.core;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 对象 key 的安全和 Unicode 兼容性测试。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-11
 */
public class ObjectKeyTest {

    /** 中文和多级相对 key 应保持规范化后的逻辑值。 */
    @Test
    public void acceptsChineseNestedKey() {
        assertEquals("用户资料/头像/1.png", ObjectKey.of("用户资料/头像/1.png").value());
    }

    /** UTF-8 百分号编码的中文 key 应还原为相同逻辑路径。 */
    @Test
    public void decodesUtf8PercentEncodedChineseKey() {
        assertEquals("中文目录/板卡_PSR-663UG-DM-1-HMI.gltf",
                ObjectKey.of("%E4%B8%AD%E6%96%87%E7%9B%AE%E5%BD%95/"
                        + "%E6%9D%BF%E5%8D%A1_PSR-663UG-DM-1-HMI.gltf").value());
    }

    /** NFC 规范化应在校验前完成。 */
    @Test
    public void normalizesUnicodeToNfc() {
        assertEquals("café.txt", ObjectKey.of("cafe\u0301.txt").value());
    }

    /** 路径穿越片段必须被拒绝。 */
    @Test(expected = IllegalArgumentException.class)
    public void rejectsTraversal() {
        ObjectKey.of("tenant/../secret.txt");
    }

    /** URL 解码后形成的路径穿越片段也必须被拒绝。 */
    @Test(expected = IllegalArgumentException.class)
    public void rejectsDecodedTraversal() {
        ObjectKey.of("tenant/%2e%2e/secret.txt");
    }

    /** 空 key、绝对 key、空路径段和反斜杠均不安全。 */
    @Test
    public void rejectsUnsafePathForms() {
        assertRejected("");
        assertRejected("/tmp/a");
        assertRejected("a//b");
        assertRejected("a\\b");
        assertRejected(".");
        assertRejected("a/./b");
        assertRejected("a\u0000b");
        assertRejected("a\n b");
    }

    /** 单段 255 字节应允许，256 字节应拒绝。 */
    @Test
    public void enforcesSegmentUtf8ByteLimit() {
        String valid = "a".repeat(255);
        assertEquals(255, valid.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(valid, ObjectKey.of(valid).value());
        assertRejected("a".repeat(256));
    }

    /** 完整 key 1024 字节应允许，超过应拒绝。 */
    @Test
    public void enforcesFullUtf8ByteLimit() {
        String valid = "a".repeat(255) + "/" + "b".repeat(255) + "/"
                + "c".repeat(255) + "/" + "d".repeat(252) + "/中";
        assertEquals(1024, valid.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(valid, ObjectKey.of(valid).value());
        assertRejected("a".repeat(255) + "/" + "b".repeat(255) + "/"
                + "c".repeat(255) + "/" + "d".repeat(252) + "/中国");
    }

    private static void assertRejected(String value) {
        try {
            ObjectKey.of(value);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected key rejection: " + value);
    }
}
