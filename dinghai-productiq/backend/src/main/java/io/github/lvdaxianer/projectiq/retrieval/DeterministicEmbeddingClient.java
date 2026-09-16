package io.github.lvdaxianer.projectiq.retrieval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** 用 SHA-256 生成稳定测试向量的离线实现。 */
public final class DeterministicEmbeddingClient implements EmbeddingClient {
    private static final int VECTOR_SIZE = 8;

    /** 将文本映射为固定长度的非零测试向量。 */
    @Override
    public List<Float> embed(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            List<Float> values = new ArrayList<>(VECTOR_SIZE);
            for (int index = 0; index < VECTOR_SIZE; index++) {
                values.add((digest[index] & 0xFF) / 255.0f);
            }
            return values;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("测试向量生成失败", exception);
        }
    }
}
