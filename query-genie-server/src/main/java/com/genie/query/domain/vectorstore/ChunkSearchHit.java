package com.genie.query.domain.vectorstore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单条分块检索命中的结果（不含文档信息，由应用层补全）。
 *
 * @author daicy
 * @date 2026/2/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSearchHit {
    /** 知识库编码（索引名） */
    private String knowledgeCode;
    /** 源文档 id */
    private String docId;
    /** 相关性分数 */
    private double score;
    /** 分块内容（字段名 -> 值，不含向量字段） */
    private Map<String, Object> chunkContent;
}
