package com.genie.query.infrastructure.llm.dashscope;

import com.genie.query.domain.qa.model.ChatTurn;
import com.genie.query.domain.qa.service.ConversationSummarizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 使用 DashScope ChatModel 将历史对话总结为一段摘要。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Service
public class DashScopeConversationSummarizer implements ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(DashScopeConversationSummarizer.class);

    private static final String SUMMARIZE_SYSTEM = "你是一个对话摘要助手。请将下面多轮问答总结成一段简洁的摘要，保留用户主要问题和助手回答的要点，便于后续对话延续上下文。只输出摘要内容，不要其他解释。";

    @Autowired(required = false)
    private ChatModel chatModel;

    @Override
    public String summarize(List<ChatTurn> turns) {
        if (chatModel == null || turns == null || turns.isEmpty()) {
            return "";
        }
        String conversation = turns.stream()
                .map(t -> (t.getRole().equalsIgnoreCase("user") ? "用户: " : "助手: ") + t.getContent())
                .collect(Collectors.joining("\n\n"));

        String userPrompt = "请总结以下对话：\n\n" + conversation;

        try {
            String summary = chatModel.call(new Prompt(List.of(
                    new SystemMessage(SUMMARIZE_SYSTEM),
                    new UserMessage(userPrompt)
            ))).getResult().getOutput().getText();
            return StringUtils.isBlank(summary) ? "" : summary.trim();
        } catch (Exception e) {
            log.warn("对话总结调用异常: {}", e.getMessage());
            return "";
        }
    }
}
