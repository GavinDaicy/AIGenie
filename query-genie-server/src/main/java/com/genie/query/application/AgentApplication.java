package com.genie.query.application;

import com.genie.query.controller.dto.AgentAskRequest;
import com.genie.query.domain.agent.AgentOrchestrator;
import com.genie.query.domain.agent.QuestionType;
import com.genie.query.domain.agent.SemanticRouter;
import com.genie.query.domain.agent.StepEvent;
import com.genie.query.domain.agent.StepEventPublisher;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Agent 问答应用层：编排语义路由与 Agent 执行，将用户问题路由到合适的处理链路。
 *
 * <p>路由策略：
 * <ul>
 *   <li>KNOWLEDGE  → 直接走 RAG 检索答案（跳过 Agent 多轮规划，降低延迟）</li>
 *   <li>DATA_QUERY → 直接走 SQL Agent（跳过 RAG 工具，节省无效检索）</li>
 *   <li>COMPLEX    → 完整 ReAct Agent（多工具协同）</li>
 * </ul>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class AgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentApplication.class);

    @Autowired
    private SemanticRouter semanticRouter;

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private StepEventPublisher stepEventPublisher;

    @Autowired(required = false)
    private ChatMessageDAO chatMessageDAO;

    @Autowired(required = false)
    private SessionApplication sessionApplication;

    /**
     * 处理 Agent 问答请求，通过 SSE 实时推送推理步骤与最终答案。
     *
     * @param request 用户请求（含问题、会话ID、可用知识库和数据源）
     * @param emitter SSE 推送器（由 Controller 层创建并传入）
     */
    public void handleQuestion(AgentAskRequest request, SseEmitter emitter) {
        String question = request.getQuestion();
        String sessionId = request.getSessionId();
        List<String> knowledgeCodes = request.getKnowledgeCodes();
        List<Long> datasourceIds = request.getDatasourceIds();

        // 语义路由分类
        QuestionType questionType;
        try {
            questionType = semanticRouter.route(question);
            log.info("[AgentApplication] 路由结果 | type={} | question={}", questionType, question);
        } catch (Exception e) {
            log.warn("[AgentApplication] 语义路由异常，兜底 COMPLEX | error={}", e.getMessage());
            questionType = QuestionType.COMPLEX;
        }

        // 根据路由结果调整工具配置
        List<Long> effectiveDatasourceIds = datasourceIds;
        List<String> effectiveKnowledgeCodes = knowledgeCodes;

        if (questionType == QuestionType.KNOWLEDGE) {
            effectiveDatasourceIds = List.of(); // KNOWLEDGE 路由不开放数据源
        } else if (questionType == QuestionType.DATA_QUERY) {
            effectiveKnowledgeCodes = List.of(); // DATA_QUERY 路由不开放知识库
        }

        // 执行 Agent
        String finalAnswer = null;
        try {
            finalAnswer = agentOrchestrator.execute(
                    question, sessionId,
                    effectiveKnowledgeCodes, effectiveDatasourceIds,
                    emitter);
        } catch (Exception e) {
            log.error("[AgentApplication] Agent执行异常 | error={}", e.getMessage(), e);
            stepEventPublisher.publish(emitter, StepEvent.error("系统错误: " + e.getMessage()));
            stepEventPublisher.sendDone(emitter);
        }

        // 持久化对话记录（同 QaApplication.persistChatTurnIfNeeded 逻辑）
        if (sessionId != null && finalAnswer != null && chatMessageDAO != null) {
            try {
                int nextOrder = chatMessageDAO.countBySessionId(sessionId);

                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(sessionId);
                userMsg.setRole("user");
                userMsg.setContent(question);
                userMsg.setSortOrder(nextOrder);
                chatMessageDAO.insert(userMsg);

                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(sessionId);
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(finalAnswer);
                assistantMsg.setSortOrder(nextOrder + 1);
                assistantMsg.setSources("[]");
                chatMessageDAO.insert(assistantMsg);

                if (nextOrder == 0 && sessionApplication != null) {
                    String title = question.length() > 30 ? question.substring(0, 30) + "..." : question;
                    sessionApplication.updateSessionTitle(sessionId, title);
                }
                log.info("[AgentApplication] 对话已落库 | sessionId={}", sessionId);
            } catch (Exception e) {
                log.warn("[AgentApplication] 对话落库失败 | error={}", e.getMessage());
            }
        }
    }
}
