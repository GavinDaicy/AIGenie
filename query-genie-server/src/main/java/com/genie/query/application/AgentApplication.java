package com.genie.query.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.controller.dto.AgentAskRequest;
import com.genie.query.domain.agent.orchestration.AgentOrchestrator;
import com.genie.query.domain.agent.orchestration.AgentResult;
import com.genie.query.domain.agent.routing.QuestionType;
import com.genie.query.domain.agent.routing.SemanticRouter;
import com.genie.query.domain.agent.event.StepEvent;
import com.genie.query.domain.agent.event.StepEventPublisher;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.PrintWriter;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String buildRoutingLabel(QuestionType type) {
        switch (type) {
            case DATA_QUERY: return "数据查询";
            case KNOWLEDGE:  return "知识问答";
            default:         return "综合推理";
        }
    }

    /**
     * 处理 Agent 问答请求，通过 SSE 实时推送推理步骤与最终答案。
     *
     * @param request 用户请求（含问题、会话ID、可用知识库和数据源）
     * @param writer  SSE 推送器（由 Controller 层创建并传入）
     */
    public void handleQuestion(AgentAskRequest request, PrintWriter writer) {
        String question = request.getQuestion();
        String sessionId = request.getSessionId();
        List<String> knowledgeCodes = CollectionUtils.isEmpty(request.getKnowledgeCodes()) ? null : request.getKnowledgeCodes();
        List<Long> datasourceIds = CollectionUtils.isEmpty(request.getDatasourceIds()) ? null : request.getDatasourceIds();
        AgentAskRequest.ToolForce toolForce = request.getToolForce();

        QuestionType questionType = routeQuestion(question, writer);
        EffectiveTools tools = resolveEffectiveTools(questionType, datasourceIds, knowledgeCodes, toolForce);
        AgentResult agentResult = executeAgent(question, sessionId, tools, toolForce, writer);
        persistConversation(sessionId, question, agentResult);
    }

    private QuestionType routeQuestion(String question, PrintWriter writer) {
        stepEventPublisher.publish(writer, StepEvent.planning("正在识别问题意图…"));
        QuestionType type;
        try {
            type = semanticRouter.route(question);
            log.info("[AgentApplication] 路由结果 | type={} | question={}", type, question);
        } catch (Exception e) {
            log.warn("[AgentApplication] 语义路由异常，兜底 COMPLEX | error={}", e.getMessage());
            type = QuestionType.COMPLEX;
        }
        stepEventPublisher.publish(writer, StepEvent.routing(type.name(), buildRoutingLabel(type)));
        return type;
    }

    private EffectiveTools resolveEffectiveTools(QuestionType questionType,
                                                 List<Long> datasourceIds,
                                                 List<String> knowledgeCodes,
                                                 AgentAskRequest.ToolForce toolForce) {
        List<Long> effectiveDatasourceIds = datasourceIds;
        List<String> effectiveKnowledgeCodes = knowledgeCodes;

        if (questionType == QuestionType.KNOWLEDGE) {
            effectiveDatasourceIds = List.of(); // KNOWLEDGE 路由不开放数据源
        } else if (questionType == QuestionType.DATA_QUERY) {
            effectiveKnowledgeCodes = List.of(); // DATA_QUERY 路由不开放知识库
        }

        if (toolForce != null) {
            if (Boolean.FALSE.equals(toolForce.getSql())) {
                effectiveDatasourceIds = List.of();
            } else if (Boolean.TRUE.equals(toolForce.getSql()) && CollectionUtils.isEmpty(effectiveDatasourceIds)) {
                effectiveDatasourceIds = null; // 强制启用：路由屏蔽时恢复使用全部
            }
            if (Boolean.FALSE.equals(toolForce.getKnowledge())) {
                effectiveKnowledgeCodes = List.of();
            } else if (Boolean.TRUE.equals(toolForce.getKnowledge()) && CollectionUtils.isEmpty(effectiveKnowledgeCodes)) {
                effectiveKnowledgeCodes = null; // 强制启用：路由屏蔽时恢复使用全部
            }
            log.info("[AgentApplication] toolForce 覆盖已应用 | web={} knowledge={} sql={}",
                    toolForce.getWebSearch(), toolForce.getKnowledge(), toolForce.getSql());
        }

        return new EffectiveTools(effectiveKnowledgeCodes, effectiveDatasourceIds);
    }

    private AgentResult executeAgent(String question, String sessionId,
                                     EffectiveTools tools,
                                     AgentAskRequest.ToolForce toolForce,
                                     PrintWriter writer) {
        try {
            return agentOrchestrator.execute(
                    question, sessionId,
                    tools.knowledgeCodes, tools.datasourceIds,
                    toolForce, writer);
        } catch (Exception e) {
            log.error("[AgentApplication] Agent执行异常 | error={}", e.getMessage(), e);
            stepEventPublisher.publish(writer, StepEvent.error("系统错误: " + e.getMessage()));
            stepEventPublisher.sendDone(writer);
            return null;
        }
    }

    private void persistConversation(String sessionId, String question, AgentResult agentResult) {
        if (sessionId == null || chatMessageDAO == null) return;
        try {
            int nextOrder = chatMessageDAO.countBySessionId(sessionId);
            persistUserMessage(sessionId, question, nextOrder);
            String finalAnswer = agentResult != null ? agentResult.getFinalAnswer() : null;
            if (finalAnswer != null) {
                persistAssistantMessage(sessionId, finalAnswer, agentResult, nextOrder + 1);
                log.info("[AgentApplication] 对话已落库 | sessionId={}", sessionId);
            } else {
                log.info("[AgentApplication] Agent 暂停（追问），仅落库 user 消息 | sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.warn("[AgentApplication] 对话落库失败 | error={}", e.getMessage());
        }
    }

    private void persistUserMessage(String sessionId, String question, int sortOrder) {
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        userMsg.setSortOrder(sortOrder);
        chatMessageDAO.insert(userMsg);
    }

    private void persistAssistantMessage(String sessionId, String finalAnswer,
                                         AgentResult agentResult, int sortOrder) {
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(finalAnswer);
        assistantMsg.setSortOrder(sortOrder);
        assistantMsg.setSources("[]");
        if (!agentResult.getCitations().isEmpty()) {
            try {
                assistantMsg.setCitationsJson(objectMapper.writeValueAsString(agentResult.getCitations()));
            } catch (Exception jsonEx) {
                log.warn("[AgentApplication] citations 序列化失败 | error={}", jsonEx.getMessage());
            }
        }
        chatMessageDAO.insert(assistantMsg);
    }

    private static final class EffectiveTools {
        final List<String> knowledgeCodes;
        final List<Long> datasourceIds;

        EffectiveTools(List<String> knowledgeCodes, List<Long> datasourceIds) {
            this.knowledgeCodes = knowledgeCodes;
            this.datasourceIds = datasourceIds;
        }
    }
}
