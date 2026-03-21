package com.genie.query.controller.dto;

import com.genie.query.domain.document.model.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单条检索结果：分块内容 + 文档信息。
 *
 * @author daicy
 * @date 2026/2/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResultItem {
    /** 知识库编码 */
    private String knowledgeCode;
    /** 相关性分数 */
    private double score;
    /** 分块具体内容（字段名 -> 值） */
    private Map<String, Object> chunkContent;
    /** 所属文档信息 */
    private Document document;
}
