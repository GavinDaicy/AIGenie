package com.genie.query.domain.vectorstore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 向量库检索入参。由应用层根据知识库配置组装。
 *
 * @author daicy
 * @date 2026/2/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchParam {
    /** 要检索的知识库编码列表（索引名） */
    private List<String> knowledgeCodes;
    /** 关键字查询（KEYWORD / HYBRID 时必填） */
    private String keywordQuery;
    /** 查询向量（VECTOR / HYBRID 时必填） */
    private float[] queryVector;
    /** 检索方式 */
    private SearchMode mode;
    /** 每个知识库编码 -> 用于关键字检索的字段 key 列表（fullTextSearchable） */
    private Map<String, List<String>> textFieldsPerIndex;
    /** 每个知识库编码 -> 用于向量检索的字段 key 列表（语义字段，ES 中为 fieldKey + "_vector_system"）。与 queryVector 配合做多 knn 检索 */
    private Map<String, List<String>> vectorFieldsPerIndex;
    /**
     * 每个知识库编码 -> 字段 key -> boost 权重。
     * 对 keyword 检索用于 multi_match 字段权重，对 vector 检索用于多向量融合时的权重。
     */
    private Map<String, Map<String, Double>> fieldBoostsPerIndex;
    /** 返回条数上限 */
    private int size;
    /**
     * 是否将得分归一化到 0~10。true（默认）：归一化，便于阈值过滤与展示；false：返回 ES 原始分数，便于对比归一化前后差异。
     */
    @Builder.Default
    private boolean normalizeScore = true;

    /**
     * 时间衰减开关（可选）。
     * <ul>
     *   <li>true：启用时间衰减</li>
     *   <li>false：禁用时间衰减</li>
     *   <li>null：由调用方根据知识库默认配置决定后再传入</li>
     * </ul>
     */
    private Boolean useTimeDecay;

    /**
     * 每个知识库编码 -> 时间衰减参数（若该知识库不启用则可为空或 enabled=false）。
     */
    private Map<String, TimeDecayParam> timeDecayPerIndex;
}
