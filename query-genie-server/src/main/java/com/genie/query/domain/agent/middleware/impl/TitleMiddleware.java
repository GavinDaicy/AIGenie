package com.genie.query.domain.agent.middleware.impl;

import com.genie.query.application.SessionApplication;
import com.genie.query.domain.agent.middleware.AgentMiddleware;
import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.agent.orchestration.AgentResult;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 标题生成中间件：首轮对话完成后自动生成会话标题。
 *
 * <p>仅在 result 有最终答案（非 askUser 暂停）且 DB 中无历史消息时触发（首轮判断）。
 * 标题失败不影响主流程。
 *
 * <p>ORDER = 200：最后执行，确保 ReAct 循环和其他中间件的后置处理先完成。
 *
 * @author daicy
 */
@Component
public class TitleMiddleware implements AgentMiddleware {

    public static final int ORDER = 200;

    private static final Logger log = LoggerFactory.getLogger(TitleMiddleware.class);

    @Autowired(required = false)
    private SessionApplication sessionApplication;

    @Autowired(required = false)
    private ChatMessageDAO chatMessageDAO;

    @Override
    public String getName() { return "TitleMiddleware"; }

    @Override
    public int getOrder() { return ORDER; }

    @Override
    public void before(AgentContext context) {}

    @Override
    public void after(AgentContext context, AgentResult result) {
        if (result == null || !result.hasFinalAnswer()) return;
        String sessionId = context.getSessionId();
        if (sessionId == null || sessionApplication == null) return;

        try {
            // 首轮判断：after() 在消息持久化前执行，此时 DB 中尚无本轮消息，count=0 表示首轮
            int existingCount = chatMessageDAO != null ? chatMessageDAO.countBySessionId(sessionId) : 0;
            if (existingCount == 0) {
                String question = context.getOriginalQuestion();
                String title = question != null && question.length() > 30
                        ? question.substring(0, 30) + "..." : question;
                sessionApplication.updateSessionTitle(sessionId, title);
                log.info("[TitleMiddleware] 首轮对话，已更新会话标题 | sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.warn("[TitleMiddleware] 标题生成失败，不影响主流程 | error={}", e.getMessage());
        }
    }
}
