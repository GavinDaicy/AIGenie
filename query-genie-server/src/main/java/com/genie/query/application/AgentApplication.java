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

    @Autowired(required = false)
    private SessionApplication sessionApplication;

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
     * @param emitter SSE 推送器（由 Controller 层创建并传入）
     */
    public void handleQuestion(AgentAskRequest request, PrintWriter writer) {
        String question = request.getQuestion();
        String sessionId = request.getSessionId();
        List<String> knowledgeCodes = request.getKnowledgeCodes();
        List<Long> datasourceIds = request.getDatasourceIds();
        AgentAskRequest.ToolForce toolForce = request.getToolForce();

        // null代表查全部，empty代表不查询，前端传空，默认查全部，禁用是由后端处理的规则
        knowledgeCodes = CollectionUtils.isEmpty(knowledgeCodes) ? null : knowledgeCodes;
        datasourceIds = CollectionUtils.isEmpty(datasourceIds) ? null : datasourceIds;

        // 语义路由分类：先推送"识别中"状态
        stepEventPublisher.publish(writer, StepEvent.planning("正在识别问题意图…"));
        QuestionType questionType;
        try {
            questionType = semanticRouter.route(question);
            log.info("[AgentApplication] 路由结果 | type={} | question={}", questionType, question);
        } catch (Exception e) {
            log.warn("[AgentApplication] 语义路由异常，兜底 COMPLEX | error={}", e.getMessage());
            questionType = QuestionType.COMPLEX;
        }
        // 推送意图识别结果
        stepEventPublisher.publish(writer, StepEvent.routing(questionType.name(), buildRoutingLabel(questionType)));

        // 根据路由结果调整工具配置
        List<Long> effectiveDatasourceIds = datasourceIds;
        List<String> effectiveKnowledgeCodes = knowledgeCodes;

        if (questionType == QuestionType.KNOWLEDGE) {
            effectiveDatasourceIds = List.of(); // KNOWLEDGE 路由不开放数据源
        } else if (questionType == QuestionType.DATA_QUERY) {
            effectiveKnowledgeCodes = List.of(); // DATA_QUERY 路由不开放知识库
        }

        // toolForce 覆盖（优先级高于路由结果）
        if (toolForce != null) {
            if (Boolean.FALSE.equals(toolForce.getSql())) {
                // 强制禁用 SQL：清空数据源列表
                effectiveDatasourceIds = List.of();
            } else if (Boolean.TRUE.equals(toolForce.getSql()) && CollectionUtils.isEmpty(effectiveDatasourceIds)) {
                // 强制启用 SQL：路由屏蔽了数据源时恢复为 null（使用全部）
                effectiveDatasourceIds = null;
            }
            if (Boolean.FALSE.equals(toolForce.getKnowledge())) {
                // 强制禁用知识库：清空知识库列表
                effectiveKnowledgeCodes = List.of();
            } else if (Boolean.TRUE.equals(toolForce.getKnowledge()) && CollectionUtils.isEmpty(effectiveKnowledgeCodes)) {
                // 强制启用知识库：路由屏蔽了知识库时恢复为 null（使用全部）
                effectiveKnowledgeCodes = null;
            }
            log.info("[AgentApplication] toolForce 覆盖已应用 | web={} knowledge={} sql={}",
                    toolForce.getWebSearch(), toolForce.getKnowledge(), toolForce.getSql());
        }

        // 执行 Agent
        AgentResult agentResult = null;
        try {
            agentResult = agentOrchestrator.execute(
                    question, sessionId,
                    effectiveKnowledgeCodes, effectiveDatasourceIds,
                    toolForce,
                    writer);
        } catch (Exception e) {
            log.error("[AgentApplication] Agent执行异常 | error={}", e.getMessage(), e);
            stepEventPublisher.publish(writer, StepEvent.error("系统错误: " + e.getMessage()));
            stepEventPublisher.sendDone(writer);
        }
        String finalAnswer = agentResult != null ? agentResult.getFinalAnswer() : null;

        // 持久化用户消息（无论是否有最终答案，user 消息均落库）
        if (sessionId != null && chatMessageDAO != null) {
            try {
                int nextOrder = chatMessageDAO.countBySessionId(sessionId);

                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(sessionId);
                userMsg.setRole("user");
                userMsg.setContent(question);
                userMsg.setSortOrder(nextOrder);
                chatMessageDAO.insert(userMsg);

                // 首轮对话更新会话标题
                if (nextOrder == 0 && sessionApplication != null) {
                    String title = question.length() > 30 ? question.substring(0, 30) + "..." : question;
                    sessionApplication.updateSessionTitle(sessionId, title);
                }

                // 有最终答案时才持久化 assistant 消息（追问场景 finalAnswer 为 null）
                if (finalAnswer != null) {
                    ChatMessage assistantMsg = new ChatMessage();
                    assistantMsg.setSessionId(sessionId);
                    assistantMsg.setRole("assistant");
                    assistantMsg.setContent(finalAnswer);
                    assistantMsg.setSortOrder(nextOrder + 1);
                    assistantMsg.setSources("[]");
                    if (agentResult != null && !agentResult.getCitations().isEmpty()) {
                        try {
                            assistantMsg.setCitationsJson(
                                    objectMapper.writeValueAsString(agentResult.getCitations()));
                        } catch (Exception jsonEx) {
                            log.warn("[AgentApplication] citations 序列化失败 | error={}", jsonEx.getMessage());
                        }
                    }
                    chatMessageDAO.insert(assistantMsg);
                    log.info("[AgentApplication] 对话已落库 | sessionId={}", sessionId);
                } else {
                    log.info("[AgentApplication] Agent 暂停（追问），仅落库 user 消息 | sessionId={}", sessionId);
                }
            } catch (Exception e) {
                log.warn("[AgentApplication] 对话落库失败 | error={}", e.getMessage());
            }
        }
    }
}
