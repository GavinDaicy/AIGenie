package com.genie.query.infrastructure.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.genie.query.domain.etlpipeline.embedding.EmbeddingService;

import java.util.List;

/**
 * 基于 Spring AI EmbeddingModel（如 DashScope）的向量化实现。
 *
 * @author daicy
 * @date 2026/2/1
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String text) {
        if (embeddingModel == null) {
            throw new IllegalStateException("未配置 EmbeddingModel，请配置 spring-ai-alibaba 或其它 Embedding 实现");
        }
        return embeddingModel.embed(text);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (embeddingModel == null) {
            throw new IllegalStateException("未配置 EmbeddingModel，请配置 spring-ai-alibaba 或其它 Embedding 实现");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return embeddingModel.embed(texts);
    }
}
