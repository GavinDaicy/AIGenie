package com.genie.query.domain.etlpipeline.model;

import lombok.Data;

import java.util.Map;

/**
 * 带向量信息的切块，用于写入 ES。
 * semanticVectors: 需要语义检索的字段 fieldKey -> 768 维向量。
 *
 * @author daicy
 * @date 2026/2/1
 */
@Data
public class IndexChunk {
    private Chunk chunk;
    /** 需要向量化的字段 fieldKey -> 向量 */
    private Map<String, float[]> semanticVectors;
}
