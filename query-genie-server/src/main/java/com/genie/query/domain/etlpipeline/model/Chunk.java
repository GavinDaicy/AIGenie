package com.genie.query.domain.etlpipeline.model;

import lombok.Data;

import java.util.Map;

/**
 * 文档切块结果。
 * 单内容字段模式：data 仅包含一个语义字段 key 及其文本内容。
 * 多字段模式（Excel/CSV 行）：data 为 fieldKey -> 单元格值的映射。
 *
 * @author daicy
 * @date 2026/2/1
 */
@Data
public class Chunk {
    /** 块序号 */
    private int order;
    /**
     * 字段键值对。单内容模式 key 为内容字段的 fieldKey；多字段模式为知识库 fieldKey -> 值。
     */
    private Map<String, Object> data;
}
