package io.github.lvdaxianer.dinghai.projectiq.retrieval;

import java.util.List;

/** 文本向量化端口，生产环境可接入模型网关。 */
public interface EmbeddingClient {
    /**
     * 将文本转换为向量。
     *
     * @param text 待向量化文本
     * @return 向量
     */
    List<Float> embed(String text);
}
