package com.genie.query.domain.agent;

import java.io.PrintWriter;
import java.util.List;

/**
 * Agent 编排引擎接口：驱动 ReAct 循环，管理工具调用与结果观察，通过 SSE 实时推送步骤事件。
 *
 * <p>ReAct 循环：Thought → Action(ToolCall) → Observation(ToolResult) → ... → FinalAnswer
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface AgentOrchestrator {

    /**
     * 执行 Agent 任务，通过 SSE 实时推送推理步骤事件。
     *
     * @param question       用户问题
     * @param sessionId      会话 ID（携带历史上下文）
     * @param knowledgeCodes 可访问的知识库编码列表
     * @param datasourceIds  可访问的数据源 ID 列表
     * @param writer         响应流写入器（由 AgentController 从 HttpServletResponse 获取）
     * @return 执行结果（含最终答案文本和本轮引用数据），追问暂停时 finalAnswer 为 null
     */
    AgentResult execute(String question, String sessionId,
                        List<String> knowledgeCodes, List<Long> datasourceIds,
                        PrintWriter writer);
}
