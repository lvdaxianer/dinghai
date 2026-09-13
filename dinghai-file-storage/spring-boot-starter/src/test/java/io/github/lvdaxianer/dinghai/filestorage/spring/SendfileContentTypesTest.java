package io.github.lvdaxianer.dinghai.filestorage.spring;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 验证原始响应 MIME 类型的 sendfile 资格判定。 */
public class SendfileContentTypesTest {

    @Test
    public void shouldRecognizeStandardBinaryTypesAndIgnoreParameters() {
        assertTrue(SendfileContentTypes.isEligible("application/octet-stream"));
        assertTrue(SendfileContentTypes.isEligible("Application/PDF; charset=binary"));
        assertTrue(SendfileContentTypes.isEligible("application/zip"));
        assertTrue(SendfileContentTypes.isEligible("font/woff2"));
    }

    @Test
    public void shouldRecognizeBinaryTopLevelTypesExceptSvg() {
        assertTrue(SendfileContentTypes.isEligible("image/png"));
        assertTrue(SendfileContentTypes.isEligible("video/mp4"));
        assertTrue(SendfileContentTypes.isEligible("audio/mpeg"));
        assertTrue(SendfileContentTypes.isEligible("font/custom-font"));
        assertFalse(SendfileContentTypes.isEligible("image/svg+xml"));
    }

    @Test
    public void shouldRejectTextUnknownAndMalformedRawTypes() {
        assertFalse(SendfileContentTypes.isEligible("text/plain"));
        assertFalse(SendfileContentTypes.isEligible("application/json"));
        assertFalse(SendfileContentTypes.isEligible("application/wasm"));
        assertFalse(SendfileContentTypes.isEligible(null));
        assertFalse(SendfileContentTypes.isEligible(""));
        assertFalse(SendfileContentTypes.isEligible("image/png/extra"));
        assertFalse(SendfileContentTypes.isEligible("image/ png"));
        assertFalse(SendfileContentTypes.isEligible("image"));
        assertFalse(SendfileContentTypes.isEligible("image/png extra"));
    }
}
