package com.genie.query.domain.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 追问工具：当信息不足以完成任务时，向用户主动追问补充信息。
 *
 * <p>执行机制：方法返回包含 {@link #ASK_USER_SIGNAL} 前缀的字符串，
 * {@link AgentOrchestratorImpl} 检测到此信号后推送 {@code ASK_USER} SSE 事件并暂停当前 ReAct 循环。
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

    /** 追问信号前缀，Orchestrator 通过此前缀识别并处理追问动作 */
    public static final String ASK_USER_SIGNAL = "__ASK_USER__: ";

    /**
     * 向用户追问补充信息，用于信息不足以完成任务的场景。
     *
     * <p>触发示例：
     * <ul>
     *   <li>用户问"帮我查钢筋价格" → 追问"请问需要查哪种规格的钢筋（如直径、型号）？"</li>
     *   <li>用户问"最近销售情况怎么样" → 追问"请问您希望查询的时间范围是？"</li>
     * </ul>
     *
     * @param question 向用户提出的追问内容（简洁、明确、一次只问一个问题）
     * @return 包含 ASK_USER_SIGNAL 前缀的信号字符串，由 Orchestrator 拦截处理
     */
    @Tool(description = "当用户信息不足以完成任务时向用户追问。例如：用户未说明查询规格、时间范围、对象名称等关键条件时使用。每次只追问一个最关键的问题。不要在信息已经足够的情况下追问。")
    public String askUser(
            @ToolParam(description = "向用户提出的追问内容，应简洁明确，一次只问一个问题") String question) {
        return ASK_USER_SIGNAL + question;
    }
}
