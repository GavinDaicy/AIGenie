package com.genie.query.domain.agent;

/**
 * 语义路由器：对用户问题进行轻量分类，决定走 RAG、SQL Agent 还是完整 ReAct Agent。
 *
 * <p>分类策略：关键词规则优先（毫秒级），兜底 LLM 分类（单次轻量调用）。
 * 引入此路由层的原因：约 80% 的简单知识文档类问题无需走 Agent 多轮规划，
 * 直接走 RAG 可节省 1-2 次额外 LLM 调用，响应时间从 15-30 秒降至 3-5 秒。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface SemanticRouter {

    /**
     * 对用户问题进行语义分类。
     *
     * @param question 用户输入的自然语言问题
     * @return 问题类型枚举，决定后续路由方向
     */
    QuestionType route(String question);
}
