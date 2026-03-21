package com.genie.query.controller.dto;

import com.genie.query.domain.vectorstore.SearchMode;
import lombok.Data;

import java.util.List;

/**
 * 智能问答接口入参。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
public class QaRequest {
    /** 会话 ID，不传则单轮问答不落库 */
    private String sessionId;
    /** 用户问题（必填） */
    private String question;
    /** 知识库编码列表，不传或空表示所有已发布且具备字段配置的知识库 */
    private List<String> knowledgeCodes;
    /** 检索方式，默认 HYBRID */
    private SearchMode mode = SearchMode.HYBRID;
    /** 检索条数（用于上下文的条数），默认由配置决定 */
    private Integer size;
    /** 是否对检索结果做 rerank，默认 true */
    private Boolean rerank = true;

    /** 是否启用 Query 改写（可选，不传则使用服务端默认配置） */
    private Boolean enableQueryRewrite;

    /**
     * 生成的查询总数（包含 1 个主要查询），至少为 1。
     * 当前仅在 enableQueryRewrite=true 时有效。
     */
    private Integer rewriteQueryCount;

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
