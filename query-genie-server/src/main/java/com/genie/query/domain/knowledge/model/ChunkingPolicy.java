package com.genie.query.domain.knowledge.model;

import lombok.Data;

/**
 * 知识库分块配置。
 * <p>
 * 该值对象用于描述分块策略及其参数，便于在领域层中以统一方式驱动
 * 文档切块实现，而不暴露具体实现细节给应用层。
 *
 * @author daicy
 * @date 2026/3/9
 */
@Data
public class ChunkingPolicy {

    /**
     * 分块策略。
     */
    private ChunkingStrategy strategy;

    /**
     * 文本分块大小（字符数），适用于长文本类策略。
     */
    private Integer chunkSize;

    /**
     * 文本分块最大大小（字符数），适用于长文本类策略。
     */
    private Integer maxChunkSize;

    /**
     * 文本块间重叠大小（字符数），适用于长文本类策略。
     */
    private Integer overlap;

    /**
     * 每个块聚合的行数，适用于表格类行聚合策略。
     */
    private Integer rowsPerChunk;

    /**
     * 相邻块之间的行重叠数，适用于表格类行聚合策略。
     */
    private Integer rowOverlap;
}

