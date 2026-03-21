package com.genie.query.controller.dto;

import com.genie.query.domain.vectorstore.SearchMode;
import lombok.Data;

import java.util.List;

/**
 * 查询接口入参。
 *
 * @author daicy
 * @date 2026/2/8
 */
@Data
public class QueryRequest {
    /** 查询关键词/问句（必填） */
    private String query;
    /** 知识库编码列表，不传或空则查询所有已发布知识库 */
    private List<String> knowledgeCodes;
    /** 检索方式：KEYWORD / VECTOR / HYBRID，默认 HYBRID */
    private SearchMode mode = SearchMode.HYBRID;
    /** 返回条数上限，默认 10 */
    private Integer size = 10;
    /** 是否将得分归一化到 0~10，默认 true；设为 false 可对比归一化前后分值差异 */
    private Boolean normalizeScore;
    /** 是否对检索结果做 rerank 重排序，默认 false；需同时开启 app.rerank.enabled */
    private Boolean rerank;

    /**
     * 是否启用时间衰减（可选）。
     * <ul>
     *   <li>true：强制启用（若知识库未配置时间衰减字段/参数，则该知识库不生效）</li>
     *   <li>false：强制关闭</li>
     *   <li>null：使用知识库默认配置（timeDecayConfig.enabled）</li>
     * </ul>
     */
    private Boolean useTimeDecay;
}
