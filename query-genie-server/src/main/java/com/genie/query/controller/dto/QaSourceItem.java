package com.genie.query.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 智能问答引用来源项，与 QueryResultItem 对齐便于前端展示。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaSourceItem {
    /** 知识库编码 */
    private String knowledgeCode;
    /** 相关性分数 */
    private double score;
    /** 文档名称（可选） */
    private String documentName;
    /** 分块内容（字段名 -> 值） */
    private Map<String, Object> chunkContent;
}
