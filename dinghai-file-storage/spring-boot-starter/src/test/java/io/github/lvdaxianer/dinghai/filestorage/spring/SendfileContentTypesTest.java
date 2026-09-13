package io.github.lvdaxianer.dinghai.filestorage.spring;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 验证 sendfile 不可压缩内容类型分类规则。 */
public class SendfileContentTypesTest {

    /** 内置二进制清单与通配类型应判定为不可压缩。 */
    @Test
    public void shouldRecognizeIncompressibleBinaryTypes() {
        assertTrue(SendfileContentTypes.isIncompressible("application/octet-stream"));
        assertTrue(SendfileContentTypes.isIncompressible("application/pdf"));
        assertTrue(SendfileContentTypes.isIncompressible("application/zip"));
        assertTrue(SendfileContentTypes.isIncompressible("application/wasm"));
        assertTrue(SendfileContentTypes.isIncompressible("image/png"));
        assertTrue(SendfileContentTypes.isIncompressible("image/jpeg"));
        assertTrue(SendfileContentTypes.isIncompressible("video/mp4"));
        assertTrue(SendfileContentTypes.isIncompressible("audio/mpeg"));
        assertTrue(SendfileContentTypes.isIncompressible("font/woff2"));
    }

    /** 可压缩文本类型应判定为需要回退。 */
    @Test
    public void shouldTreatTextTypesAsCompressible() {
        assertFalse(SendfileContentTypes.isIncompressible("text/plain"));
        assertFalse(SendfileContentTypes.isIncompressible("text/html"));
        assertFalse(SendfileContentTypes.isIncompressible("application/json"));
        assertFalse(SendfileContentTypes.isIncompressible("application/javascript"));
        assertFalse(SendfileContentTypes.isIncompressible("application/xml"));
        assertFalse(SendfileContentTypes.isIncompressible("application/xhtml+xml"));
        assertFalse(SendfileContentTypes.isIncompressible("image/svg+xml"));
    }

    /** 大小写与参数不应影响分类结果。 */
    @Test
    public void shouldIgnoreCaseAndParameters() {
        assertTrue(SendfileContentTypes.isIncompressible("IMAGE/PNG"));
        assertTrue(SendfileContentTypes.isIncompressible("application/pdf; name=a.pdf"));
        assertFalse(SendfileContentTypes.isIncompressible("Text/Plain; charset=UTF-8"));
        assertFalse(SendfileContentTypes.isIncompressible("image/SVG+XML"));
    }

    /** 空值、空白或无法解析的类型应保守判定为可压缩。 */
    @Test
    public void shouldTreatAbsentOrMalformedTypesAsCompressible() {
        assertFalse(SendfileContentTypes.isIncompressible(null));
        assertFalse(SendfileContentTypes.isIncompressible(""));
        assertFalse(SendfileContentTypes.isIncompressible("   "));
        assertFalse(SendfileContentTypes.isIncompressible("not-a-type"));
        assertFalse(SendfileContentTypes.isIncompressible("application/x-unlisted-type"));
        assertFalse(SendfileContentTypes.isIncompressible("/png"));
        assertFalse(SendfileContentTypes.isIncompressible("image/"));
    }
}
