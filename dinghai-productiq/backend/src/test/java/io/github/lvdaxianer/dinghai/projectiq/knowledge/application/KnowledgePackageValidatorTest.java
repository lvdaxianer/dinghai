package io.github.lvdaxianer.dinghai.projectiq.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证知识包协议的结构、来源完整性和原文证据约束。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
class KnowledgePackageValidatorTest {

    /** 被测知识包校验器。 */
    private KnowledgePackageValidator validator;

    /**
     * 初始化 JSON 解析器和被测对象。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @BeforeEach
    void setUp() {
        validator = new KnowledgePackageValidator(new ObjectMapper());
    }

    /**
     * 验证完整且证据逐字匹配的知识包可以通过。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldAcceptValidKnowledgePackage() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source);

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
        assertThat(result.knowledgePackage()).isPresent();
    }

    /**
     * 验证原文哈希不匹配时返回稳定错误码。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectSourceHashMismatch() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate("# 旧内容\n");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.SOURCE_HASH_MISMATCH);
    }

    /**
     * 验证缺失章节概念映射时拒绝候选包。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectSectionWithoutConcept() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source)
                .replace("\"conceptIds\":[\"member_invitation\",\"invitation_link_expired\"]",
                        "\"conceptIds\":[]");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.MISSING_SECTION);
    }

    /**
     * 验证关系证据 quote 不在章节正文中时拒绝候选包。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectNonVerbatimRelationEvidence() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source)
                .replace("\"quote\":\"邀请链接有效期为 72 小时。\"", "\"quote\":\"邀请链接可以重新发送。\"");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.INVALID_EVIDENCE_QUOTE);
    }

    /**
     * 验证概念类型不在协议白名单时拒绝候选包。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectUnsupportedConceptType() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source).replace("\"type\":\"feature\"", "\"type\":\"unknown\"");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.INVALID_CONCEPT_TYPE);
    }

    /**
     * 验证文档 ID 不符合稳定 snake_case 协议时被拒绝。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectInvalidIdentifier() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source)
                .replace("\"id\":\"team_member_management\"", "\"id\":\"team-member\"");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.issues()).anyMatch(issue ->
                issue.code() == KnowledgePackageValidator.IssueCode.INVALID_SCHEMA
                        && issue.candidatePath().equals("/document/id"));
    }

    /**
     * 验证关系类型不在白名单时返回稳定错误码。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectUnsupportedRelationType() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source).replace("\"type\":\"requires\"", "\"type\":\"blocks\"");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.INVALID_RELATION_TYPE);
    }

    /**
     * 验证概念没有当前文档证据时拒绝候选包。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectConceptWithoutEvidence() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source)
                .replace("\"evidenceSectionIds\":[\"team_member_management.invitation_link_expired\"]",
                        "\"evidenceSectionIds\":[]");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.MISSING_EVIDENCE);
    }

    /**
     * 验证章节正文必须落在 sourceAnchor 指向的原文中。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectSectionContentOutsideSourceAnchor() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source)
                .replace("邀请链接有效期为 72 小时。\",\"conceptIds\"",
                        "未在原文出现的正文。\",\"conceptIds\"");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.UNSUPPORTED_CONTENT);
    }

    /**
     * 验证未知 JSON 字段不能绕过协议校验。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectUnknownField() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source).replace(
                "\"schemaVersion\":\"1.0\",",
                "\"schemaVersion\":\"1.0\",\"unexpected\":\"value\",");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .containsExactly(KnowledgePackageValidator.IssueCode.INVALID_SCHEMA);
    }

    /**
     * 验证协议允许没有口语查询别名的概念。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldAllowEmptyQueryAliases() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source)
                .replace("\"queryAliases\":[\"加人\"]", "\"queryAliases\":[]")
                .replace("\"queryAliases\":[\"过期\"]", "\"queryAliases\":[]");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isTrue();
    }

    /**
     * 验证章节概念引用错误时返回标准 JSON Pointer 路径。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldReportJsonPointerForUnknownSectionConcept() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source)
                .replace("\"conceptIds\":[\"member_invitation\",\"invitation_link_expired\"]",
                        "\"conceptIds\":[\"unknown_concept\"]");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.issues())
                .anyMatch(issue -> issue.code() == KnowledgePackageValidator.IssueCode.INVALID_SCHEMA
                        && issue.candidatePath().equals("/sections/0/conceptIds/0"));
    }

    /**
     * 验证数组中的 null 元素被转换为稳定协议错误而非抛出空指针。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    @Test
    void shouldRejectNullSectionElementAsSchemaError() throws Exception {
        String source = "# 团队成员管理\n\n邀请链接有效期为 72 小时。\n";
        String candidate = validCandidate(source).replace("\"sections\":[{", "\"sections\":[null,{ ");

        KnowledgePackageValidator.ValidationResult result =
                validator.validate("team.md", source.getBytes(StandardCharsets.UTF_8), candidate);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(KnowledgePackageValidator.ValidationIssue::code)
                .contains(KnowledgePackageValidator.IssueCode.MISSING_SECTION);
    }

    /**
     * 构造一个最小可通过候选包，供多个行为测试复用。
     *
     * @param source Markdown 原文
     * @return 候选知识包 JSON
     * @throws Exception 计算摘要失败时抛出
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String validCandidate(String source) throws Exception {
        String hash = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8)));
        return """
                {
                  "schemaVersion":"1.0",
                  "source":{"key":"team.md","hash":"%s","title":"团队成员管理","language":"zh-CN"},
                  "document":{"id":"team_member_management","summary":"成员邀请规则"},
                  "sections":[{"id":"team_member_management.invitation_link_expired","headingPath":["团队成员管理"],"summary":"邀请链接有效期规则","content":"邀请链接有效期为 72 小时。","conceptIds":["member_invitation","invitation_link_expired"],"sourceAnchor":{"startLine":3,"endLine":3}}],
                  "concepts":[{"id":"member_invitation","name":"成员邀请","type":"feature","definition":"管理员邀请用户加入团队的产品能力。","canonicalAliases":["邀请链接"],"queryAliases":["加人"],"evidenceSectionIds":["team_member_management.invitation_link_expired"]},{"id":"invitation_link_expired","name":"邀请链接有效期","type":"state","definition":"邀请链接在指定时间后失效的状态。","canonicalAliases":["有效期"],"queryAliases":["过期"],"evidenceSectionIds":["team_member_management.invitation_link_expired"]}],
                  "relations":[{"operation":"add","from":"member_invitation","type":"requires","to":"invitation_link_expired","evidence":{"sectionId":"team_member_management.invitation_link_expired","quote":"邀请链接有效期为 72 小时。"}}]
                }
                """.formatted(hash);
    }
}
