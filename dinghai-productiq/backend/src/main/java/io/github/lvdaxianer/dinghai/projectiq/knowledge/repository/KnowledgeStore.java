package io.github.lvdaxianer.dinghai.projectiq.knowledge.repository;

import io.github.lvdaxianer.dinghai.projectiq.knowledge.domain.KnowledgePackage;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SQLite 当前知识事实的事务仓储边界。
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-09-16
 */
public interface KnowledgeStore {
    /**
     * 在一个事务中替换来源拥有的章节、映射和证据。
     *
     * @param knowledgePackage 待持久化的知识包
     * @param updatedAt 文档更新时间
     * @throws SQLException SQLite 写入失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    void replaceSource(KnowledgePackage knowledgePackage, Instant updatedAt) throws SQLException;

    /**
     * 显式删除一个来源及其来源证据。
     *
     * @param sourceKey 来源文件键
     * @throws SQLException SQLite 删除失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    void deleteSource(String sourceKey) throws SQLException;

    /**
     * 按来源读取当前文档。
     *
     * @param sourceKey 来源文件键
     * @return 文档记录；不存在时为空
     * @throws SQLException SQLite 查询失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    Optional<DocumentRecord> findDocumentBySourceKey(String sourceKey) throws SQLException;

    /**
     * 按来源读取当前章节。
     *
     * @param sourceKey 来源文件键
     * @return 按章节 ID 排序的章节记录
     * @throws SQLException SQLite 查询失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    List<SectionRecord> findSectionsBySourceKey(String sourceKey) throws SQLException;

    /**
     * 创建一个可恢复的向量索引同步任务。
     *
     * @param sourceKey 来源文件键
     * @param operation 索引操作，只允许 upsert 或 delete
     * @throws SQLException SQLite 写入失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    void enqueueVectorJob(String sourceKey, String operation) throws SQLException;

    /**
     * 读取待处理的向量索引任务。
     *
     * @param limit 最大返回数量
     * @return 待处理任务
     * @throws SQLException SQLite 查询失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    List<VectorIndexJobRecord> findPendingVectorJobs(int limit) throws SQLException;

    /**
     * 更新索引任务状态和失败信息。
     *
     * @param jobId 任务 ID
     * @param status 新状态
     * @param errorMessage 失败信息，无错误时为空
     * @throws SQLException SQLite 更新失败
     * @author lvdaxianer@yeah.net
     * @date 2026-09-16
     */
    void updateVectorJobStatus(long jobId, String status, String errorMessage) throws SQLException;

    /**
     * 按稳定章节 ID 读取当前章节。
     *
     * @param sectionId 稳定章节 ID
     * @return 当前章节；不存在时为空
     * @throws SQLException SQLite 查询失败
     */
    Optional<SectionRecord> findSectionById(String sectionId) throws SQLException;

    /**
     * 按稳定文档 ID 读取当前文档。
     *
     * @param documentId 稳定文档 ID
     * @return 当前文档；不存在时为空
     * @throws SQLException SQLite 查询失败
     */
    Optional<DocumentRecord> findDocumentById(String documentId) throws SQLException;

    /**
     * 按概念 ID 读取其关系边。
     *
     * @param conceptId 出边起点概念 ID
     * @return 按关系 ID 排序的关系
     * @throws SQLException SQLite 查询失败
     */
    List<RelationRecord> findRelationsFromConcept(String conceptId) throws SQLException;

    /**
     * 按章节读取其关联概念 ID。
     *
     * @param sectionId 章节 ID
     * @return 关联概念 ID
     * @throws SQLException SQLite 查询失败
     */
    List<String> findConceptIdsBySectionId(String sectionId) throws SQLException;

    /**
     * 按概念读取当前章节。
     *
     * @param conceptId 概念 ID
     * @return 关联章节
     * @throws SQLException SQLite 查询失败
     */
    List<SectionRecord> findSectionsByConceptId(String conceptId) throws SQLException;
}
