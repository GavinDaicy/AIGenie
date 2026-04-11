package com.genie.query.domain.agent.orchestration;

import com.genie.query.domain.agent.citation.CitationItem;
import com.genie.query.domain.agent.citation.CitationRegistry;
import com.genie.query.domain.agent.repository.AgentStepLogRepository;
import com.genie.query.domain.agent.model.AgentStepLog;
import com.genie.query.domain.agent.event.StepEvent;
import com.genie.query.domain.agent.event.StepEventPublisher;
import com.genie.query.domain.agent.planning.PlannerService;
import com.genie.query.domain.agent.planning.ExecutionPlan;
import com.genie.query.domain.agent.tool.AskUserTool;
import com.genie.query.domain.agent.tool.ToolRegistry;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.domain.qa.model.ChatTurn;
import com.genie.query.domain.qa.service.ConversationSummarizer;
import com.genie.query.domain.knowledge.dao.KnowledgeDAO;
import com.genie.query.domain.knowledge.model.KLState;
import com.genie.query.domain.knowledge.model.Knowledge;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.genie.query.controller.dto.AgentAskRequest;
import com.genie.query.domain.cache.CacheService;

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT_TEMPLATE =
            "你是一个企业智能助手。请根据用户的问题，选择合适的工具来获取信息并给出准确答案。\n\n" +
            "## 工具选择决策（每次回答前按顺序判断）\n" +
            "1. 问题是否**明确需要实时外部信息**（今日/最新市场行情、行业新闻、外部政策等信号词）？\n" +
            "   - 是 → 直接调用 searchWeb，无需先查内部工具\n" +
            "2. 问题是否属于**统计分析类**（聚合/排名/趋势/对比，强依赖数据完整性）？\n" +
            "   - 是 → 仅调用 querySql（RAG 基于分块召回，样本不完整，不适合聚合统计场景）\n" +
            "3. 问题是否属于**纯知识文档类**（概念解释/操作规范/合同条款/流程手册）？\n" +
            "   - 是 → 仅调用 searchKnowledge\n" +
            "4. 其他一般查询类问题，且用户提供了**任意一个可识别的查询主体**（大类即可，如\"钢筋\"）？\n" +
            "   - 是 → **同时调用 querySql 和 searchKnowledge**，等两个结果都返回后合并生成最终答案\n" +
            "5. 内部工具（querySql + searchKnowledge）均返回无结果？\n" +
            "   - 是 → 调用 searchWeb 补充外部信息\n" +
            "6. 完全无法识别任何查询主体（连大类都没有，不知道查什么）？\n" +
            "   - 是 → 调用 askUser，询问**最关键的一个缺失信息**\n\n" +
            "## 宽松查询原则\n" +
            "- 有查询主体（任何大类/名称）就直接查，不要以信息不完整为由拒绝或追问\n" +
            "- 时间范围不明确时默认近3个月，在答案中注明\"基于近3个月数据\"\n" +
            "- 仅当完全无法确定查询主体（不知道查什么）时，才调用 askUser 追问\n\n" +
            "## 工具使用规则\n" +
            "1. **querySql**：查询内部数据库中的任意结构化数据，适用于统计数量、排名、趋势、明细、汇总等各类需要精确数据的问题\n" +
            "   ✅ 适用：任意数量统计、记录明细查询、历史数据分析、聚合汇总——查询范围以上方列出的数据源为准\n" +
            "   ⚠️ 关键：不要因问题领域与示例不同就跳过此工具，凡是数据源中可能存有的结构化数据均可查\n" +
            "   ❌ 不适用：知识文档内容、实时市场行情、纯概念解释\n" +
            "2. **searchKnowledge**：查找知识库中的产品说明、操作规范、技术文档、合同条款、流程规范等\n" +
            "   ✅ 示例：'钢筋验收标准是什么' / '如何填写采购申请' / '系统登录流程'\n" +
            "   ❌ 不适用：需要统计计算的数据分析、互联网实时信息\n" +
            "3. **searchWeb**：查询互联网实时信息，如最新市场价格、行业新闻、外部政策法规等\n" +
            "   🔑 优先触发：问题中含\"今日/最新/最近市场/实时\"等明确外部信息信号词，直接调用此工具\n" +
            "   ✅ 示例：'今日螺纹钢市场价' / '最新建材行业政策' / '2024年铜价走势'\n" +
            "   ❌ 不适用：内部数据库可查到的内容、知识库已有文档\n" +
            "   ⚠️ 后备触发：searchKnowledge 和 querySql 均返回无结果时才作为补充手段\n" +
            "4. **askUser**：**最后手段**，仅当完全无法识别任何查询主体时才使用\n" +
            "   ✅ 必须触发：'帮我查一下价格' / '查一下最近的数据'（完全不知道查什么）\n" +
            "   ❌ 禁止触发：'查钢筋价格'（\"钢筋\"已是足够的查询主体，直接查，结果中提示可补充规格）\n" +
            "   ❌ 禁止触发：'查一下最近钢筋价格'（时间不明确不是理由，默认近3个月）\n" +
            "   ❌ 禁止触发：已有品类但缺少精确规格时（用品类条件查，答案中提示补充细节）\n" +
            "   ⛔ 时间范围不明确：一律默认近3个月，不追问\n" +
            "   ⛔ 规格不明确但品类明确：用品类条件查询，答案末尾提示精化建议\n" +
            "   ⛔ 严禁：输出\"您可以确认以下哪个维度…\"等列举选项文字，**必须直接调用 askUser 工具**\n\n" +
            "## 并行工具调用规则（优先）\n" +
            "对于同时涉及内部数据和知识文档的通用查询，**必须在同一轮同时调用 querySql 和 searchKnowledge**，无需等待其中一个结果再决定是否调用另一个：\n" +
            "- ✅ 并行：「XXX价格和验收标准」→ querySql + searchKnowledge 同时发出\n" +
            "- ✅ 并行：「钢筋采购记录和规格说明」→ querySql + searchKnowledge 同时发出\n" +
            "- ❌ 仅 querySql：「近半年各供应商采购量排名」（统计排名，纯 DB 聚合）\n" +
            "- ❌ 仅 searchKnowledge：「钢筋验收标准是什么」（纯知识文档）\n" +
            "- ❌ 优先 searchWeb：「今日市场行情」（实时外部信息，内部无法回答）\n\n" +
            "## 多工具使用规则\n" +
            "- searchWeb 返回 SEARCH_UNAVAILABLE 时跳过，不要重复调用\n" +
            "- 有多个数据源ID时，针对每个相关ID分别调用 querySql，每次只传一个ID\n\n" +
            "## 多子问题处理规则\n" +
            "- 若用户问题含多个子问题（如含多个'？'），必须逐一处理，不得合并跳过\n" +
            "- 收到工具结果后，检查是否还有未解决的子问题，有则继续调用工具\n" +
            "- 所有子问题都有工具支持的答案后，才能输出最终结论，分条对应每个子问题\n\n" +
            "## 强制规则\n" +
            "- 所有数字结论必须来自工具执行结果，禁止猜测或估算\n" +
            "- 工具结果无法直接回答问题时，立即尝试其他工具，不要在信息不完整时直接输出答案\n" +
            "- askUser 每次只问一个问题，用户回复后继续完成任务，不要再次追问已知信息\n" +
            "- 在最终答案中引用工具数据时，在该句末尾标注 [N]，N 为对应工具返回结果末尾注明的「引用编号」；若工具结果未注明引用编号，则不添加角标\n\n" +
            "当前可用数据源（ID、名称、用途说明）：%s\n" +
            "当前可用知识库编码：%s";

    @Value("${app.agent.max-iterations:8}")
    private int maxIterations;

    @Value("${app.agent.max-history-turns:5}")
    private int maxHistoryTurns;

    @Value("${app.agent.summarize-when-turns-over:5}")
    private int summarizeWhenTurnsOver;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private StepEventPublisher stepEventPublisher;

    @Autowired
    private ContextWindowManager contextWindowManager;

    @Autowired(required = false)
    private AgentStepLogRepository agentStepLogRepository;

    @Autowired(required = false)
    private DbDatasourceDAO dbDatasourceDAO;

    @Autowired(required = false)
    private KnowledgeDAO knowledgeDAO;

    @Autowired(required = false)
    private ChatMessageDAO chatMessageDAO;

    @Autowired(required = false)
    private ConversationSummarizer conversationSummarizer;

    @Autowired
    private PlannerService plannerService;

    @Autowired
    private CacheService cacheService;

    /** askUser 暂停上下文的 Redis key 前缀，value 为已执行工具结果文本，TTL 10分钟 */
    private static final String PAUSED_CTX_KEY_PREFIX = "agent:paused_ctx:";
    private static final long PAUSED_CTX_TTL_MINUTES = 10L;

    @Override
    public AgentResult execute(String question, String sessionId,
                               List<String> knowledgeCodes, List<Long> datasourceIds,
                               AgentAskRequest.ToolForce toolForce,
                               PrintWriter writer) {

        AgentContext context = new AgentContext(sessionId, question, knowledgeCodes, datasourceIds);
        log.info("[AgentOrchestrator] 开始执行 | sessionId={} | question={}", sessionId, question);
        String finalAnswer = null;
        List<CitationItem> allCitations = new ArrayList<>();

        try {
            // 初始化工具 Callback（根据 toolForce 动态过滤被禁用的工具）
            List<Object> toolList = buildToolList(toolForce, datasourceIds, knowledgeCodes);
            ToolCallback[] toolCallbacks = ToolCallbacks.from(toolList.toArray());
            Map<String, ToolCallback> callbackMap = Arrays.stream(toolCallbacks)
                    .collect(Collectors.toMap(cb -> cb.getToolDefinition().name(), cb -> cb));

            // 初始化消息历史
            String systemPrompt = buildSystemPrompt(datasourceIds, knowledgeCodes, toolForce);
            context.addMessage(new SystemMessage(systemPrompt));

            // 检查是否为 askUser 续跑：若有暂停前的工具结果，注入为上下文避免 LLM 重复执行
            String pausedHint = getPausedContext(sessionId);
            if (pausedHint != null) {
                log.info("[AgentOrchestrator] 检测到 askUser 续跑上下文 | sessionId={}", sessionId);
                context.addMessage(new SystemMessage(
                        "[续跑上下文] 用户回复追问前，本轮已执行以下工具并得到结果，请勿重复执行：\n"
                        + pausedHint));
            }

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
                    if (!allCitations.isEmpty()) {
                        stepEventPublisher.publish(writer, StepEvent.citations(allCitations));
                    }
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

                    // askUser 工具：在调用 ToolCallback 之前直接拦截，从 JSON 参数中提取追问内容
                    // 避免经过 ToolCallback.call() 导致的 JSON 二次编码问题
                    if (AskUserTool.TOOL_NAME.equals(toolName)) {
                        String askQuestion = extractAskUserQuestion(toolArgs);
                        log.info("[AgentOrchestrator] 检测到 askUser 调用 | question={} | sessionId={}", askQuestion, sessionId);
                        // 保存本轮已执行工具的结果到 Redis，续跑时注入为上下文（避免 LLM 重复调用工具）
                        String toolResultsHint = buildToolResultsHint(context);
                        if (!toolResultsHint.isBlank()) {
                            cacheService.set(
                                    PAUSED_CTX_KEY_PREFIX + sessionId,
                                    toolResultsHint,
                                    PAUSED_CTX_TTL_MINUTES,
                                    TimeUnit.MINUTES);
                            log.debug("[AgentOrchestrator] 已保存 askUser 暂停上下文到 Redis | sessionId={}", sessionId);
                        }
                        stepEventPublisher.publish(writer, StepEvent.askUser(askQuestion));
                        persistAskUserMessageAsync(sessionId, askQuestion);
                        stepEventPublisher.sendDone(writer);
                        CitationRegistry.cleanup();
                        return AgentResult.paused(); // 暂停 ReAct 循环，等待用户回复
                    }

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

                    // 从 CitationRegistry 取出本次工具调用注册的引用，每条单独分配全局递增编号
                    List<CitationItem> toolCitations = CitationRegistry.drainAndClear();
                    Integer firstCitationIndex = null;
                    if (!toolCitations.isEmpty()) {
                        for (CitationItem c : toolCitations) {
                            c.setIndex(CitationRegistry.nextIndex());
                        }
                        firstCitationIndex = toolCitations.get(0).getIndex();
                        allCitations.addAll(toolCitations);
                    }

                    // 推送 TOOL_RESULT 事件（携带第一个 citationIndex，前端据此定位本次引用起点）
                    StepEvent resultEvent = StepEvent.toolResult(i, toolName, toolResult, durationMs, firstCitationIndex);
                    stepEventPublisher.publish(writer, resultEvent);
                    persistStepAsync(sessionId, i, "TOOL_RESULT", toolName, toolResult, (int) durationMs);

                    // 若有引用编号，将编号追加给 LLM，确保最终答案中 [N] 与引用数据一一对应
                    String toolResultForLlm = toolResult;
                    if (firstCitationIndex != null) {
                        if (toolCitations.size() == 1) {
                            toolResultForLlm = toolResult + "\n【引用编号：[" + firstCitationIndex
                                    + "]，在最终答案引用此工具数据时请在句末标注 [" + firstCitationIndex + "]】";
                        } else {
                            StringBuilder idxHint = new StringBuilder();
                            for (CitationItem c : toolCitations) {
                                if (idxHint.length() > 0) idxHint.append(", ");
                                idxHint.append("[").append(c.getIndex()).append("]");
                            }
                            toolResultForLlm = toolResult + "\n【引用编号：" + idxHint
                                    + "，在最终答案引用此工具各条数据时请在句末标注对应编号】";
                        }
                    }

                    context.incrementToolCallCount();
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolName, toolResultForLlm));
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
        } finally {
            CitationRegistry.cleanup();
        }
        return AgentResult.of(finalAnswer, allCitations);
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
        } else if ("ask_user".equals(turn.getRole())) {
            return new AssistantMessage("[Agent追问] " + turn.getContent());
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

    private String buildSystemPrompt(List<Long> datasourceIds, List<String> knowledgeCodes,
                                     AgentAskRequest.ToolForce toolForce) {
        String dsIds = datasourceIds == null ? resolveAllDatasourceIds()
                : datasourceIds.isEmpty() ? "（不可用）"
                : resolveDatasourceInfo(datasourceIds);
        String kbCodes = knowledgeCodes == null ? resolveAllKnowledgeCodes()
                : knowledgeCodes.isEmpty() ? "（不可用）"
                : String.join(", ", knowledgeCodes);
        StringBuilder prompt = new StringBuilder(String.format(SYSTEM_PROMPT_TEMPLATE, dsIds, kbCodes))
                .append("\n当前日期：").append(LocalDate.now());
        // 追加 toolForce 强制约束指令（最高优先级，覆盖上方所有工具选择规则）
        String forceSection = buildToolForceSection(toolForce);
        if (forceSection != null) {
            prompt.append("\n\n").append(forceSection);
        }
        return prompt.toString();
    }

    /**
     * 根据 toolForce 生成强制约束 System Prompt 附加段。
     * 强制启用时通过 Prompt 驱动 LLM 主动调用工具；强制禁用时通过 Prompt 明确禁止（配合 toolCallbacks 过滤双重保障）。
     */
    private String buildToolForceSection(AgentAskRequest.ToolForce toolForce) {
        if (toolForce == null) return null;
        boolean hasAnyForce = toolForce.getWebSearch() != null
                || toolForce.getKnowledge() != null
                || toolForce.getSql() != null;
        if (!hasAnyForce) return null;

        StringBuilder sb = new StringBuilder("## 本次强制工具约束（最高优先级，覆盖上方所有工具选择规则）\n");
        if (Boolean.TRUE.equals(toolForce.getSql())) {
            sb.append("- 【强制调用】querySql：无论问题类型，**必须**调用此工具查询数据库\n");
        } else if (Boolean.FALSE.equals(toolForce.getSql())) {
            sb.append("- 【严格禁止】querySql：**禁止**调用此工具，即使问题涉及数据统计\n");
        }
        if (Boolean.TRUE.equals(toolForce.getKnowledge())) {
            sb.append("- 【强制调用】searchKnowledge：无论问题类型，**必须**调用此工具检索知识库\n");
        } else if (Boolean.FALSE.equals(toolForce.getKnowledge())) {
            sb.append("- 【严格禁止】searchKnowledge：**禁止**调用此工具，即使问题涉及知识文档\n");
        }
        if (Boolean.TRUE.equals(toolForce.getWebSearch())) {
            sb.append("- 【强制调用】searchWeb：无论问题类型，**必须**调用此工具搜索互联网\n");
        } else if (Boolean.FALSE.equals(toolForce.getWebSearch())) {
            sb.append("- 【严格禁止】searchWeb：**禁止**调用此工具，不得进行联网搜索\n");
        }
        return sb.toString();
    }

    /**
     * 根据 toolForce 动态组装工具列表：被强制禁用的工具从 toolCallbacks 中移除，
     * LLM 将看不到该工具的定义，彻底避免误调用。
     */
    private List<Object> buildToolList(AgentAskRequest.ToolForce toolForce,
                                       List<Long> datasourceIds,
                                       List<String> knowledgeCodes) {
        return toolRegistry.getTools(toolForce, datasourceIds, knowledgeCodes);
    }

    private String resolveAllKnowledgeCodes() {
        if (knowledgeDAO == null) return "（全部）";
        try {
            List<String> codes = knowledgeDAO.queryKnowledgeList().stream()
                    .filter(k -> Boolean.TRUE.equals(k.getEnabled()))
                    .filter(k -> k.getStatus() != null && k.getStatus() != KLState.UNPUBLISHED)
                    .map(Knowledge::getCode)
                    .collect(Collectors.toList());
            return codes.isEmpty() ? "（不可用）"
                    : String.join(", ", codes);
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 加载全量知识库编码失败，兜底显示全部: {}", e.getMessage());
            return "（全部）";
        }
    }

    private String resolveAllDatasourceIds() {
        if (dbDatasourceDAO == null) return "（全部）";
        try {
            List<DbDatasource> list = dbDatasourceDAO.listAll().stream()
                    .filter(ds -> Integer.valueOf(1).equals(ds.getStatus()))
                    .collect(Collectors.toList());
            if (list.isEmpty()) return "（不可用）";
            return list.stream()
                    .map(this::formatDatasourceEntry)
                    .collect(Collectors.joining("; "));
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 加载全量数据源ID失败，兜底显示全部: {}", e.getMessage());
            return "（全部）";
        }
    }

    private String resolveDatasourceInfo(List<Long> ids) {
        if (dbDatasourceDAO == null) {
            return ids.stream().map(String::valueOf).collect(Collectors.joining("; "));
        }
        try {
            Map<Long, DbDatasource> dsMap = dbDatasourceDAO.listAll().stream()
                    .collect(Collectors.toMap(DbDatasource::getId, ds -> ds, (a, b) -> a));
            return ids.stream()
                    .map(id -> {
                        DbDatasource ds = dsMap.get(id);
                        return ds != null ? formatDatasourceEntry(ds) : String.valueOf(id);
                    })
                    .collect(Collectors.joining("; "));
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 加载数据源名称失败，仅显示ID: {}", e.getMessage());
            return ids.stream().map(String::valueOf).collect(Collectors.joining("; "));
        }
    }

    private String formatDatasourceEntry(DbDatasource ds) {
        StringBuilder sb = new StringBuilder(String.valueOf(ds.getId()));
        if (ds.getName() != null && !ds.getName().isBlank()) {
            sb.append(" (").append(ds.getName());
            if (ds.getDescription() != null && !ds.getDescription().isBlank()) {
                sb.append("：").append(ds.getDescription());
            }
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * 将追问消息异步持久化到 chat_message（role=ask_user），供后续多轮历史注入使用。
     */
    @Async
    protected void persistAskUserMessageAsync(String sessionId, String question) {
        if (sessionId == null || chatMessageDAO == null) return;
        try {
            int nextOrder = chatMessageDAO.countBySessionId(sessionId);
            ChatMessage askMsg = new ChatMessage();
            askMsg.setSessionId(sessionId);
            askMsg.setRole("ask_user");
            askMsg.setContent(question);
            askMsg.setSortOrder(nextOrder);
            chatMessageDAO.insert(askMsg);
            log.info("[AgentOrchestrator] 追问消息已落库 | sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 追问消息落库失败 | error={}", e.getMessage());
        }
    }

    @Async
    protected void persistStepAsync(String sessionId, int iteration, String stepType,
                                    String toolName, String content, int durationMs) {
        if (agentStepLogRepository == null) return;
        try {
            AgentStepLog log = new AgentStepLog();
            log.setSessionId(sessionId);
            log.setIteration(iteration);
            log.setStepType(stepType);
            log.setToolName(toolName);
            log.setContent(content != null && content.length() > 4000
                    ? content.substring(0, 4000) + "..." : content);
            log.setDurationMs(durationMs);
            agentStepLogRepository.insert(log);
        } catch (Exception ex) {
            AgentOrchestratorImpl.log.warn("[AgentOrchestrator] 步骤持久化失败: {}", ex.getMessage());
        }
    }

    /**
     * 从当前 AgentContext 的消息历史中提取已执行的工具结果文本。
     * 用于 askUser 暂停前保存上下文，续跑时让 LLM 了解之前的工具结果，避免重复执行。
     */
    private String buildToolResultsHint(AgentContext context) {
        return context.getMessages().stream()
                .filter(m -> m instanceof ToolResponseMessage)
                .map(m -> ((ToolResponseMessage) m).getResponses().stream()
                        .map(r -> "工具[" + r.name() + "]结果：" + r.responseData())
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 从 Redis 取出并删除指定 session 的暂停上下文（一次性消费）。
     * Redis TTL 到期后自动失效，无需手动判断过期。
     */
    private String getPausedContext(String sessionId) {
        String key = PAUSED_CTX_KEY_PREFIX + sessionId;
        String hint = cacheService.get(key);
        if (hint != null) {
            cacheService.delete(key);
        }
        return hint;
    }

    /**
     * 从 askUser 工具的 JSON 参数字符串中提取追问内容。
     * Spring AI 将 toolCall.arguments() 序列化为 JSON，如 {"question":"请问..."}，
     * 此方法直接解析该 JSON，无需依赖返回值信号字符串。
     */
    private String extractAskUserQuestion(String toolArgsJson) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(toolArgsJson);
            JsonNode questionNode = node.get("question");
            if (questionNode != null && !questionNode.isNull()) {
                return questionNode.asText();
            }
        } catch (Exception e) {
            log.warn("[AgentOrchestrator] 解析 askUser 参数失败，使用原始值 | args={} | error={}", toolArgsJson, e.getMessage());
        }
        return toolArgsJson;
    }
}
