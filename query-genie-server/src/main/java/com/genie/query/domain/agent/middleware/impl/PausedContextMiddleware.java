package com.genie.query.domain.agent.middleware.impl;

import com.genie.query.domain.agent.middleware.AgentMiddleware;
import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.agent.orchestration.AgentResult;
import com.genie.query.domain.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * askUser 暂停上下文中间件：
 * <ul>
 *   <li>before()：从 Redis 读取暂停上下文并注入到 AgentContext（一次性消费，读后删除）</li>
 *   <li>onAskUserPause()：将本轮工具结果写入 Redis（TTL 10分钟），替代编排器直接操作 Redis</li>
 * </ul>
 *
 * <p>ORDER = 20：在历史注入（ORDER=10）之后执行，确保暂停上下文在历史消息之后注入。
 *
 * @author daicy
 */
@Component
public class PausedContextMiddleware implements AgentMiddleware {

    public static final int ORDER = 20;

    private static final Logger log = LoggerFactory.getLogger(PausedContextMiddleware.class);

    private static final String PAUSED_CTX_KEY_PREFIX = "agent:paused_ctx:";
    private static final long PAUSED_CTX_TTL_MINUTES = 10L;

    @Autowired
    private CacheService cacheService;

    @Override
    public String getName() { return "PausedContextMiddleware"; }

    @Override
    public int getOrder() { return ORDER; }

    @Override
    public void before(AgentContext context) {
        String sessionId = context.getSessionId();
        if (sessionId == null) return;
        String key = PAUSED_CTX_KEY_PREFIX + sessionId;
        String hint = cacheService.get(key);
        if (hint != null) {
            cacheService.delete(key);
            log.info("[PausedContextMiddleware] 检测到 askUser 续跑上下文 | sessionId={}", sessionId);
            context.addMessage(new SystemMessage(
                    "[续跑上下文] 用户回复追问前，本轮已执行以下工具并得到结果，请勿重复执行：\n" + hint));
        }
    }

    @Override
    public void after(AgentContext context, AgentResult result) {}

    @Override
    public void onAskUserPause(AgentContext context, String toolResultsHint) {
        if (toolResultsHint == null || toolResultsHint.isBlank()) return;
        String sessionId = context.getSessionId();
        if (sessionId == null) return;
        cacheService.set(
                PAUSED_CTX_KEY_PREFIX + sessionId,
                toolResultsHint,
                PAUSED_CTX_TTL_MINUTES,
                TimeUnit.MINUTES);
        log.debug("[PausedContextMiddleware] 已保存 askUser 暂停上下文到 Redis | sessionId={}", sessionId);
    }
}
