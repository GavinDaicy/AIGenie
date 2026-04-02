package com.genie.query.domain.agent;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 上下文窗口管理器：当 Agent 工具调用次数过多时，对早期 Observation 做摘要压缩，
 * 避免触达 LLM Context Window 上限。
 *
 * <p>压缩策略参考现有 {@code ConversationSummarizer}：
 * 超过阈值（默认 6 次工具调用）后，对早期轮次的 ToolResponse 内容做摘要，
 * 只保留结论性文本，丢弃原始数据表格等冗余内容。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface ContextWindowManager {

    /**
     * 判断是否需要对当前上下文执行压缩。
     *
     * @param context 当前 Agent 执行上下文
     * @return true 表示需要压缩
     */
    boolean shouldCompress(AgentContext context);

    /**
     * 执行上下文压缩：对早期工具调用结果做 LLM 摘要，返回压缩后的消息列表。
     *
     * @param context   当前 Agent 执行上下文
     * @param chatModel 用于生成摘要的 LLM
     */
    void compress(AgentContext context, ChatModel chatModel);
}
