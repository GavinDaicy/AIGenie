package com.genie.query.domain.knowledge.model;

import lombok.Data;

/**
 * 知识库字段
 *
 * @author daicy
 * @date 2026/1/6
 */
@Data
public class KLField {
    /** 字段id标识 */
    private String id;
    /** 字段所属知识库id标识 */
    private String knowledgeId;
    /** 字段标识 */
    private String fieldKey;
    /** 字段名称 */
    private String name;
    /** 字段描述 */
    private String description;
    /** 字段类型 */
    private KLFieldType type;
    /** 支持精准匹配 */
    private Boolean matchable;
    /** 可全文检索 */
    private Boolean fullTextSearchable;
    /** 支持语义检索 */
    private Boolean semanticSearchable;
    /** 可排序 */
    private Boolean sortable;
    /** 顺序 */
    private Integer ord;
    /**
     * 字段权重，用于检索打分时调节该字段的重要性，默认 1.0。
     */
    private Double boost;
}
