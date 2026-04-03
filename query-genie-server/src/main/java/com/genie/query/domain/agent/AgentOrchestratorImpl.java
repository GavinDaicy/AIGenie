package com.genie.query.domain.agent;

import com.genie.query.domain.agent.dao.AgentStepLogDAO;
import com.genie.query.domain.agent.model.AgentStepLog;
import com.genie.query.domain.agent.sql.SqlQueryTool;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.domain.qa.model.ChatTurn;
import com.genie.query.domain.qa.service.ConversationSummarizer;
import com.genie.query.domain.schema.dao.DbDatasourceDAO;
import com.genie.query.domain.schema.model.DbDatasource;
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
import java.io.PrintWriter;

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
            "多子问题处理规则（重要）：\n" +
            "- 若用户问题包含多个子问题（如含多个\"？\"），必须逐一处理每个子问题\n" +
            "- 每个子问题需单独调用工具获取结果，不得合并跳过任何一个\n" +
            "- 收到工具结果后，主动检查是否还有未解决的子问题，有则继续调用工具\n" +
            "- 只有全部子问题都已获得工具支持的答案后，才能输出最终答案\n" +
            "- 最终答案需分条对应每个子问题\n\n" +
            "强制规则：\n" +
            "- 所有数字结论必须来自工具执行结果，禁止猜测或估算\n" +
            "- 若某个工具的返回内容无法直接回答问题（如搜索结果未包含所需数量/统计数据），立即尝试改用其他工具，尽量不要在信息不完整时直接输出答案\n" +
            "- 只有在工具结果已足够完整回答问题后，才能输出最终答案\n\n" +
            "当前可用数据源 ID：%s\n" +
            "当前可用知识库：%s\n\n" +
            "多数据源规则：若有多个数据源ID，请针对每个相关ID分别调用 querySql 工具，每次只传一个ID";

    @Value("${app.agent.max-iterations:8}")
    private int maxIterations;

    @Value("${app.agent.max-history-turns:5}")
    private int maxHistoryTurns;

    @Value("${app.agent.summarize-when-turns-over:5}")
    private int summarizeWhenTurnsOver;

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

    @Autowired(required = false)
    private DbDatasourceDAO dbDatasourceDAO;

    @Autowired(required = false)
    private ChatMessageDAO chatMessageDAO;

    @Autowired(required = false)
    private ConversationSummarizer conversationSummarizer;

    @Autowired
    private PlannerService plannerService;

    @Override
    public String execute(String question, String sessionId,
                          List<String> knowledgeCodes, List<Long> datasourceIds,
                          PrintWriter writer) {

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

            // 任务规划：优先尝试生成依赖链执行计划，fallback 到多子问题清单
            String contextHint = buildContextHint(question, knowledgeCodes, datasourceIds, sessionId, writer);
            if (contextHint != null) {
                context.addMessage(new SystemMessage(contextHint));
            }

            // 注入会话历史（多轮对话支持）
            injectHistoryIntoContext(context);

            context.addMessage(new UserMessage(question));

            // ReAct 主循环
            for (int i = 1; i <= maxIterations; i++) {
                context.incrementIteration();

                // 检查是否需要上下文压缩
                if (contextWindowManager.shouldCompress(context)) {
                    stepEventPublisher.publish(writer, StepEvent.planning("上下文过长，正在压缩历史记录…"));
                    contextWindowManager.compress(context, chatModel);
                }

                // 推送 THINKING 事件：告知前端 LLM 正在推理
                stepEventPublisher.publish(writer, StepEvent.thinking(i));

                // 调用 LLM（携带工具定义）— 流式输出，降级阻塞
                Prompt prompt = new Prompt(context.getMessages(),
                        ToolCallingChatOptions.builder()
                                .toolCallbacks(Arrays.asList(toolCallbacks))
                                .internalToolExecutionEnabled(false)
                                .build());

                AssistantMessage assistantMsg = streamLlmCall(prompt, writer, i, sessionId);

                // 检查是否有工具调用
                List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                if (toolCalls == null || toolCalls.isEmpty()) {
                    // 无工具调用 → LLM 的回答即为最终答案
                    log.info("[AgentOrchestrator] 无工具调用，输出最终答案 | iteration={}", i);
                    String thought = assistantMsg.getText();
                    finalAnswer = (thought == null || thought.isBlank()) ? "（无结果）" : thought;
                    stepEventPublisher.publish(writer, StepEvent.finalAnswer(finalAnswer));
                    persistStepAsync(sessionId, i, "FINAL_ANSWER", null, finalAnswer, 0);
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
                    stepEventPublisher.publish(writer, callEvent);
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
                    stepEventPublisher.publish(writer, resultEvent);
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
                    stepEventPublisher.publish(writer, StepEvent.error(errMsg));
                }
            }

            stepEventPublisher.sendDone(writer);

        } catch (Exception e) {
            log.error("[AgentOrchestrator] 执行异常 | sessionId={} | error={}", sessionId, e.getMessage(), e);
            stepEventPublisher.publish(writer, StepEvent.error("Agent执行异常: " + e.getMessage()));
            stepEventPublisher.sendDone(writer);
        }
        return finalAnswer;
    }

    /**
     * 从 DB 加载会话历史并注入到 AgentContext，支持 LLM 摘要压缩超长历史。
     * 注入位置：SystemMessage(s) 之后、当前 UserMessage 之前。
     */
    private void injectHistoryIntoContext(AgentContext context) {
        String sessionId = context.getSessionId();
        if (sessionId == null || chatMessageDAO == null) {
            return;
        }
        int fetchLimit = (maxHistoryTurns + summarizeWhenTurnsOver + 2) * 2;
        List<ChatMessage> rawMessages;
        try {
            rawMessages = chatMessageDAO.listBySessionIdOrderBySortOrder(sessionId, fetchLimit);
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 加载历史消息失败 | sessionId={} | error={}", sessionId, e.getMessage());
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
                log.warn("[AgentOrchestrator] 历史摘要失败，降级截断 | error={}", e.getMessage());
            }
            if (summary != null && !summary.isBlank()) {
                context.addMessage(new SystemMessage("[此前对话摘要] " + summary));
            }
            for (ChatTurn turn : recent) {
                context.addMessage(toSpringAiMessage(turn));
            }
        }
        log.info("[AgentOrchestrator] 历史注入完成 | sessionId={} | 原始消息数={} | 总轮数={}",
                sessionId, rawMessages.size(), totalTurns);
    }

    private org.springframework.ai.chat.messages.Message toSpringAiMessage(ChatTurn turn) {
        if ("user".equals(turn.getRole())) {
            return new UserMessage(turn.getContent());
        } else {
            return new AssistantMessage(turn.getContent());
        }
    }

    private String buildContextHint(String question, List<String> knowledgeCodes,
                                    List<Long> datasourceIds, String sessionId,
                                    PrintWriter writer) {
        // 优先：LLM 智能规划（依赖链场景）
        try {
            stepEventPublisher.publish(writer, StepEvent.planning("正在分析问题，生成执行计划…"));
            ExecutionPlan plan = plannerService.plan(question, knowledgeCodes, datasourceIds);
            if (plan != null) {
                log.info("[AgentOrchestrator] 已生成执行计划 | steps={} | sessionId={}",
                        plan.getSteps().size(), sessionId);
                stepEventPublisher.publish(writer,
                        StepEvent.planning("已生成 " + plan.getSteps().size() + " 步执行计划"));
                return plan.toTaskListHint();
            }
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 执行计划生成失败，降级到多子问题清单 | error={}", e.getMessage());
        }
        // Fallback：多子问题并列清单（并列独立子问题场景）
        String taskList = buildSubQuestionTaskList(question);
        if (taskList != null) {
            log.info("[AgentOrchestrator] 检测到多子问题，已注入任务清单 | sessionId={}", sessionId);
            stepEventPublisher.publish(writer,
                    StepEvent.planning("检测到多子问题，将逐一处理"));
        } else {
            stepEventPublisher.publish(writer, StepEvent.planning("无需额外规划，直接开始推理"));
        }
        return taskList;
    }

    /**
     * 流式 LLM 调用：逐 token 推送 THOUGHT_CHUNK 事件；失败时降级为阻塞调用。
     */
    private AssistantMessage streamLlmCall(Prompt prompt, PrintWriter writer,
                                           int iteration, String sessionId) {
        StringBuilder textBuilder = new StringBuilder();
        ChatResponse[] lastChunkRef = {null};
        boolean streamed = false;

        try {
            chatModel.stream(prompt)
                    .doOnNext(chunk -> {
                        lastChunkRef[0] = chunk;
                        if (chunk.getResult() == null) return;
                        AssistantMessage partial = chunk.getResult().getOutput();
                        String text = partial.getText();
                        if (text != null && !text.isEmpty()) {
                            textBuilder.append(text);
                            stepEventPublisher.publish(writer,
                                    StepEvent.thoughtChunk(iteration, text));
                        }
                    })
                    .blockLast();
            streamed = true;
        } catch (Exception streamEx) {
            log.warn("[AgentOrchestrator] 流式LLM调用失败，降级阻塞调用 | iter={} | error={}",
                    iteration, streamEx.getMessage());
        }

        if (streamed) {
            // 持久化完整思考文本
            String fullThought = textBuilder.toString();
            if (!fullThought.isBlank()) {
                persistStepAsync(sessionId, iteration, "THOUGHT", null, fullThought, 0);
            }
            // 从最后一个 chunk 中获取 toolCalls（Spring AI 流式聚合后最后一帧含完整信息）
            AssistantMessage lastMsg = lastChunkRef[0] != null
                    ? lastChunkRef[0].getResult().getOutput() : null;
            List<AssistantMessage.ToolCall> toolCalls =
                    lastMsg != null ? lastMsg.getToolCalls() : null;
            if (toolCalls != null && !toolCalls.isEmpty()) {
                // 直接返回 lastMsg：其 toolCalls 已完整，无需重新构造
                return lastMsg;
            }
            // 无工具调用：用完整累积文本构造 AssistantMessage
            return new AssistantMessage(fullThought);
        }

        // 阻塞降级路径
        try {
            ChatResponse response = chatModel.call(prompt);
            AssistantMessage msg = response.getResult().getOutput();
            String thought = msg.getText();
            if (thought != null && !thought.isBlank()) {
                stepEventPublisher.publish(writer, StepEvent.thought(iteration, thought));
                persistStepAsync(sessionId, iteration, "THOUGHT", null, thought, 0);
            }
            return msg;
        } catch (Exception callEx) {
            log.error("[AgentOrchestrator] 阻塞LLM调用也失败 | iter={} | error={}",
                    iteration, callEx.getMessage());
            return new AssistantMessage("");
        }
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

    private String buildSystemPrompt(List<Long> datasourceIds, List<String> knowledgeCodes) {
        String dsIds = datasourceIds == null ? resolveAllDatasourceIds()
                : datasourceIds.isEmpty() ? "（不可用）"
                : datasourceIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String kbCodes = knowledgeCodes == null ? "（全部）"
                : knowledgeCodes.isEmpty() ? "（不可用）"
                : String.join(", ", knowledgeCodes);
        return String.format(SYSTEM_PROMPT_TEMPLATE, dsIds, kbCodes);
    }

    private String resolveAllDatasourceIds() {
        if (dbDatasourceDAO == null) return "（全部）";
        try {
            List<Long> ids = dbDatasourceDAO.listAll().stream()
                    .filter(ds -> Integer.valueOf(1).equals(ds.getStatus()))
                    .map(DbDatasource::getId)
                    .collect(Collectors.toList());
            return ids.isEmpty() ? "（不可用）"
                    : ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 加载全量数据源ID失败，兜底显示全部: {}", e.getMessage());
            return "（全部）";
        }
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
