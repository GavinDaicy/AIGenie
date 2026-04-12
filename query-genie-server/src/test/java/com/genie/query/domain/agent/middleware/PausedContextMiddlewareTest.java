package com.genie.query.domain.agent.middleware;

import com.genie.query.domain.agent.middleware.impl.PausedContextMiddleware;
import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.cache.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PausedContextMiddleware 单元测试：覆盖有/无暂停上下文及 onAskUserPause 写入三个场景。
 */
class PausedContextMiddlewareTest {

    private static final String PAUSED_KEY_PREFIX = "agent:paused_ctx:";

    private PausedContextMiddleware middleware;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        middleware = new PausedContextMiddleware();
        cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(middleware, "cacheService", cacheService);
    }

    private AgentContext makeContext(String sessionId) {
        return new AgentContext(sessionId, "question", List.of(), List.of(), null);
    }

    // ──────────────────────────────────────────────
    // 场景 1：Redis 有暂停上下文 → 注入 SystemMessage 并删除 key
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景1: Redis 存在暂停上下文时，注入 SystemMessage 且删除 Redis key")
    void beforeInjectsPausedHintWhenPresent() {
        String key = PAUSED_KEY_PREFIX + "s1";
        when(cacheService.get(key)).thenReturn("工具A结果：xxx");

        AgentContext context = makeContext("s1");
        middleware.before(context);

        assertThat(context.getMessages()).hasSize(1);
        assertThat(context.getMessages().get(0)).isInstanceOf(SystemMessage.class);
        String text = ((SystemMessage) context.getMessages().get(0)).getText();
        assertThat(text).contains("工具A结果：xxx");

        verify(cacheService).delete(key);
    }

    // ──────────────────────────────────────────────
    // 场景 2：Redis 无暂停上下文 → 不注入任何消息
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景2: Redis 无暂停上下文时，不向 context 添加任何消息")
    void beforeDoesNothingWhenNoPausedHint() {
        when(cacheService.get(anyString())).thenReturn(null);

        AgentContext context = makeContext("s2");
        middleware.before(context);

        assertThat(context.getMessages()).isEmpty();
        verify(cacheService, never()).delete(anyString());
    }

    // ──────────────────────────────────────────────
    // 场景 3：onAskUserPause → 写入 Redis（带 TTL 10 分钟）
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景3: onAskUserPause() 将工具结果写入 Redis，TTL=10分钟")
    void onAskUserPauseWritesHintToRedis() {
        AgentContext context = makeContext("s3");
        middleware.onAskUserPause(context, "工具B结果：yyy");

        verify(cacheService).set(
                eq(PAUSED_KEY_PREFIX + "s3"),
                eq("工具B结果：yyy"),
                eq(10L),
                eq(TimeUnit.MINUTES));
    }
}
