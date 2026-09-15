package io.github.lvdaxianer.dinghai.filestorage.spring;

import io.github.lvdaxianer.dinghai.filestorage.core.MultipartCompletion;
import io.github.lvdaxianer.dinghai.filestorage.core.MultipartPart;
import java.util.List;

/**
 * 完成 multipart 会话的 HTTP 请求体。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-15
 */
public record MultipartCompleteRequest(List<MultipartPart> parts, String idempotencyKey) {

    /** 将 HTTP 请求转换为核心完成清单。 */
    MultipartCompletion toCompletion() {
        return new MultipartCompletion(parts);
    }
}
