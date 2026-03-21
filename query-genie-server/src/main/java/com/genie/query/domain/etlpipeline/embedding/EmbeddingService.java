package com.genie.query.domain.etlpipeline.embedding;

import java.util.List;

/**
 * 文本向量化服务。将文本转为固定维度的向量，用于语义检索。
 *
 * @author daicy
 * @date 2026/2/1
 */
public interface EmbeddingService {

    /**
     * 单条文本向量化。
     *
     * @param text 文本内容
     * @return 向量，维度与 ES 中 dense_vector 一致（如 768）
     */
    float[] embed(String text);

    /**
     * 批量文本向量化，便于减少调用次数。
     *
     * @param texts 文本列表
     * @return 与 texts 一一对应的向量列表
     */
    List<float[]> embed(List<String> texts);
}
