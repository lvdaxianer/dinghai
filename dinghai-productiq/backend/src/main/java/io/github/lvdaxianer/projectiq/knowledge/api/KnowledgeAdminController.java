package io.github.lvdaxianer.projectiq.knowledge.api;

import io.github.lvdaxianer.projectiq.knowledge.application.KnowledgeImportService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向内部运维的可信知识导入和显式来源删除 API。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
@RestController
@RequestMapping("/api/admin")
public final class KnowledgeAdminController {
    private final KnowledgeImportService importer;
    private final String adminKey;

    /**
     * 创建管理 API 控制器。
     *
     * @param importer 知识导入服务
     * @param adminKey 管理请求密钥
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public KnowledgeAdminController(KnowledgeImportService importer,
                                    @Value("${productiq.admin-key:}") String adminKey) {
        this.importer = importer;
        this.adminKey = adminKey == null ? "" : adminKey;
    }

    /**
     * 导入一对可信 Markdown 和知识包文件。
     *
     * @param key 管理请求密钥
     * @param request 导入请求
     * @return 导入结果或稳定错误响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @PostMapping("/knowledge-imports")
    public ResponseEntity<?> importSource(@RequestHeader(value = "X-ProductIQ-Admin-Key", required = false) String key,
                                          @RequestBody ImportRequest request) {
        if (!authorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "UNAUTHORIZED"));
        }
        if (request == null || request.sourceKey() == null) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_SOURCE_KEY"));
        }
        try {
            KnowledgeImportService.ImportResult result = importer.importSource(request.sourceKey());
            return ResponseEntity.ok(result);
        } catch (KnowledgeImportService.ImportException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", exception.code(), "message", exception.getMessage()));
        }
    }

    /**
     * 显式删除指定知识来源。
     *
     * @param key 管理请求密钥
     * @param sourceKey 来源文件名
     * @return 空响应或稳定错误响应
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @DeleteMapping("/knowledge-sources/{sourceKey:.+}")
    public ResponseEntity<?> deleteSource(@RequestHeader(value = "X-ProductIQ-Admin-Key", required = false) String key,
                                          @PathVariable String sourceKey) {
        if (!authorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "UNAUTHORIZED"));
        }
        try {
            importer.deleteSource(sourceKey);
            return ResponseEntity.noContent().build();
        } catch (KnowledgeImportService.ImportException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", exception.code(), "message", exception.getMessage()));
        }
    }

    /**
     * 使用常量时间比较校验管理密钥。
     *
     * @param supplied 请求携带的密钥
     * @return 密钥是否匹配
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private boolean authorized(String supplied) {
        if (adminKey.isBlank() || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    /** 管理 API 的导入请求体。 */
    public record ImportRequest(String sourceKey) { }
}
