package com.genie.query.domain.qa.service;

import com.genie.query.domain.qa.model.ChatTurn;

import java.util.List;

/**
 * 对话历史总结：将多轮对话压缩为一段摘要，用于超长上下文时缩减 token。
 *
 * @author daicy
 * @date 2026/3/8
 */
public interface ConversationSummarizer {

    /**
     * 将历史轮次总结成一段简短摘要。
     *
     * @param turns 历史对话轮次（按时间顺序）
     * @return 摘要文本，供后续 prompt 使用
     */
    String summarize(List<ChatTurn> turns);
}
