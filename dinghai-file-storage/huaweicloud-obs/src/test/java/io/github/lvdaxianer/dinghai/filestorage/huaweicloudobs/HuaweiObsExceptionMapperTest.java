package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

import com.obs.services.exception.ObsException;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageErrorCode;
import io.github.lvdaxianer.dinghai.filestorage.core.FileStorageException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** 华为云 OBS 异常映射测试。 */
public class HuaweiObsExceptionMapperTest {

    /** OBS 404 对象错误应被识别为缺失对象。 */
    @Test
    public void shouldRecognizeMissingObject() {
        ObsException exception = exception(404, "NoSuchKey");

        assertEquals(FileStorageErrorCode.OBJECT_NOT_FOUND,
                HuaweiObsExceptionMapper.errorCode(exception));
        assertEquals(FileStorageErrorCode.OBJECT_NOT_FOUND,
                HuaweiObsExceptionMapper.map(exception).errorCode());
    }

    /** OBS 鉴权错误应被归类为认证失败且不泄露原始内容。 */
    @Test
    public void shouldMapAuthenticationFailureSafely() {
        ObsException exception = exception(403, "AccessDenied");

        FileStorageException mapped = HuaweiObsExceptionMapper.map(exception);

        assertEquals(FileStorageErrorCode.AUTHENTICATION_FAILED, mapped.errorCode());
        assertFalse(mapped.getMessage().contains("AccessDenied"));
    }

    /** 只有明确的缺失上传错误才允许 abort 幂等忽略。 */
    @Test
    public void shouldRecognizeMissingMultipartUpload() {
        assertEquals(true, HuaweiObsExceptionMapper.isMissingMultipart(
                exception(404, "NoSuchUpload")));
        assertEquals(false, HuaweiObsExceptionMapper.isMissingMultipart(
                exception(500, "InternalError")));
    }

    /** 创建带 HTTP 状态和 OBS 错误码的测试异常。 */
    private static ObsException exception(int status, String code) {
        ObsException exception = new ObsException("provider detail");
        exception.setResponseCode(status);
        exception.setErrorCode(code);
        return exception;
    }
}
