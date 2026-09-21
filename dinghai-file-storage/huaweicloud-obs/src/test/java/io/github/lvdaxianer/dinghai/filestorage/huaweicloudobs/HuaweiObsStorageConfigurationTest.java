package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 华为云 OBS 连接配置校验测试。 */
public class HuaweiObsStorageConfigurationTest {

    /** 默认超时应被应用到合法的固定凭证配置。 */
    @Test
    public void shouldApplyDefaultTimeoutsToStaticCredentials() {
        HuaweiObsStorageConfiguration configuration =
                HuaweiObsStorageConfiguration.staticCredentials(
                        "https://obs.cn-north-4.myhuaweicloud.com", "cn-north-4",
                        "bucket", "ak", "sk");

        assertEquals(Duration.ofSeconds(10), configuration.connectTimeout());
        assertEquals("bucket", configuration.bucket());
        assertEquals(HuaweiObsCredentialMode.STATIC, configuration.credentialMode());
    }

    /** 缺失 Security Token 的 STS 配置必须在创建时失败。 */
    @Test
    public void shouldRejectIncompleteTemporaryCredentials() {
        assertThrows(IllegalArgumentException.class, () ->
                HuaweiObsStorageConfiguration.stsCredentials(
                        "https://obs.cn-north-4.myhuaweicloud.com", "cn-north-4",
                        "bucket", "ak", "sk", " "));
    }

    /** 非正超时必须被拒绝，避免远端调用无界阻塞。 */
    @Test
    public void shouldRejectNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new HuaweiObsStorageConfiguration(
                        "https://obs.cn-north-4.myhuaweicloud.com", "cn-north-4",
                        "bucket", HuaweiObsCredentialMode.STATIC, "ak", "sk", null,
                        Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }
}
