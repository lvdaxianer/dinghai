package io.github.lvdaxianer.projectiq.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将重排后的 section ID 转换为有界完整证据。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public final class EvidenceContextBuilder {
    /** Answerer 输入的字符上限。 */
    public static final int MAX_CHARACTERS = 8_000;

    /** 构建最多六节、三份文档且不截断章节正文的证据。 */
    public EvidenceContext build(List<EvidenceCandidate> candidates, List<String> sectionIds) {
        Map<String, EvidenceCandidate> byId = indexCandidates(candidates);
        List<EvidenceCandidate> sections = new ArrayList<>(StructuredReranker.MAX_SECTIONS);
        Set<String> documentIds = new HashSet<>(StructuredReranker.MAX_DOCUMENTS);
        int characterCount = 0;
        for (String sectionId : sectionIds) {
            EvidenceCandidate candidate = byId.get(sectionId);
            if (candidate == null || candidate.hop() >= 3 || !canAddDocument(candidate, documentIds)
                    || characterCount + sizeOf(candidate) > MAX_CHARACTERS) {
                continue;
            }
            sections.add(candidate);
            documentIds.add(candidate.documentId());
            characterCount += sizeOf(candidate);
            if (sections.size() == StructuredReranker.MAX_SECTIONS) {
                break;
            }
        }
        return new EvidenceContext(List.copyOf(sections), Set.copyOf(documentIds), characterCount);
    }

    /** 建立 section ID 到候选的索引。 */
    private Map<String, EvidenceCandidate> indexCandidates(List<EvidenceCandidate> candidates) {
        Map<String, EvidenceCandidate> byId = new HashMap<>(Math.max(16, candidates.size() * 2));
        candidates.forEach(candidate -> byId.putIfAbsent(candidate.sectionId(), candidate));
        return byId;
    }

    /** 判断候选文档是否已在三份文档上限内。 */
    private boolean canAddDocument(EvidenceCandidate candidate, Set<String> documentIds) {
        return documentIds.contains(candidate.documentId())
                || documentIds.size() < StructuredReranker.MAX_DOCUMENTS;
    }

    /** 计算完整证据的字符数，正文不做截断。 */
    private int sizeOf(EvidenceCandidate candidate) {
        return textLength(candidate.documentTitle()) + textLength(candidate.summary())
                + textLength(candidate.content());
    }

    /** 将空文本按零长度处理。 */
    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }
}
