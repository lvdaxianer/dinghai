package io.github.lvdaxianer.projectiq.knowledge.application;

import io.github.lvdaxianer.projectiq.knowledge.repository.DocumentRecord;
import io.github.lvdaxianer.projectiq.knowledge.repository.KnowledgeStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 从运维可信目录导入经过校验的 Markdown/知识包 JSON 文件对。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class KnowledgeImportService {
    private static final Pattern SOURCE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*\\.md$");
    private final Path trustedDirectory;
    private final KnowledgePackageValidator validator;
    private final KnowledgeStore store;

    /**
     * 创建可信目录导入服务。
     *
     * @param trustedDirectory 可信知识目录
     * @param validator 知识包校验器
     * @param store SQLite 事实仓储
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public KnowledgeImportService(Path trustedDirectory, KnowledgePackageValidator validator, KnowledgeStore store) {
        this.trustedDirectory = Objects.requireNonNull(trustedDirectory, "trustedDirectory 不能为空").toAbsolutePath().normalize();
        this.validator = Objects.requireNonNull(validator, "validator 不能为空");
        this.store = Objects.requireNonNull(store, "store 不能为空");
    }

    /**
     * 校验并原子替换一个可信来源，事实提交后才创建索引任务。
     *
     * @param sourceKey 单层 Markdown 文件名
     * @return 导入结果
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public synchronized ImportResult importSource(String sourceKey) {
        validateSourceKey(sourceKey);
        Path markdown = trustedDirectory.resolve(sourceKey).normalize();
        Path candidate = trustedDirectory.resolve(candidateName(sourceKey)).normalize();
        ensureWithinTrustedDirectory(markdown);
        ensureWithinTrustedDirectory(candidate);
        try {
            if (!Files.isRegularFile(markdown) || !Files.isRegularFile(candidate)) {
                throw new ImportException("MISSING_SOURCE_PAIR", "可信目录缺少 Markdown 或 knowledge.json 文件");
            }
            ensureTrustedRealPath(markdown);
            ensureTrustedRealPath(candidate);
            byte[] sourceBytes = Files.readAllBytes(markdown);
            String candidateJson = Files.readString(candidate, StandardCharsets.UTF_8);
            KnowledgePackageValidator.ValidationResult validation =
                    validator.validate(sourceKey, sourceBytes, candidateJson);
            if (!validation.valid()) {
                throw new ImportException(validation.issues().stream().findFirst()
                        .map(issue -> issue.code().name()).orElse("INVALID_KNOWLEDGE_PACKAGE"),
                        "知识包校验失败");
            }
            String sourceHash = validation.sourceHash();
            Optional<DocumentRecord> existing = store.findDocumentBySourceKey(sourceKey);
            if (existing.isPresent() && sourceHash.equals(existing.get().contentHash())) {
                return new ImportResult(Status.ALREADY_IMPORTED, sourceKey, sourceHash);
            }
            store.replaceSource(validation.knowledgePackage().orElseThrow(), Instant.now());
            store.enqueueVectorJob(sourceKey, "upsert");
            return new ImportResult(Status.IMPORTED, sourceKey, sourceHash);
        } catch (ImportException exception) {
            throw exception;
        } catch (IOException | java.sql.SQLException exception) {
            throw new ImportException("IMPORT_IO_ERROR", "知识包导入失败", exception);
        }
    }

    /**
     * 显式删除一个来源并创建向量删除任务，目录扫描不会隐式调用此方法。
     *
     * @param sourceKey 单层 Markdown 文件名
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public void deleteSource(String sourceKey) {
        validateSourceKey(sourceKey);
        try {
            store.deleteSource(sourceKey);
            store.enqueueVectorJob(sourceKey, "delete");
        } catch (java.sql.SQLException exception) {
            throw new ImportException("DELETE_FAILED", "知识源删除失败", exception);
        }
    }

    /**
     * 计算与 Markdown 同名的知识包 JSON 文件名。
     *
     * @param sourceKey Markdown 文件名
     * @return 知识包文件名
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    static String candidateName(String sourceKey) {
        return sourceKey.substring(0, sourceKey.length() - 3) + ".knowledge.json";
    }

    /**
     * 校验来源键只能表示可信目录下的单层 Markdown 文件。
     *
     * @param sourceKey 待校验来源键
     * @throws ImportException 来源键不合法
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    static void validateSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank() || sourceKey.startsWith("/")
                || sourceKey.contains("/") || sourceKey.contains("\\") || sourceKey.contains("..")
                || !SOURCE_KEY.matcher(sourceKey).matches()) {
            throw new ImportException("INVALID_SOURCE_KEY", "sourceKey 必须是可信目录下单层 .md 文件名");
        }
    }

    /**
     * 校验规范化路径仍位于可信目录内。
     *
     * @param path 待检查路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void ensureWithinTrustedDirectory(Path path) {
        if (!path.startsWith(trustedDirectory)) {
            throw new ImportException("INVALID_SOURCE_KEY", "sourceKey 超出可信目录");
        }
    }

    /**
     * 校验符号链接解析后的真实路径仍位于可信目录内。
     *
     * @param path 待检查路径
     * @throws ImportException 文件链接越界或不可读
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void ensureTrustedRealPath(Path path) {
        try {
            if (!path.toRealPath().startsWith(trustedDirectory.toRealPath())) {
                throw new ImportException("INVALID_SOURCE_KEY", "文件链接超出可信目录");
            }
        } catch (IOException exception) {
            throw new ImportException("MISSING_SOURCE_PAIR", "可信目录文件不可读取", exception);
        }
    }

    /** 导入结果状态。 */
    public enum Status { IMPORTED, ALREADY_IMPORTED }

    /** 导入结果记录。 */
    public record ImportResult(Status status, String sourceKey, String contentHash) { }

    /** 导入失败时返回给管理 API 的稳定异常。 */
    public static final class ImportException extends RuntimeException {
        private final String code;

        /**
         * 创建不带底层异常的导入错误。
         *
         * @param code 稳定错误码
         * @param message 对外安全消息
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        public ImportException(String code, String message) { super(message); this.code = code; }

        /**
         * 创建带底层异常链的导入错误。
         *
         * @param code 稳定错误码
         * @param message 对外安全消息
         * @param cause 底层异常
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        public ImportException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }

        /**
         * 返回稳定错误码。
         *
         * @return 稳定错误码
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        public String code() { return code; }
    }
}
