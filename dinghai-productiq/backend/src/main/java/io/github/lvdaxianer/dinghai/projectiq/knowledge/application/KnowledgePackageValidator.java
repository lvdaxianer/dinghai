package io.github.lvdaxianer.dinghai.projectiq.knowledge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lvdaxianer.dinghai.projectiq.knowledge.domain.KnowledgePackage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校验 Markdown 与候选知识包之间的协议、哈希和证据约束。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class KnowledgePackageValidator {

    /** 当前支持的知识包协议版本。 */
    private static final String SCHEMA_VERSION = "1.0";
    /** 当前支持的知识原文语言。 */
    private static final String SOURCE_LANGUAGE = "zh-CN";
    /** SHA-256 值的协议前缀。 */
    private static final String HASH_PREFIX = "sha256:";
    /** SHA-256 值的严格格式。 */
    private static final Pattern HASH_PATTERN = Pattern.compile("^sha256:[a-f0-9]{64}$");
    /** 关系键的字段分隔符。 */
    private static final String RELATION_KEY_SEPARATOR = "|";
    /** 校验问题 ID 的固定前缀。 */
    private static final String ISSUE_ID_PREFIX = "VAL-";
    /** 校验问题 ID 的数字格式。 */
    private static final String ISSUE_ID_FORMAT = "%03d";
    /** 集合索引的默认容量下限。 */
    private static final int INITIAL_COLLECTION_CAPACITY = 16;
    /** Map/Set 的默认负载因子。 */
    private static final float COLLECTION_LOAD_FACTOR = 0.75f;
    /** 空文档的行数。 */
    private static final int EMPTY_LINE_COUNT = 0;

    /** 协议允许的稳定 ID 格式。 */
    private static final Pattern ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");

    /** 协议允许的源文件名格式。 */
    private static final Pattern SOURCE_KEY_PATTERN =
            Pattern.compile("^[^/\\\\]+\\.md$");

    /** 协议允许的概念类型。 */
    private static final Set<String> CONCEPT_TYPES = Set.of(
            "feature", "action", "entity", "state", "rule", "exception", "role");

    /** 协议允许的本体关系类型。 */
    private static final Set<String> RELATION_TYPES = Set.of(
            "parent_of", "has_part", "creates", "requires", "governed_by",
            "exception_of", "overrides", "related_to");

    /** 协议允许的关系操作。 */
    private static final Set<String> RELATION_OPERATIONS = Set.of("add", "retain", "retire");

    /** JSON 解析器。 */
    private final ObjectMapper objectMapper;

    /**
     * 使用指定 JSON 解析器创建校验器。
     *
     * @param objectMapper JSON 解析器，不得为 null
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public KnowledgePackageValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 校验一份候选知识包并返回可复用的解析结果。
     *
     * @param sourceKey 请求导入的 Markdown 文件名
     * @param sourceBytes Markdown 原始 UTF-8 字节
     * @param candidateJson 候选知识包 JSON
     * @return 校验结果、稳定错误列表和通过时的知识包
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public ValidationResult validate(String sourceKey, byte[] sourceBytes, String candidateJson) {
        byte[] safeBytes = sourceBytes == null ? new byte[0] : sourceBytes;
        ValidationContext context = new ValidationContext(sourceKey, safeBytes, candidateJson);
        Optional<KnowledgePackage> parsed = parseCandidate(context);
        // JSON 解析失败时直接返回稳定协议问题，不进入交叉校验。
        if (parsed.isEmpty()) {
            return context.result();
        }
        context.knowledgePackage = parsed.get();
        validatePackage(context);
        return context.result();
    }

    /**
     * 将候选 JSON 解析为严格字段集合的领域记录。
     *
     * @param context 当前校验上下文
     * @return 解析成功的知识包，失败时为空
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private Optional<KnowledgePackage> parseCandidate(ValidationContext context) {
        // 空候选不能进入 Jackson，避免把缺失输入误判成有效空包。
        if (isBlank(context.candidateJson)) {
            context.add(IssueCode.INVALID_SCHEMA, "/", "候选知识包为空。",
                    "提供完整的知识包 JSON。");
            return Optional.empty();
        }
        try {
            KnowledgePackage parsed = strictMapper().readValue(context.candidateJson, KnowledgePackage.class);
            return parsedPackage(context, parsed);
        } catch (JsonProcessingException exception) {
            context.add(IssueCode.INVALID_SCHEMA, "/", "候选知识包不是有效的协议 JSON。",
                    "修复 JSON 语法、字段类型或未知字段。");
            return Optional.empty();
        }
    }

    /**
     * 创建拒绝未知字段的 JSON 解析器副本。
     *
     * @return 严格字段解析器
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private ObjectMapper strictMapper() {
        return objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    /**
     * 将解析值转换为非空候选包。
     *
     * @param context 当前校验上下文
     * @param parsed Jackson 解析结果
     * @return 非空候选包，解析值为空时返回空 Optional
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private Optional<KnowledgePackage> parsedPackage(
            ValidationContext context, KnowledgePackage parsed) {
        // JSON null 没有可校验的领域结构，返回结构错误。
        if (parsed == null) {
            context.add(IssueCode.INVALID_SCHEMA, "/", "候选知识包不能是 JSON null。",
                    "提供完整的知识包对象。");
            return Optional.empty();
        }
        return Optional.of(parsed);
    }

    /**
     * 执行来源、文档、章节、概念和关系的完整交叉校验。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validatePackage(ValidationContext context) {
        validateSource(context);
        validateDocument(context);
        indexSections(context);
        indexConcepts(context);
        validateSectionConcepts(context);
        validateConceptEvidence(context);
        validateRelations(context);
    }

    /**
     * 校验协议版本、文件名、语言和原文哈希。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateSource(ValidationContext context) {
        checkEqual(context, context.knowledgePackage.schemaVersion(), SCHEMA_VERSION, "/schemaVersion",
                IssueCode.INVALID_SCHEMA, "只支持 schemaVersion 1.0。");
        KnowledgePackage.Source source = context.knowledgePackage.source();
        // 来源元数据缺失时停止该分支，避免继续访问空对象。
        if (source == null) {
            context.add(IssueCode.INVALID_SCHEMA, "/source", "缺少 source 元数据。",
                    "补充 key、hash、title 和 language。");
            return;
        }
        checkEqual(context, source.key(), context.sourceKey, "/source/key",
                IssueCode.SOURCE_HASH_MISMATCH, "source.key 必须与请求文件名一致。");
        checkSourceKey(context, source.key());
        checkSourceHashFormat(context, source.hash());
        checkEqual(context, source.hash(), context.sourceHash(), "/source/hash",
                IssueCode.SOURCE_HASH_MISMATCH, "source.hash 必须等于当前 Markdown SHA-256。");
        checkNonBlank(context, source.title(), "/source/title",
                IssueCode.INVALID_SCHEMA, "source.title 不能为空。");
        checkEqual(context, source.language(), SOURCE_LANGUAGE, "/source/language",
                IssueCode.UNSUPPORTED_CONTENT, "第一期只接受 zh-CN 文档。");
    }

    /**
     * 校验文档元数据和稳定文档 ID。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateDocument(ValidationContext context) {
        KnowledgePackage.Document document = context.knowledgePackage.document();
        // 文档元数据缺失时停止该分支，避免设置无效文档上下文。
        if (document == null) {
            context.add(IssueCode.INVALID_SCHEMA, "/document", "缺少 document 元数据。",
                    "补充稳定 document.id 和 summary。");
            return;
        }
        checkId(context, document.id(), "/document/id");
        checkNonBlank(context, document.summary(), "/document/summary",
                IssueCode.INVALID_SCHEMA, "document.summary 不能为空。");
        context.documentId = document.id();
    }

    /**
     * 建立章节索引并检查章节结构、锚点和必需字段。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void indexSections(ValidationContext context) {
        List<KnowledgePackage.Section> sections = context.knowledgePackage.sections();
        // 没有章节不能形成可检索知识，返回缺失章节问题。
        if (sections.isEmpty()) {
            context.add(IssueCode.MISSING_SECTION, "/sections", "知识包至少需要一个章节。",
                    "按 Markdown 标题生成至少一个完整章节。");
            return;
        }
        context.sectionsById = new LinkedHashMap<>(capacityFor(sections.size()));
        context.sectionIndexesById = new LinkedHashMap<>(capacityFor(sections.size()));
        for (int index = 0; index < sections.size(); index++) {
            KnowledgePackage.Section section = sections.get(index);
            validateSection(context, section, index);
        }
    }

    /**
     * 校验单个章节并放入章节索引。
     *
     * @param context 当前校验上下文
     * @param section 待校验章节
     * @param index 章节数组下标
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateSection(ValidationContext context, KnowledgePackage.Section section, int index) {
        String path = "/sections/" + index;
        // 数组中的空元素按章节缺失处理，不访问其字段。
        if (section == null) {
            context.add(IssueCode.MISSING_SECTION, path, "章节对象不能为空。",
                    "补充完整章节对象。");
            return;
        }
        validateSectionFields(context, section, path);
        registerSection(context, section, index);
    }

    /**
     * 校验章节的字段、标题路径、概念映射和原文锚点。
     *
     * @param context 当前校验上下文
     * @param section 待校验章节
     * @param path 章节 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateSectionFields(
            ValidationContext context, KnowledgePackage.Section section, String path) {
        checkId(context, section.id(), path + "/id");
        checkSectionPrefix(context, section.id(), path + "/id");
        checkNonBlank(context, section.summary(), path + "/summary",
                IssueCode.INVALID_SCHEMA, "章节摘要不能为空。");
        checkNonBlank(context, section.content(), path + "/content",
                IssueCode.INVALID_SCHEMA, "章节正文不能为空。");
        checkStringList(context, section.headingPath(), path + "/headingPath", true);
        checkStringList(context, section.conceptIds(), path + "/conceptIds", true);
        checkAnchor(context, section.sourceAnchor(), path + "/sourceAnchor");
        checkHeadingPath(context, section.headingPath(), path + "/headingPath");
        checkSectionContent(context, section, path);
    }

    /**
     * 将通过字段解析的章节放入索引并报告重复 ID。
     *
     * @param context 当前校验上下文
     * @param section 待注册章节
     * @param index 章节数组下标
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void registerSection(
            ValidationContext context, KnowledgePackage.Section section, int index) {
        // 空 ID 已由字段校验报告，跳过索引避免制造空键。
        if (isBlank(section.id())) {
            return;
        }
        KnowledgePackage.Section previous = context.sectionsById.putIfAbsent(section.id(), section);
        context.sectionIndexesById.putIfAbsent(section.id(), index);
        // 已有相同 ID 时保留首个定义并报告重复章节。
        if (previous != null) {
            context.add(IssueCode.MISSING_SECTION, "/sections/" + index + "/id", "章节 ID 重复。",
                    "为每个语义章节分配唯一 section.id。");
        }
    }

    /**
     * 建立概念索引并校验概念类型、别名和结构字段。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void indexConcepts(ValidationContext context) {
        List<KnowledgePackage.Concept> concepts = context.knowledgePackage.concepts();
        context.conceptsById = new LinkedHashMap<>(capacityFor(concepts.size()));
        context.conceptIndexesById = new LinkedHashMap<>(capacityFor(concepts.size()));
        for (int index = 0; index < concepts.size(); index++) {
            KnowledgePackage.Concept concept = concepts.get(index);
            validateConcept(context, concept, index);
        }
    }

    /**
     * 校验单个概念并放入概念索引。
     *
     * @param context 当前校验上下文
     * @param concept 待校验概念
     * @param index 概念数组下标
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateConcept(ValidationContext context, KnowledgePackage.Concept concept, int index) {
        String path = "/concepts/" + index;
        // 数组中的空元素按结构错误处理，不访问其字段。
        if (concept == null) {
            context.add(IssueCode.INVALID_SCHEMA, path, "概念对象不能为空。",
                    "补充完整概念对象。");
            return;
        }
        validateConceptFields(context, concept, path);
        registerConcept(context, concept, index);
    }

    /**
     * 校验概念字段、类型、别名和证据章节列表。
     *
     * @param context 当前校验上下文
     * @param concept 待校验概念
     * @param path 概念 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateConceptFields(
            ValidationContext context, KnowledgePackage.Concept concept, String path) {
        checkId(context, concept.id(), path + "/id");
        checkNonBlank(context, concept.name(), path + "/name",
                IssueCode.INVALID_SCHEMA, "概念名称不能为空。");
        checkNonBlank(context, concept.definition(), path + "/definition",
                IssueCode.INVALID_SCHEMA, "概念定义不能为空。");
        checkAllowed(context, concept.type(), CONCEPT_TYPES, path + "/type",
                IssueCode.INVALID_CONCEPT_TYPE, "概念类型不在协议白名单中。");
        checkAliases(context, concept.canonicalAliases(), context.sourceText, path + "/canonicalAliases");
        checkStringList(context, concept.queryAliases(), path + "/queryAliases", false);
        checkStringList(context, concept.evidenceSectionIds(), path + "/evidenceSectionIds", true);
    }

    /**
     * 将通过字段解析的概念放入索引并报告重复 ID。
     *
     * @param context 当前校验上下文
     * @param concept 待注册概念
     * @param index 概念数组下标
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void registerConcept(
            ValidationContext context, KnowledgePackage.Concept concept, int index) {
        // 空 ID 已由字段校验报告，跳过索引避免制造空键。
        if (isBlank(concept.id())) {
            return;
        }
        KnowledgePackage.Concept previous = context.conceptsById.putIfAbsent(concept.id(), concept);
        context.conceptIndexesById.putIfAbsent(concept.id(), index);
        // 已有相同 ID 时保留首个定义并报告重复概念。
        if (previous != null) {
            context.add(IssueCode.DUPLICATE_CONCEPT, "/concepts/" + index + "/id", "概念 ID 重复。",
                    "复用已有 concept.id，不要创建重复概念。");
        }
    }

    /**
     * 检查章节引用的概念都在当前知识包中声明。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateSectionConcepts(ValidationContext context) {
        for (Map.Entry<String, KnowledgePackage.Section> entry : context.sectionsById.entrySet()) {
            List<String> conceptIds = entry.getValue().conceptIds();
            for (int index = 0; index < conceptIds.size(); index++) {
                String conceptId = conceptIds.get(index);
                // 章节引用未声明概念时阻断该引用，但继续收集其他问题。
                if (!context.conceptsById.containsKey(conceptId)) {
                    String sectionPath = sectionPath(context, entry.getKey());
                    context.add(IssueCode.INVALID_SCHEMA, sectionPath + "/conceptIds/" + index,
                            "章节引用了未声明的概念 ID。", "补充概念声明或修正 conceptId。");
                }
            }
        }
    }

    /**
     * 检查概念的证据章节存在且确实绑定了该概念。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateConceptEvidence(ValidationContext context) {
        for (Map.Entry<String, KnowledgePackage.Concept> entry : context.conceptsById.entrySet()) {
            List<String> evidenceIds = entry.getValue().evidenceSectionIds();
            // 没有证据章节的概念不能进入可信知识图谱。
            if (evidenceIds.isEmpty()) {
                context.add(IssueCode.MISSING_EVIDENCE, conceptPath(context, entry.getKey()) + "/evidenceSectionIds",
                        "概念至少需要一个当前文档证据章节。", "绑定能够证明概念的章节。");
                continue;
            }
            for (String sectionId : evidenceIds) {
                KnowledgePackage.Section section = context.sectionsById.get(sectionId);
                // 证据章节缺失或未反向绑定概念时返回缺证据问题。
                if (section == null || !section.conceptIds().contains(entry.getKey())) {
                    String evidencePath = conceptPath(context, entry.getKey()) + "/evidenceSectionIds";
                    context.add(IssueCode.MISSING_EVIDENCE, evidencePath,
                            "概念证据章节不存在或未绑定该概念。",
                            "修正 evidenceSectionIds 和 section.conceptIds。");
                }
            }
        }
    }

    /**
     * 校验所有关系的操作、端点、方向、证据和逐字 quote。
     *
     * @param context 当前校验上下文
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateRelations(ValidationContext context) {
        context.relationKeys = new HashSet<>(capacityFor(context.knowledgePackage.relations().size()));
        for (int index = 0; index < context.knowledgePackage.relations().size(); index++) {
            KnowledgePackage.Relation relation = context.knowledgePackage.relations().get(index);
            validateRelation(context, relation, index);
        }
    }

    /**
     * 校验一条关系记录。
     *
     * @param context 当前校验上下文
     * @param relation 待校验关系
     * @param index 关系数组下标
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void validateRelation(
            ValidationContext context,
            KnowledgePackage.Relation relation,
            int index) {
        String path = "/relations/" + index;
        // 数组中的空元素按结构错误处理，不访问其字段。
        if (relation == null) {
            context.add(IssueCode.INVALID_SCHEMA, path, "关系对象不能为空。",
                    "补充完整关系对象。");
            return;
        }
        checkAllowed(context, relation.operation(), RELATION_OPERATIONS, path + "/operation",
                IssueCode.INVALID_RELATION_TYPE, "关系 operation 不在协议白名单中。");
        checkAllowed(context, relation.type(), RELATION_TYPES, path + "/type",
                IssueCode.INVALID_RELATION_TYPE, "关系 type 不在协议白名单中。");
        checkRelationEndpoints(context, relation, path);
        checkRelationEvidence(context, relation.evidence(), path);
    }

    /**
     * 校验关系两端存在且方向不形成自环或重复边。
     *
     * @param context 当前校验上下文
     * @param relation 待校验关系
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkRelationEndpoints(
            ValidationContext context,
            KnowledgePackage.Relation relation,
            String path) {
        boolean validFrom = context.conceptsById.containsKey(relation.from());
        boolean validTo = context.conceptsById.containsKey(relation.to());
        // 任一端点未声明时不能继续判断方向或重复边。
        if (!validFrom || !validTo) {
            addInvalidRelationEndpoint(context, path);
            return;
        }
        checkSelfRelation(context, relation, path);
        checkDuplicateRelation(context, relation, path);
    }

    /**
     * 报告关系端点未声明的错误。
     *
     * @param context 当前校验上下文
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void addInvalidRelationEndpoint(ValidationContext context, String path) {
        context.add(IssueCode.INVALID_RELATION_DIRECTION, path,
                "关系两端必须引用当前知识包已声明的概念。", "修正 from 和 to 概念 ID。");
    }

    /**
     * 拒绝关系自环。
     *
     * @param context 当前校验上下文
     * @param relation 待校验关系
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkSelfRelation(
            ValidationContext context, KnowledgePackage.Relation relation, String path) {
        // 起点和终点相同会形成本体自环，拒绝该关系。
        if (Objects.equals(relation.from(), relation.to())) {
            context.add(IssueCode.INVALID_RELATION_DIRECTION, path,
                    "关系不能指向自身。", "选择不同的起点和终点概念。");
        }
    }

    /**
     * 拒绝同一方向的重复关系。
     *
     * @param context 当前校验上下文
     * @param relation 待校验关系
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkDuplicateRelation(
            ValidationContext context,
            KnowledgePackage.Relation relation,
            String path) {
        String relationKey = String.join(RELATION_KEY_SEPARATOR,
                String.valueOf(relation.from()), String.valueOf(relation.type()),
                String.valueOf(relation.to()));
        // 已出现同方向关系时只保留一条边，避免图谱重复。
        if (!context.relationKeys.add(relationKey)) {
            context.add(IssueCode.INVALID_RELATION_DIRECTION, path,
                    "同一方向关系只能声明一次。", "合并重复关系并保留一条证据。");
        }
    }

    /**
     * 校验关系证据章节和逐字引用。
     *
     * @param context 当前校验上下文
     * @param evidence 关系证据
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkRelationEvidence(
            ValidationContext context,
            KnowledgePackage.Relation.Evidence evidence,
            String path) {
        // 关系缺少证据对象时无法进入可信图谱。
        if (evidence == null) {
            addMissingRelationEvidence(context, path);
            return;
        }
        KnowledgePackage.Section section = context.sectionsById.get(evidence.sectionId());
        // 证据章节不属于当前包时不能验证 quote。
        if (section == null) {
            addMissingEvidenceSection(context, path);
            return;
        }
        checkEvidenceQuote(context, evidence, section, path);
    }

    /**
     * 报告缺少关系证据对象。
     *
     * @param context 当前校验上下文
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void addMissingRelationEvidence(ValidationContext context, String path) {
        context.add(IssueCode.MISSING_EVIDENCE, path + "/evidence",
                "关系必须包含原文证据。", "补充 sectionId 和逐字 quote。");
    }

    /**
     * 报告关系证据章节不存在。
     *
     * @param context 当前校验上下文
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void addMissingEvidenceSection(ValidationContext context, String path) {
        context.add(IssueCode.MISSING_EVIDENCE, path + "/evidence/sectionId",
                "关系证据章节不存在。", "引用当前知识包中的 section.id。");
    }

    /**
     * 检查关系 quote 是否逐字出现在章节正文中。
     *
     * @param context 当前校验上下文
     * @param evidence 关系证据
     * @param section 证据章节
     * @param path 关系 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkEvidenceQuote(
            ValidationContext context,
            KnowledgePackage.Relation.Evidence evidence,
            KnowledgePackage.Section section,
            String path) {
        // quote 为空或不在章节正文中时，证据不具备逐字可追溯性。
        if (isBlank(evidence.quote()) || !section.content().contains(evidence.quote())) {
            context.add(IssueCode.INVALID_EVIDENCE_QUOTE, path + "/evidence/quote",
                    "关系 quote 未在指定章节正文中逐字出现。",
                    "改为章节正文中的原文句子。");
        }
    }

    /**
     * 校验 Markdown 文件名格式。
     *
     * @param context 当前校验上下文
     * @param sourceKey 候选 source.key
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkSourceKey(ValidationContext context, String sourceKey) {
        // 空文件名不是合法来源键，返回结构错误。
        if (isBlank(sourceKey)) {
            context.add(IssueCode.INVALID_SCHEMA, "/source/key", "source.key 不能为空。",
                    "提供当前 Markdown 文件名。");
        // 非空来源键仍需满足单层 Markdown 文件名规则。
        } else if (!SOURCE_KEY_PATTERN.matcher(sourceKey).matches()) {
            context.add(IssueCode.INVALID_SCHEMA, "/source/key", "source.key 必须是单层 Markdown 文件名。",
                    "移除路径分隔符、父目录片段和非 .md 后缀。");
        }
    }

    /**
     * 校验来源摘要满足 SHA-256 字符串格式。
     *
     * @param context 当前校验上下文
     * @param sourceHash 来源摘要
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkSourceHashFormat(ValidationContext context, String sourceHash) {
        // 摘要格式错误时先返回结构问题，再由相等性检查报告内容不一致。
        if (isBlank(sourceHash) || !HASH_PATTERN.matcher(sourceHash).matches()) {
            context.add(IssueCode.INVALID_SCHEMA, "/source/hash", "source.hash 格式无效。",
                    "使用 sha256: 加 64 位小写十六进制摘要。");
        }
    }

    /**
     * 校验 ID 是否符合英文 snake_case 协议。
     *
     * @param context 当前校验上下文
     * @param value 待校验 ID
     * @param path JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkId(ValidationContext context, String value, String path) {
        // 空值或非法字符都会破坏跨文档稳定引用。
        if (isBlank(value) || !ID_PATTERN.matcher(value).matches()) {
            context.add(IssueCode.INVALID_SCHEMA, path, "ID 必须使用英文 snake_case，可包含语义点号。",
                    "修正为小写字母、数字、下划线和可选点号组成的稳定 ID。");
        }
    }

    /**
     * 校验章节 ID 属于当前文档。
     *
     * @param context 当前校验上下文
     * @param sectionId 章节 ID
     * @param path JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkSectionPrefix(ValidationContext context, String sectionId, String path) {
        // 文档 ID 已知时，章节 ID 必须位于该文档命名空间。
        if (!isBlank(context.documentId) && !isBlank(sectionId)
                && !sectionId.startsWith(context.documentId + ".")) {
            context.add(IssueCode.INVALID_SCHEMA, path, "section.id 必须以 document.id 加点号开头。",
                    "按 <document.id>.<语义章节名> 生成稳定章节 ID。");
        }
    }

    /**
     * 校验章节原文行锚点位于当前 Markdown 范围内。
     *
     * @param context 当前校验上下文
     * @param anchor 行锚点
     * @param path JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkAnchor(ValidationContext context, KnowledgePackage.SourceAnchor anchor, String path) {
        // 锚点缺失、倒置或越过原文范围都不可用于证据定位。
        if (anchor == null || anchor.startLine() < 1 || anchor.endLine() < anchor.startLine()
                || anchor.endLine() > context.lineCount()) {
            context.add(IssueCode.MISSING_SECTION, path, "sourceAnchor 不是当前原文中的有效行范围。",
                    "修正 startLine/endLine，使其落在 Markdown 行数内。");
        }
    }

    /**
     * 检查章节正文确实出现在其行锚点覆盖的原文中。
     *
     * @param context 当前校验上下文
     * @param section 待校验章节
     * @param path 章节 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkSectionContent(
            ValidationContext context, KnowledgePackage.Section section, String path) {
        KnowledgePackage.SourceAnchor anchor = section.sourceAnchor();
        // 无效锚点已由专门校验报告，这里不再切分原文。
        if (anchor == null || isBlank(section.content())
                || anchor.startLine() < 1 || anchor.endLine() > context.lineCount()) {
            return;
        }
        String anchoredContent = String.join(
                "\n",
                context.sourceLines.subList(anchor.startLine() - 1, anchor.endLine()));
        // 锚点范围内未找到章节正文时，章节与原文不一致。
        if (!anchoredContent.contains(section.content())) {
            context.add(IssueCode.UNSUPPORTED_CONTENT,
                    path + "/content",
                    "章节正文未在 sourceAnchor 覆盖的原文中逐字出现。",
                    "保持章节 content 与 Markdown 原文及行锚点一致。");
        }
    }

    /**
     * 检查章节标题路径中的每级标题确实出现在当前 Markdown 原文中。
     *
     * @param context 当前校验上下文
     * @param headingPath 标题路径
     * @param path 标题路径的 JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkHeadingPath(
            ValidationContext context, List<String> headingPath, String path) {
        for (int index = 0; index < headingPath.size(); index++) {
            String heading = headingPath.get(index);
            // 空标题由列表校验负责；非空标题必须能在原文中定位。
            if (!isBlank(heading) && !context.sourceText.contains(heading)) {
                context.add(IssueCode.UNSUPPORTED_CONTENT, path + "/" + index,
                        "章节标题未在 Markdown 原文中出现。",
                        "修正 headingPath 或补充对应 Markdown 标题。");
            }
        }
    }

    /**
     * 校验字符串列表非空且不包含空字符串。
     *
     * @param context 当前校验上下文
     * @param values 字符串列表
     * @param path JSON 路径
     * @param required 是否要求至少一个元素
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkStringList(
            ValidationContext context, List<String> values, String path, boolean required) {
        // 只有协议要求的列表才禁止为空，queryAliases 可以没有口语别名。
        if (required && values.isEmpty()) {
            context.add(IssueCode.MISSING_SECTION, path, "列表至少需要一个非空值。",
                    "补充标题、概念或证据 ID。");
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            // 列表项必须是有意义的非空文本。
            if (isBlank(values.get(index))) {
                context.add(IssueCode.INVALID_SCHEMA, path + "/" + index, "列表不能包含空字符串。",
                        "删除空值或补充有效字符串。");
            }
        }
    }

    /**
     * 返回章节的标准 JSON Pointer 路径。
     *
     * @param context 当前校验上下文
     * @param sectionId 章节 ID
     * @return 章节 JSON Pointer 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String sectionPath(ValidationContext context, String sectionId) {
        Integer index = context.sectionIndexesById.get(sectionId);
        return "/sections/" + (index == null ? "-" : index);
    }

    /**
     * 返回概念的标准 JSON Pointer 路径。
     *
     * @param context 当前校验上下文
     * @param conceptId 概念 ID
     * @return 概念 JSON Pointer 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String conceptPath(ValidationContext context, String conceptId) {
        Integer index = context.conceptIndexesById.get(conceptId);
        return "/concepts/" + (index == null ? "-" : index);
    }

    /**
     * 校验标准别名在原文中出现。
     *
     * @param context 当前校验上下文
     * @param aliases 标准别名
     * @param sourceText Markdown 原文
     * @param path JSON 路径
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkAliases(
            ValidationContext context,
            List<String> aliases,
            String sourceText,
            String path) {
        for (int index = 0; index < aliases.size(); index++) {
            String alias = aliases.get(index);
            // 空别名先按结构错误处理。
            if (isBlank(alias)) {
                context.add(IssueCode.INVALID_SCHEMA, path + "/" + index, "canonicalAliases 不能包含空值。",
                        "补充原文中的标准术语。");
            // 非空标准别名必须能在当前原文中逐字找到。
            } else if (!sourceText.contains(alias)) {
                context.add(IssueCode.UNSUPPORTED_CONTENT, path + "/" + index,
                        "canonicalAlias 未在 Markdown 原文中出现。",
                        "只使用原文出现过的标准别名。");
            }
        }
    }

    /**
     * 校验字符串是否为非空文本。
     *
     * @param context 当前校验上下文
     * @param value 待校验文本
     * @param path JSON 路径
     * @param code 失败错误码
     * @param message 失败信息
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkNonBlank(
            ValidationContext context,
            String value,
            String path,
            IssueCode code,
            String message) {
        // 必填文本缺失时返回对应稳定错误码。
        if (isBlank(value)) {
            context.add(code, path, message, "补充非空文本。");
        }
    }

    /**
     * 校验值是否位于允许集合。
     *
     * @param context 当前校验上下文
     * @param value 待校验值
     * @param allowed 允许集合
     * @param path JSON 路径
     * @param code 失败错误码
     * @param message 失败信息
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkAllowed(
            ValidationContext context,
            String value,
            Set<String> allowed,
            String path,
            IssueCode code,
            String message) {
        // 不在协议白名单中的枚举值不能继续进入业务流程。
        if (!allowed.contains(value)) {
            context.add(code, path, message, "改为协议白名单中的值。");
        }
    }

    /**
     * 在不泄露原文内容的前提下检查两个元数据值相等。
     *
     * @param context 当前校验上下文
     * @param actual 实际值
     * @param expected 期望值
     * @param path JSON 路径
     * @param code 失败错误码
     * @param message 失败信息
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private void checkEqual(
            ValidationContext context,
            String actual,
            String expected,
            String path,
            IssueCode code,
            String message) {
        // 元数据不一致时返回可修复的稳定问题。
        if (!Objects.equals(expected, actual)) {
            context.add(code, path, message, "重新生成候选包并保持元数据一致。");
        }
    }

    /**
     * 计算集合所需的保守初始容量，减少扩容。
     *
     * @param size 元素数量
     * @return Map/Set 初始容量
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private int capacityFor(int size) {
        return Math.max(INITIAL_COLLECTION_CAPACITY,
                (int) (size / COLLECTION_LOAD_FACTOR) + 1);
    }

    /**
     * 判断文本是否为空或仅包含空白。
     *
     * @param value 待判断文本
     * @return 文本为空或全空白时返回 true
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 计算 UTF-8 内容的 SHA-256。
     *
     * @param content 待计算内容
     * @return sha256: 前缀的十六进制摘要
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算原始字节的 SHA-256，避免先解码导致摘要漂移。
     *
     * @param content 原始 UTF-8 字节
     * @return sha256: 前缀的十六进制摘要
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HASH_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256 算法。", exception);
        }
    }

    /**
     * 描述校验成功或失败的结果。
     *
     * @param valid 是否通过
     * @param issues 阻塞问题
     * @param knowledgePackage 通过时的解析包
     * @param sourceHash 当前原文哈希
     * @param candidateHash 候选 JSON 哈希
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record ValidationResult(
            boolean valid,
            List<ValidationIssue> issues,
            Optional<KnowledgePackage> knowledgePackage,
            String sourceHash,
            String candidateHash) {

        /**
         * 规范化结果集合，防止调用方修改内部状态。
         *
         * @param valid 是否通过
         * @param issues 阻塞问题
         * @param knowledgePackage 解析包
         * @param sourceHash 原文摘要
         * @param candidateHash 候选摘要
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        public ValidationResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
            knowledgePackage = knowledgePackage == null
                    ? Optional.empty() : knowledgePackage;
        }
    }

    /**
     * 描述一个可交回 Producer 修复的阻塞问题。
     *
     * @param issueId 稳定问题 ID
     * @param code 协议错误码
     * @param candidatePath 候选 JSON 路径
     * @param message 问题描述
     * @param requiredAction 修复动作
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record ValidationIssue(
            String issueId,
            IssueCode code,
            String candidatePath,
            String message,
            String requiredAction) {
    }

    /**
     * 第一版 Checker 协议错误码。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public enum IssueCode {
        INVALID_SCHEMA,
        SOURCE_HASH_MISMATCH,
        UNSUPPORTED_CONTENT,
        MISSING_SECTION,
        DUPLICATE_CONCEPT,
        INVALID_CONCEPT_TYPE,
        INVALID_RELATION_TYPE,
        INVALID_RELATION_DIRECTION,
        UNSUPPORTED_RELATION,
        MISSING_EVIDENCE,
        INVALID_EVIDENCE_QUOTE,
        UNDECLARED_RELATION_RETIREMENT
    }

    /**
     * 保存一次校验调用的临时索引和问题。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private final class ValidationContext {

        /** 请求源文件名。 */
        private final String sourceKey;
        /** Markdown 原文。 */
        private final String sourceText;
        /** 原始 Markdown 的 SHA-256。 */
        private final String sourceHash;
        /** 按原文换行拆分的行列表。 */
        private final List<String> sourceLines;
        /** 候选 JSON。 */
        private final String candidateJson;
        /** 阻塞问题。 */
        private final List<ValidationIssue> issues = new ArrayList<>(INITIAL_COLLECTION_CAPACITY);
        /** 章节索引。 */
        private Map<String, KnowledgePackage.Section> sectionsById = Map.of();
        /** 章节在候选数组中的索引。 */
        private Map<String, Integer> sectionIndexesById = Map.of();
        /** 概念索引。 */
        private Map<String, KnowledgePackage.Concept> conceptsById = Map.of();
        /** 概念在候选数组中的索引。 */
        private Map<String, Integer> conceptIndexesById = Map.of();
        /** 已出现的关系键。 */
        private Set<String> relationKeys = Set.of();
        /** 当前文档 ID。 */
        private String documentId = "";
        /** 当前解析包。 */
        private KnowledgePackage knowledgePackage;

        /**
         * 创建调用上下文。
         *
         * @param sourceKey 请求源文件名
         * @param sourceBytes Markdown 原始 UTF-8 字节
         * @param candidateJson 候选 JSON
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        private ValidationContext(String sourceKey, byte[] sourceBytes, String candidateJson) {
            this.sourceKey = sourceKey;
            byte[] safeBytes = sourceBytes.clone();
            this.sourceHash = sha256(safeBytes);
            String sourceText = new String(safeBytes, StandardCharsets.UTF_8);
            this.sourceText = sourceText;
            this.sourceLines = sourceText.length() == EMPTY_LINE_COUNT
                    ? List.of() : List.of(sourceText.split("\\R", -1));
            this.candidateJson = candidateJson;
        }

        /**
         * 追加一个带自动序号的问题。
         *
         * @param code 错误码
         * @param path 候选 JSON 路径
         * @param message 问题描述
         * @param action 修复动作
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        private void add(IssueCode code, String path, String message, String action) {
            String issueId = ISSUE_ID_PREFIX
                    + String.format(Locale.ROOT, ISSUE_ID_FORMAT, issues.size() + 1);
            issues.add(new ValidationIssue(issueId, code, path, message, action));
        }

        /**
         * 计算当前原文行数。
         *
         * @return 原文行数，空文档返回 0
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        private int lineCount() {
            return sourceLines.size();
        }

        /**
         * 返回当前校验结果。
         *
         * @return 不可变校验结果
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        private ValidationResult result() {
            String candidate = candidateJson == null ? "" : candidateJson;
            Optional<KnowledgePackage> packageValue =
                    issues.isEmpty() && knowledgePackage != null
                            ? Optional.of(knowledgePackage) : Optional.empty();
            return new ValidationResult(
                    issues.isEmpty() && packageValue.isPresent(),
                    issues,
                    packageValue,
                    sourceHash,
                    sha256(candidate));
        }

        /**
         * 返回当前源文件哈希。
         *
         * @return 原文哈希
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        private String sourceHash() {
            return sourceHash;
        }
    }
}
