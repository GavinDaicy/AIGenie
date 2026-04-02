package com.genie.query.domain.agent;

/**
 * 用户问题类型枚举，由 {@link SemanticRouter} 分类输出。
 *
 * <ul>
 *   <li>{@link #KNOWLEDGE}  — 知识文档类，直接走现有 RAG Pipeline（快速，2-4秒）</li>
 *   <li>{@link #DATA_QUERY} — 数据分析类，直接走 SQL Agent（跳过RAG工具）</li>
 *   <li>{@link #COMPLEX}    — 综合/复杂类，走完整 ReAct Agent（多工具协同）</li>
 * </ul>
 *
 * @author daicy
 * @date 2026/4/2
 */
public enum QuestionType {

    /**
     * 知识文档类：产品信息、规范文件、操作手册等，约占 80%，直接走 RAG Pipeline 避免额外 LLM 调用。
     */
    KNOWLEDGE,

    /**
     * 数据分析类：价格分析、订单统计、供应商比较等，直接调用 SqlQueryTool。
     */
    DATA_QUERY,

    /**
     * 综合/复杂类：需要跨工具协同或决策较复杂，走完整 ReAct Agent 循环。
     */
    COMPLEX
}
