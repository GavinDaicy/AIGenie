package com.genie.query.domain.agent.middleware;

import com.genie.query.domain.agent.middleware.impl.HistoryInjectMiddleware;
import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.domain.qa.service.ConversationSummarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * HistoryInjectMiddleware 单元测试：覆盖空历史、截断模式、摘要模式三个场景。
 */
class HistoryInjectMiddlewareTest {

    private HistoryInjectMiddleware middleware;
    private ChatMessageDAO chatMessageDAO;
    private ConversationSummarizer conversationSummarizer;

    @BeforeEach
    void setUp() {
        middleware = new HistoryInjectMiddleware();
        chatMessageDAO = mock(ChatMessageDAO.class);
        conversationSummarizer = mock(ConversationSummarizer.class);
        ReflectionTestUtils.setField(middleware, "chatMessageDAO", chatMessageDAO);
        ReflectionTestUtils.setField(middleware, "conversationSummarizer", null);
        ReflectionTestUtils.setField(middleware, "maxHistoryTurns", 5);
        ReflectionTestUtils.setField(middleware, "summarizeWhenTurnsOver", 5);
    }

    private AgentContext makeContext() {
        return new AgentContext("session1", "question", List.of(), List.of(), null);
    }

    private ChatMessage makeMsg(String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    // ──────────────────────────────────────────────
    // 场景 1：空历史 → 不注入任何消息
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景1: DB 无历史消息时，不向 context 添加任何消息")
    void emptyHistoryInjectsNothing() {
        when(chatMessageDAO.listBySessionIdOrderBySortOrder(eq("session1"), anyInt()))
                .thenReturn(Collections.emptyList());

        AgentContext context = makeContext();
        middleware.before(context);

        assertThat(context.getMessages()).isEmpty();
    }

    // ──────────────────────────────────────────────
    // 场景 2：截断模式 — 历史轮次 ≤ summarizeWhenTurnsOver，保留最近 maxHistoryTurns 轮
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景2: 历史轮次 ≤ 阈值时，截断保留最近 maxHistoryTurns 轮（user+assistant 均注入）")
    void truncationModeInjectsRecentTurns() {
        // 构造 4 轮（8 条）历史
        List<ChatMessage> msgs = List.of(
                makeMsg("user", "问题1"), makeMsg("assistant", "答案1"),
                makeMsg("user", "问题2"), makeMsg("assistant", "答案2"),
                makeMsg("user", "问题3"), makeMsg("assistant", "答案3"),
                makeMsg("user", "问题4"), makeMsg("assistant", "答案4")
        );
        when(chatMessageDAO.listBySessionIdOrderBySortOrder(eq("session1"), anyInt())).thenReturn(msgs);

        AgentContext context = makeContext();
        middleware.before(context);

        // maxHistoryTurns=5，历史 8 条全部注入
        assertThat(context.getMessages()).hasSize(8);
        assertThat(context.getMessages().get(0)).isInstanceOf(UserMessage.class);
        assertThat(context.getMessages().get(1)).isInstanceOf(AssistantMessage.class);
    }

    // ──────────────────────────────────────────────
    // 场景 3：摘要模式 — 历史轮次 > summarizeWhenTurnsOver，早期压缩为摘要
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景3: 历史轮次 > 阈值时，早期轮次压缩为摘要 SystemMessage，近期原样保留")
    void summaryModeInjectsSummaryPlusRecentTurns() {
        // maxHistoryTurns=5, summarizeWhenTurnsOver=5 → 超过 10 条触发摘要
        // 构造 14 条（7 轮）消息
        List<ChatMessage> msgs = IntStream.range(1, 8)
                .boxed()
                .flatMap(i -> List.of(
                        makeMsg("user", "问题" + i),
                        makeMsg("assistant", "答案" + i)).stream())
                .collect(Collectors.toList());

        when(chatMessageDAO.listBySessionIdOrderBySortOrder(eq("session1"), anyInt())).thenReturn(msgs);
        ReflectionTestUtils.setField(middleware, "conversationSummarizer", conversationSummarizer);
        when(conversationSummarizer.summarize(any())).thenReturn("历史摘要内容");

        AgentContext context = makeContext();
        middleware.before(context);

        // 应包含一条摘要 SystemMessage
        boolean hasSummary = context.getMessages().stream()
                .anyMatch(m -> m instanceof SystemMessage
                        && ((SystemMessage) m).getText().startsWith("[此前对话摘要]"));
        assertThat(hasSummary).isTrue();

        // 近期轮次（summarizeWhenTurnsOver * 2 = 10 条）也要保留
        long recentCount = context.getMessages().stream()
                .filter(m -> !(m instanceof SystemMessage)).count();
        assertThat(recentCount).isEqualTo(10);
    }
}
