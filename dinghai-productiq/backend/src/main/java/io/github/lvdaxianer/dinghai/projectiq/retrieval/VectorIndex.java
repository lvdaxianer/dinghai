package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import java.io.IOException;
import java.util.List;

/** 章节向量索引端口。 */
public interface VectorIndex {
    /** 批量写入章节向量点。 */
    void upsert(List<VectorPoint> points) throws IOException, InterruptedException;

    /** 删除一个来源拥有的所有向量点。 */
    void deleteBySourceKey(String sourceKey) throws IOException, InterruptedException;

    /** 查询最相似的章节候选。 */
    List<VectorMatch> search(List<Float> query, int limit) throws IOException, InterruptedException;

    /** 重建一个 collection 的向量空间。 */
    void recreate(int dimensions) throws IOException, InterruptedException;
}
