package io.github.lvdaxianer.dinghai.filestorage.huaweicloudobs;

/** 华为云 OBS 凭证来源模式。 */
public enum HuaweiObsCredentialMode {
    /** 使用长期 AK/SK。 */
    STATIC,
    /** 使用 AK/SK/Security Token 临时凭证。 */
    STS,
    /** 使用应用提供的可刷新凭证 Provider。 */
    PROVIDER
}
