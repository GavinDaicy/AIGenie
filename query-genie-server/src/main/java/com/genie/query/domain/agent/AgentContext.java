package com.genie.query.domain.agent;

import lombok.Getter;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行上下文：维护一次 Agent 会话中的消息历史、步骤记录和工具统计。
 *
 * <p>整个 ReAct 循环期间共享同一个 AgentContext 实例：
 * <ul>
 *   <li>messages       — 传给 LLM 的完整对话消息列表（含 System/User/Assistant/ToolResponse）</li>
 *   <li>sessionId      — 关联会话 ID，用于步骤日志持久化</li>
 *   <li>toolCallCount  — 工具调用次数，超过阈值时触发上下文压缩</li>
 * </ul>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Getter
public class AgentContext {

    private final String sessionId;
    private final String originalQuestion;
    private final List<String> knowledgeCodes;
    private final List<Long> datasourceIds;

    /** 传给 LLM 的完整消息历史（System + User + Assistant + ToolResponse） */
    private final List<Message> messages = new ArrayList<>();

    /** 本次 Agent 执行中已完成的工具调用次数（用于判断是否需要上下文压缩） */
    private int toolCallCount = 0;

    /** 当前迭代轮次 */
    private int currentIteration = 0;

    public AgentContext(String sessionId, String originalQuestion,
                        List<String> knowledgeCodes, List<Long> datasourceIds) {
        this.sessionId = sessionId;
        this.originalQuestion = originalQuestion;
        this.knowledgeCodes = knowledgeCodes != null ? knowledgeCodes : List.of();
        this.datasourceIds = datasourceIds != null ? datasourceIds : List.of();
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public void addMessages(List<Message> msgs) {
        messages.addAll(msgs);
    }

    /** 记录一次工具调用完成 */
    public void incrementToolCallCount() {
        toolCallCount++;
    }

    /** 进入下一迭代轮次 */
    public void incrementIteration() {
        currentIteration++;
    }

    /** 替换消息列表（上下文压缩后使用） */
    public void replaceMessages(List<Message> compressedMessages) {
        messages.clear();
        messages.addAll(compressedMessages);
    }
}
