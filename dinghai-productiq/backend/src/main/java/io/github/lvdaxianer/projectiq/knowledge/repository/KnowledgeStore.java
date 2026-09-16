package io.github.lvdaxianer.projectiq.knowledge.repository;

import io.github.lvdaxianer.projectiq.knowledge.domain.KnowledgePackage;
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
}
