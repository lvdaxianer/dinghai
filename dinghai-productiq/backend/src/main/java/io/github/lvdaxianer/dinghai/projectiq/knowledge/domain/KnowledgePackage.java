package io.github.lvdaxianer.dinghai.projectiq.knowledge.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 描述一份与 Markdown 原文绑定的产品知识包。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public record KnowledgePackage(
        String schemaVersion,
        Source source,
        Document document,
        List<Section> sections,
        List<Concept> concepts,
        List<Relation> relations) {

    /**
     * 规范化集合字段，避免解析缺失数组后向调用方返回 null。
     *
     * @param schemaVersion 协议版本
     * @param source 原文来源
     * @param document 文档元数据
     * @param sections 文档章节
     * @param concepts 文档概念
     * @param relations 本体关系
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public KnowledgePackage {
        sections = copyList(sections);
        concepts = copyList(concepts);
        relations = copyList(relations);
    }

    /**
     * 将可空集合转换为不可变集合。
     *
     * @param values 待转换集合
     * @param <T> 集合元素类型
     * @return 不可变集合
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    private static <T> List<T> copyList(List<T> values) {
        // 缺失数组按空集合处理，保持领域记录对调用方的空值安全。
        if (values == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * 描述知识包的来源文件。
     *
     * @param key Markdown 文件名
     * @param hash 原文 SHA-256
     * @param title 产品文档标题
     * @param language 文档语言
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record Source(String key, String hash, String title, String language) {
    }

    /**
     * 描述一个产品文档。
     *
     * @param id 稳定文档 ID
     * @param summary 文档摘要
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record Document(String id, String summary) {
    }

    /**
     * 描述一个可独立检索的文档章节。
     *
     * @param id 稳定章节 ID
     * @param headingPath 标题路径
     * @param summary 章节摘要
     * @param content 章节原文
     * @param conceptIds 章节关联概念
     * @param sourceAnchor 原文行锚点
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record Section(
            String id,
            List<String> headingPath,
            String summary,
            String content,
            List<String> conceptIds,
            SourceAnchor sourceAnchor) {

        /**
         * 将章节集合字段规范化为不可变集合。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        public Section {
            headingPath = copyList(headingPath);
            conceptIds = copyList(conceptIds);
        }
    }

    /**
     * 描述章节在 Markdown 原文中的行范围。
     *
     * @param startLine 起始行，从 1 开始
     * @param endLine 结束行，从 1 开始
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record SourceAnchor(int startLine, int endLine) {
    }

    /**
     * 描述一个可跨文档复用的产品概念。
     *
     * @param id 全局稳定概念 ID
     * @param name 概念名称
     * @param type 概念类型
     * @param definition 概念定义
     * @param canonicalAliases 标准别名
     * @param queryAliases 查询口语别名
     * @param evidenceSectionIds 当前文档证据章节
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record Concept(
            String id,
            String name,
            String type,
            String definition,
            List<String> canonicalAliases,
            List<String> queryAliases,
            List<String> evidenceSectionIds) {

        /**
         * 将概念别名和证据集合规范化为不可变集合。
         *
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        public Concept {
            canonicalAliases = copyList(canonicalAliases);
            queryAliases = copyList(queryAliases);
            evidenceSectionIds = copyList(evidenceSectionIds);
        }
    }

    /**
     * 描述两个概念之间的本体关系。
     *
     * @param operation 关系操作
     * @param from 起点概念
     * @param type 关系类型
     * @param to 终点概念
     * @param evidence 关系证据
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    public record Relation(
            String operation,
            String from,
            String type,
            String to,
            Evidence evidence) {

        /**
         * 描述关系在章节原文中的逐字证据。
         *
         * @param sectionId 证据章节
         * @param quote 原文引用
         * @author lvdaxianer@yeah.net
         * @date 2026-09-16
         */
        public record Evidence(String sectionId, String quote) {
        }
    }
}
