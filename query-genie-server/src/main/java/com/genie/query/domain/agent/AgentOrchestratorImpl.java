package com.genie.query.domain.agent;

import com.genie.query.domain.agent.dao.AgentStepLogDAO;
import com.genie.query.domain.agent.model.AgentStepLog;
import com.genie.query.domain.agent.sql.SqlQueryTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 编排引擎实现：基于 Spring AI Tool Calling 驱动 ReAct 循环。
 *
 * <p>执行流程：
 * <pre>
 * execute(question, sessionId, emitter)
 *   → 构建 System Prompt（含工具描述）
 *   → 初始化 AgentContext（消息历史 + 工具 Callback）
 *   → ReAct 循环（最多 maxIterations 轮）：
 *       → 调用 LLM（携带工具定义）
 *       → 发布 THOUGHT 事件
 *       → 若有工具调用：执行工具 → 发布 TOOL_CALL/TOOL_RESULT 事件
 *       → 若无工具调用：发布 FINAL_ANSWER 事件 → 结束
 *   → 发送 [DONE] 标记
 * </pre>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class AgentOrchestratorImpl implements AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorImpl.class);

    private static final String SYSTEM_PROMPT_TEMPLATE =
            "你是一个企业智能助手。请根据用户的问题，选择合适的工具来获取信息并给出准确答案。\n\n" +
            "工具选择规则：\n" +
            "1. 需要统计数量、排名、比较数值、查询历史记录、聚合汇总等问题 → 使用 querySql 工具（含\"多少\"\"最多\"\"最少\"\"统计\"\"排行\"等词）\n" +
            "2. 查找产品说明、操作规范、概念解释、文档内容等知识型问题 → 使用 searchKnowledge 工具\n" +
            "3. 复杂问题可先后使用多个工具，每次工具调用都应针对具体子问题\n\n" +
            "强制规则：\n" +
            "- 所有数字结论必须来自工具执行结果，禁止猜测或估算\n" +
            "- 若某个工具的返回内容无法直接回答问题（如搜索结果未包含所需数量/统计数据），立即尝试改用其他工具，尽量不要在信息不完整时直接输出答案\n" +
            "- 只有在工具结果已足够完整回答问题后，才能输出最终答案\n\n" +
            "当前可用数据源 ID：%s\n" +
            "当前可用知识库：%s";

    @Value("${app.agent.max-iterations:8}")
    private int maxIterations;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private SqlQueryTool sqlQueryTool;

    @Autowired
    private RagSearchTool ragSearchTool;

    @Autowired
    private StepEventPublisher stepEventPublisher;

    @Autowired
    private ContextWindowManager contextWindowManager;

    @Autowired(required = false)
    private AgentStepLogDAO agentStepLogDAO;

    @Override
    public String execute(String question, String sessionId,
                          List<String> knowledgeCodes, List<Long> datasourceIds,
                          SseEmitter emitter) {

        AgentContext context = new AgentContext(sessionId, question, knowledgeCodes, datasourceIds);
        log.info("[AgentOrchestrator] 开始执行 | sessionId={} | question={}", sessionId, question);
        String finalAnswer = null;

        try {
            // 初始化工具 Callback
            ToolCallback[] toolCallbacks = ToolCallbacks.from(sqlQueryTool, ragSearchTool);
            Map<String, ToolCallback> callbackMap = Arrays.stream(toolCallbacks)
                    .collect(Collectors.toMap(cb -> cb.getToolDefinition().name(), cb -> cb));

            // 初始化消息历史
            String systemPrompt = buildSystemPrompt(datasourceIds, knowledgeCodes);
            context.addMessage(new SystemMessage(systemPrompt));
            context.addMessage(new UserMessage(question));

            // ReAct 主循环
            for (int i = 1; i <= maxIterations; i++) {
                context.incrementIteration();

                // 检查是否需要上下文压缩
                if (contextWindowManager.shouldCompress(context)) {
                    contextWindowManager.compress(context, chatModel);
                }

                // 调用 LLM（携带工具定义）
                Prompt prompt = new Prompt(context.getMessages(),
                        ToolCallingChatOptions.builder()
                                .toolCallbacks(Arrays.asList(toolCallbacks))
                                .internalToolExecutionEnabled(false)
                                .build());

                ChatResponse response = chatModel.call(prompt);
                AssistantMessage assistantMsg = response.getResult().getOutput();

                // 提取思考文本并推送 THOUGHT 事件
                String thought = assistantMsg.getText();
                if (thought != null && !thought.isBlank()) {
                    StepEvent thoughtEvent = StepEvent.thought(i, thought);
                    stepEventPublisher.publish(emitter, thoughtEvent);
                    persistStepAsync(sessionId, i, "THOUGHT", null, thought, 0);
                }

                // 检查是否有工具调用
                List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                if (toolCalls == null || toolCalls.isEmpty()) {
                    // 无工具调用 → LLM 的回答即为最终答案
                    log.info("[AgentOrchestrator] 无工具调用，输出最终答案 | iteration={}", i);
                    finalAnswer = thought;
                    StepEvent finalEvent = StepEvent.finalAnswer(thought);
                    stepEventPublisher.publish(emitter, finalEvent);
                    persistStepAsync(sessionId, i, "FINAL_ANSWER", null, thought, 0);
                    break;
                }

                // 将 Assistant 消息加入历史
                context.addMessage(assistantMsg);

                // 依次执行每个工具调用
                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : toolCalls) {
                    String toolName = toolCall.name();
                    String toolArgs = toolCall.arguments();

                    // 推送 TOOL_CALL 事件
                    StepEvent callEvent = StepEvent.toolCall(i, toolName, toolArgs);
                    stepEventPublisher.publish(emitter, callEvent);
                    persistStepAsync(sessionId, i, "TOOL_CALL", toolName, toolArgs, 0);

                    // 执行工具
                    long startMs = System.currentTimeMillis();
                    String toolResult;
                    try {
                        ToolCallback callback = callbackMap.get(toolName);
                        if (callback == null) {
                            toolResult = "工具 [" + toolName + "] 未注册，无法执行";
                        } else {
                            toolResult = callback.call(toolArgs);
                        }
                    } catch (Exception e) {
                        log.warn("[AgentOrchestrator] 工具执行异常 | tool={} | error={}", toolName, e.getMessage());
                        toolResult = "工具执行失败: " + e.getMessage();
                    }
                    long durationMs = System.currentTimeMillis() - startMs;

                    // 推送 TOOL_RESULT 事件
                    StepEvent resultEvent = StepEvent.toolResult(i, toolName, toolResult, durationMs);
                    stepEventPublisher.publish(emitter, resultEvent);
                    persistStepAsync(sessionId, i, "TOOL_RESULT", toolName, toolResult, (int) durationMs);

                    context.incrementToolCallCount();
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolName, toolResult));
                }

                // 将工具结果加入消息历史
                context.addMessage(ToolResponseMessage.builder()
                        .responses(toolResponses).build());

                // 若已达最大迭代次数，强制结束
                if (i == maxIterations) {
                    log.warn("[AgentOrchestrator] 达到最大迭代次数 {}", maxIterations);
                    String errMsg = "已达到最大推理轮次（" + maxIterations + "轮），请尝试更具体的问题描述。";
                    stepEventPublisher.publish(emitter, StepEvent.error(errMsg));
                }
            }

            stepEventPublisher.sendDone(emitter);

        } catch (Exception e) {
            log.error("[AgentOrchestrator] 执行异常 | sessionId={} | error={}", sessionId, e.getMessage(), e);
            stepEventPublisher.publish(emitter, StepEvent.error("Agent执行异常: " + e.getMessage()));
            stepEventPublisher.sendDone(emitter);
        }
        return finalAnswer;
    }

    private String buildSystemPrompt(List<Long> datasourceIds, List<String> knowledgeCodes) {
        String dsIds = datasourceIds == null || datasourceIds.isEmpty()
                ? "（全部）" : datasourceIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String kbCodes = knowledgeCodes == null || knowledgeCodes.isEmpty()
                ? "（全部）" : String.join(", ", knowledgeCodes);
        return String.format(SYSTEM_PROMPT_TEMPLATE, dsIds, kbCodes);
    }

    @Async
    protected void persistStepAsync(String sessionId, int iteration, String stepType,
                                    String toolName, String content, int durationMs) {
        if (agentStepLogDAO == null) return;
        try {
            AgentStepLog log = new AgentStepLog();
            log.setSessionId(sessionId);
            log.setIteration(iteration);
            log.setStepType(stepType);
            log.setToolName(toolName);
            log.setContent(content != null && content.length() > 4000
                    ? content.substring(0, 4000) + "..." : content);
            log.setDurationMs(durationMs);
            agentStepLogDAO.insert(log);
        } catch (Exception ex) {
            AgentOrchestratorImpl.log.warn("[AgentOrchestrator] 步骤持久化失败: {}", ex.getMessage());
        }
    }
}
