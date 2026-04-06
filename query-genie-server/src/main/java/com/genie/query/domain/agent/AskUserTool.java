package com.genie.query.domain.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 追问工具：当信息不足以完成任务时，向用户主动追问补充信息。
 *
 * <p>执行机制：{@link AgentOrchestratorImpl} 在工具调用循环中按 {@link #TOOL_NAME} 识别本工具，
 * 直接从 {@code toolCall.arguments()} JSON 中提取追问内容，推送 {@code ASK_USER} SSE 事件并暂停
 * 当前 ReAct 循环，无需经过 {@code ToolCallback.call()}。
 * 用户回复后，前端以相同 sessionId 发起新一轮 Agent 请求，历史上下文自动携带追问内容和用户回复。
 *
 * <p>使用原则：
 * <ul>
 *   <li>每次只问一个最关键的问题，不要连续多个问题合并追问</li>
 *   <li>仅当追问能实质性提升答案质量时才使用，简单问题不要追问</li>
 * </ul>
 *
 * @author daicy
 */
@Component
public class AskUserTool {

    /** Spring AI 注册的工具名称，与方法名一致，供 Orchestrator 按名称拦截 */
    public static final String TOOL_NAME = "askUser";

    /**
     * 向用户追问补充信息，用于信息不足以完成任务的场景。
     *
     * <p>注意：此方法体在正常流程中不会被执行。{@link AgentOrchestratorImpl} 检测到
     * 工具名为 {@link #TOOL_NAME} 时会在调用 {@code ToolCallback} 之前直接拦截处理。
     *
     * @param question 向用户提出的追问内容（简洁、明确、一次只问一个问题）
     * @return 不会被实际返回
     */
    @Tool(description = "当用户信息不足以完成任务时向用户追问。例如：用户未说明查询规格、时间范围、对象名称等关键条件时使用。每次只追问一个最关键的问题。不要在信息已经足够的情况下追问。")
    public String askUser(
            @ToolParam(description = "向用户提出的追问内容，应简洁明确，一次只问一个问题") String question) {
        return question;
    }
}
