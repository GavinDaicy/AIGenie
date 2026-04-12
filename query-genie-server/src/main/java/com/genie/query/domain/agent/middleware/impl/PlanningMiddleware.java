package com.genie.query.domain.agent.middleware.impl;

import com.genie.query.domain.agent.event.StepEvent;
import com.genie.query.domain.agent.event.StepEventPublisher;
import com.genie.query.domain.agent.middleware.AgentMiddleware;
import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.agent.orchestration.AgentResult;
import com.genie.query.domain.agent.planning.ExecutionPlan;
import com.genie.query.domain.agent.planning.PlannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务规划中间件：在 ReAct 开始前生成执行计划或多子问题清单并注入到上下文。
 *
 * <p>优先尝试 LLM 智能规划（依赖链场景），失败时 fallback 到多子问题清单。
 * 通过 {@code context.getWriter()} 推送 PLANNING SSE 事件。
 *
 * <p>ORDER = 30：在历史注入（10）和暂停上下文（20）之后、UserMessage 之前执行。
 *
 * @author daicy
 */
@Component
public class PlanningMiddleware implements AgentMiddleware {

    public static final int ORDER = 30;

    private static final Logger log = LoggerFactory.getLogger(PlanningMiddleware.class);

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private StepEventPublisher stepEventPublisher;

    @Override
    public String getName() { return "PlanningMiddleware"; }

    @Override
    public int getOrder() { return ORDER; }

    @Override
    public void before(AgentContext context) {
        String question = context.getOriginalQuestion();
        List<String> knowledgeCodes = context.getKnowledgeCodes();
        List<Long> datasourceIds = context.getDatasourceIds();
        String sessionId = context.getSessionId();

        String contextHint = buildContextHint(question, knowledgeCodes, datasourceIds, sessionId, context);
        if (contextHint != null) {
            context.addMessage(new SystemMessage(contextHint));
        }
    }

    @Override
    public void after(AgentContext context, AgentResult result) {}

    private String buildContextHint(String question, List<String> knowledgeCodes,
                                    List<Long> datasourceIds, String sessionId,
                                    AgentContext context) {
        // 优先：LLM 智能规划（依赖链场景）
        try {
            stepEventPublisher.publish(context.getWriter(), StepEvent.planning("正在分析问题，生成执行计划…"));
            ExecutionPlan plan = plannerService.plan(question, knowledgeCodes, datasourceIds);
            if (plan != null) {
                log.info("[PlanningMiddleware] 已生成执行计划 | steps={} | sessionId={}",
                        plan.getSteps().size(), sessionId);
                stepEventPublisher.publish(context.getWriter(),
                        StepEvent.planning("已生成 " + plan.getSteps().size() + " 步执行计划"));
                return plan.toTaskListHint();
            }
        } catch (Exception e) {
            log.warn("[PlanningMiddleware] 执行计划生成失败，降级到多子问题清单 | error={}", e.getMessage());
        }
        // Fallback：多子问题并列清单（并列独立子问题场景）
        String taskList = buildSubQuestionTaskList(question);
        if (taskList != null) {
            log.info("[PlanningMiddleware] 检测到多子问题，已注入任务清单 | sessionId={}", sessionId);
            stepEventPublisher.publish(context.getWriter(),
                    StepEvent.planning("检测到多子问题，将逐一处理"));
        } else {
            stepEventPublisher.publish(context.getWriter(), StepEvent.planning("无需额外规划，直接开始推理"));
        }
        return taskList;
    }

    private String buildSubQuestionTaskList(String question) {
        String[] parts = question.split("[？?]");
        List<String> subQuestions = Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (subQuestions.size() < 2) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【多子问题任务清单】用户共提出 ")
                .append(subQuestions.size()).append(" 个问题，你必须逐一调用工具处理每个问题，全部有答案后才能输出最终结论：\n");
        for (int i = 0; i < subQuestions.size(); i++) {
            sb.append(i + 1).append(". ").append(subQuestions.get(i)).append("？\n");
        }
        sb.append("\n每轮思考后，请检查以上清单中哪些还没有工具返回的答案，继续调用工具直到全部完成。");
        return sb.toString();
    }
}
