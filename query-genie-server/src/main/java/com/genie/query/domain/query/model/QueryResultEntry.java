package com.genie.query.domain.query.model;

import com.genie.query.domain.document.model.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单条检索结果的领域模型：分块内容 + 文档信息。
 * 由 QueryDomainService 返回，Application 层转换为 QueryResultItem DTO。
 *
 * @author daicy
 * @date 2026/2/9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResultEntry {
    /** 知识库编码 */
    private String knowledgeCode;
    /** 相关性分数 */
    private double score;
    /** 分块具体内容（字段名 -> 值） */
    private Map<String, Object> chunkContent;
    /** 所属文档信息 */
    private Document document;
}
