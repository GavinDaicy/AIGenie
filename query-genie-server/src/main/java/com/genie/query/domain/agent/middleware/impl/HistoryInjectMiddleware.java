package com.genie.query.domain.agent.middleware.impl;

import com.genie.query.domain.agent.middleware.AgentMiddleware;
import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.agent.orchestration.AgentResult;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.domain.qa.model.ChatTurn;
import com.genie.query.domain.qa.service.ConversationSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 历史注入中间件：在 ReAct 开始前将会话历史注入到 AgentContext。
 *
 * <p>支持两种策略：
 * <ul>
 *   <li>截断模式：历史轮次 ≤ summarizeWhenTurnsOver 时，保留最近 maxHistoryTurns 轮</li>
 *   <li>摘要模式：历史轮次超出阈值时，早期轮次用 LLM 压缩为摘要，近期轮次原样保留</li>
 * </ul>
 *
 * <p>ORDER = 10：最先执行，确保历史消息注入在 PausedContext 和 Planning 之前。
 *
 * @author daicy
 */
@Component
public class HistoryInjectMiddleware implements AgentMiddleware {

    public static final int ORDER = 10;

    private static final Logger log = LoggerFactory.getLogger(HistoryInjectMiddleware.class);

    @Value("${app.agent.max-history-turns:5}")
    private int maxHistoryTurns;

    @Value("${app.agent.summarize-when-turns-over:5}")
    private int summarizeWhenTurnsOver;

    @Autowired(required = false)
    private ChatMessageDAO chatMessageDAO;

    @Autowired(required = false)
    private ConversationSummarizer conversationSummarizer;

    @Override
    public String getName() { return "HistoryInjectMiddleware"; }

    @Override
    public int getOrder() { return ORDER; }

    @Override
    public void before(AgentContext context) {
        String sessionId = context.getSessionId();
        if (sessionId == null || chatMessageDAO == null) {
            return;
        }
        int fetchLimit = (maxHistoryTurns + summarizeWhenTurnsOver + 2) * 2;
        List<ChatMessage> rawMessages;
        try {
            rawMessages = chatMessageDAO.listBySessionIdOrderBySortOrder(sessionId, fetchLimit);
        } catch (Exception e) {
            log.warn("[HistoryInjectMiddleware] 加载历史消息失败 | sessionId={} | error={}", sessionId, e.getMessage());
            return;
        }
        if (rawMessages.isEmpty()) {
            return;
        }

        List<ChatTurn> allTurns = rawMessages.stream()
                .map(m -> ChatTurn.builder().role(m.getRole()).content(m.getContent()).build())
                .collect(Collectors.toList());
        int totalTurns = allTurns.size();

        if (totalTurns <= summarizeWhenTurnsOver * 2 || conversationSummarizer == null) {
            // 截断模式：保留最近 maxHistoryTurns 轮
            int from = Math.max(0, totalTurns - maxHistoryTurns * 2);
            List<ChatTurn> recentTurns = allTurns.subList(from, totalTurns);
            for (ChatTurn turn : recentTurns) {
                context.addMessage(toSpringAiMessage(turn));
            }
        } else {
            // 摘要模式：早期轮次压缩，近期轮次原样保留
            int keepRecentCount = summarizeWhenTurnsOver * 2;
            int summarizeSize = totalTurns - keepRecentCount;
            List<ChatTurn> toSummarize = allTurns.subList(0, summarizeSize);
            List<ChatTurn> recent = allTurns.subList(summarizeSize, totalTurns);

            String summary = null;
            try {
                summary = conversationSummarizer.summarize(toSummarize);
            } catch (Exception e) {
                log.warn("[HistoryInjectMiddleware] 历史摘要失败，降级截断 | error={}", e.getMessage());
            }
            if (summary != null && !summary.isBlank()) {
                context.addMessage(new SystemMessage("[此前对话摘要] " + summary));
            }
            for (ChatTurn turn : recent) {
                context.addMessage(toSpringAiMessage(turn));
            }
        }
        log.info("[HistoryInjectMiddleware] 历史注入完成 | sessionId={} | 原始消息数={} | 总轮数={}",
                sessionId, rawMessages.size(), totalTurns);
    }

    @Override
    public void after(AgentContext context, AgentResult result) {}

    private Message toSpringAiMessage(ChatTurn turn) {
        if ("user".equals(turn.getRole())) {
            return new UserMessage(turn.getContent());
        } else if ("ask_user".equals(turn.getRole())) {
            return new AssistantMessage("[Agent追问] " + turn.getContent());
        } else {
            return new AssistantMessage(turn.getContent());
        }
    }
}
