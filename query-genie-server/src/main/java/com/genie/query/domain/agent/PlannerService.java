package com.genie.query.domain.agent;

import java.util.List;

/**
 * 任务规划器接口：智能分析用户问题，判断是否存在步骤间数据依赖，并生成结构化执行计划。
 *
 * <p>适用于依赖链场景，例如：
 * <ul>
 *   <li>先查项目列表获取编码 → 再根据编码查材料报价</li>
 *   <li>先查销量最高的供应商ID → 再查该供应商的联系方式</li>
 * </ul>
 *
 * <p>对于并列独立的多子问题（如"总文档数？哪个知识库最多？"），由
 * {@link AgentOrchestratorImpl} 的多子问题清单逻辑处理，无需此规划器介入。
 *
 * @author daicy
 * @date 2026/4/3
 */
public interface PlannerService {

    /**
     * 分析用户问题，若存在步骤间数据依赖则生成执行计划，否则返回 null。
     *
     * @param question       用户原始问题
     * @param knowledgeCodes 当前可用知识库编码列表（用于提示 LLM 可用工具范围）
     * @param datasourceIds  当前可用数据源 ID 列表（用于提示 LLM 可用工具范围）
     * @return 结构化执行计划（含步骤列表及依赖关系），无需计划时返回 null
     */
    ExecutionPlan plan(String question, List<String> knowledgeCodes, List<Long> datasourceIds);
}
