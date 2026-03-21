package com.genie.query.application;

import com.genie.query.controller.dto.QaRequest;
import com.genie.query.controller.dto.QaResponse;
import com.genie.query.controller.dto.QaSourceItem;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.dao.ChatSessionDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.domain.chat.model.ChatSession;
import com.genie.query.domain.qa.model.AnswerResult;
import com.genie.query.domain.qa.model.ChatTurn;
import com.genie.query.domain.qa.config.QaPolicyProperties;
import com.genie.query.domain.qa.service.AnswerService;
import com.genie.query.domain.qa.service.QaQueryService;
import com.genie.query.domain.qa.service.ConversationSummarizer;
import com.genie.query.domain.qa.service.StreamCallback;
import com.genie.query.domain.query.model.QueryResultEntry;
import com.genie.query.domain.query.config.QueryRewritePolicyProperties;
import com.genie.query.domain.query.service.QueryRewriteService;
import com.genie.query.domain.vectorstore.SearchMode;
import com.genie.query.infrastructure.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能问答应用层：编排检索与答案生成，将领域结果转为 DTO 返回。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Service
public class QaApplication {

    /** 检索参数：模式、条数、是否重排、实际检索条数 */
    private static final class SearchParams {
        final SearchMode mode;
        final int size;
        final boolean enableRerank;
        final int searchSize;

        SearchParams(SearchMode mode, int size, boolean enableRerank, int searchSize) {
            this.mode = mode;
            this.size = size;
            this.enableRerank = enableRerank;
            this.searchSize = searchSize;
        }
    }

    /** 编排结果：用于检索的查询列表 + 供前端展示的改写问句（仅启用改写时有值） */
    private static final class QueriesAndRewrite {
        final List<String> queries;
        final List<String> rewrittenQueriesForDisplay;

        QueriesAndRewrite(List<String> queries, List<String> rewrittenQueriesForDisplay) {
            this.queries = queries;
            this.rewrittenQueriesForDisplay = rewrittenQueriesForDisplay;
        }
    }

    @Autowired
    private QaQueryService qaQueryService;
    @Autowired(required = false)
    private QueryRewriteService queryRewriteService;
    @Autowired
    private AnswerService answerService;
    @Autowired(required = false)
    private ChatSessionDAO chatSessionDAO;
    @Autowired(required = false)
    private ChatMessageDAO chatMessageDAO;
    @Autowired(required = false)
    private SessionApplication sessionApplication;
    @Autowired(required = false)
    private ConversationSummarizer conversationSummarizer;
    @Autowired(required = false)
    private QaPolicyProperties qaPolicyProperties;
    @Autowired(required = false)
    private QueryRewritePolicyProperties queryRewritePolicyProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.qa.size:10}")
    private int qaDefaultSize;
    @Value("${app.qa.rerank:true}")
    private boolean qaRerankDefault;
    @Value("${app.rerank.enabled:false}")
    private boolean rerankEnabled;
    @Value("${app.rerank.candidate-factor:1}")
    private int rerankCandidateFactor;

    /**
     * 智能问答：先编排改写（可选）、再召回，最后生成答案。
     * 若传入 sessionId 则多轮落库并带历史上下文；否则单轮不落库。
     *
     * @param request 问题、会话ID、知识库范围、检索参数等
     * @return 答案、引用来源及改写问句（启用改写时）
     */
    public QaResponse ask(QaRequest request) throws IOException {
        validateRequest(request);
        String question = request.getQuestion().trim();
        String sessionId = StringUtils.isBlank(request.getSessionId()) ? null : request.getSessionId().trim();

        SearchParams searchParams = resolveSearchParams(request);
        QueriesAndRewrite qar = resolveQueriesAndRewrite(question, request);
        List<QueryResultEntry> entries = qaQueryService.searchWithQueries(
                qar.queries,
                request.getKnowledgeCodes(),
                searchParams.mode,
                searchParams.size,
                searchParams.searchSize,
                searchParams.enableRerank,
                request.getUseTimeDecay()
        );

        List<ChatTurn> history = loadHistoryForSession(sessionId);
        AnswerResult result = history.isEmpty()
                ? answerService.generate(question, entries)
                : answerService.generate(question, entries, history);

        List<QaSourceItem> sources = mapToSourceItems(result);
        QaResponse.QaResponseBuilder responseBuilder = QaResponse.builder()
                .answer(result.getAnswer())
                .sources(sources)
                .rewrittenQueries(qar.rewrittenQueriesForDisplay);

        String messageId = persistChatTurnIfNeeded(sessionId, question, result.getAnswer(), sources);
        if (messageId != null) {
            responseBuilder.sessionId(sessionId).messageId(messageId);
        }
        return responseBuilder.build();
    }

    /**
     * 流式智能问答：先编排改写（可选）、再召回，答案通过 SseEmitter 按块推送。
     * 事件：rewritten（改写问句，仅启用改写时）、chunk（正文片段）、sources（引用来源）、done（结束）、error（错误信息）。
     */
    public void askStream(QaRequest request, SseEmitter emitter) throws IOException {
        validateRequest(request);
        String question = request.getQuestion().trim();
        String sessionId = StringUtils.isBlank(request.getSessionId()) ? null : request.getSessionId().trim();

        SearchParams searchParams = resolveSearchParams(request);
        QueriesAndRewrite qar = resolveQueriesAndRewrite(question, request);
        if (qar.rewrittenQueriesForDisplay != null && !qar.rewrittenQueriesForDisplay.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("rewritten").data(objectMapper.writeValueAsString(qar.rewrittenQueriesForDisplay)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        List<QueryResultEntry> entries = qaQueryService.searchWithQueries(
                qar.queries,
                request.getKnowledgeCodes(),
                searchParams.mode,
                searchParams.size,
                searchParams.searchSize,
                searchParams.enableRerank,
                request.getUseTimeDecay()
        );

        List<ChatTurn> history = loadHistoryForSession(sessionId);

        StreamCallback callback = new StreamCallback() {
            @Override
            public void onChunk(String chunk) {
                try {
                    emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(chunk)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onComplete(String fullAnswer, List<QueryResultEntry> resultSources) {
                try {
                    List<QaSourceItem> sources = mapToSourceItems(AnswerResult.builder().sources(resultSources).build());
                    String messageId = persistChatTurnIfNeeded(sessionId, question, fullAnswer, sources);
                    emitter.send(SseEmitter.event().name("sources").data(objectMapper.writeValueAsString(sources)));
                    Map<String, String> done = new HashMap<>();
                    if (sessionId != null) done.put("sessionId", sessionId);
                    if (messageId != null) done.put("messageId", messageId);
                    emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(done)));
                    emitter.complete();
                } catch (Exception e) {
                    try {
                        emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    } catch (IOException ignored) {}
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(t.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(t);
            }
        };

        answerService.streamGenerate(question, entries, history, callback);
    }

    /** 校验请求：问题不能为空。 */
    private void validateRequest(QaRequest request) {
        if (request == null || StringUtils.isBlank(request.getQuestion())) {
            throw new BusinessException("问题不能为空");
        }
    }

    /** 解析检索参数：模式、条数、是否重排及实际检索条数。 */
    private SearchParams resolveSearchParams(QaRequest request) {
        SearchMode mode = request.getMode() != null ? request.getMode() : SearchMode.HYBRID;
        int size = request.getSize() != null && request.getSize() > 0
                ? request.getSize()
                : qaDefaultSize;
        boolean enableRerank = (request.getRerank() != null ? request.getRerank() : qaRerankDefault) && rerankEnabled;
        int searchSize = enableRerank ? size * Math.max(1, rerankCandidateFactor) : size;
        return new SearchParams(mode, size, enableRerank, searchSize);
    }

    /**
     * 编排改写与召回用查询：若启用改写则调用领域改写能力得到多条查询，否则使用原问句一条。
     *
     * @return 用于召回的查询列表 + 供前端展示的改写问句（未启用改写时为 null）
     */
    private QueriesAndRewrite resolveQueriesAndRewrite(String question, QaRequest request) throws IOException {
        boolean enableRewrite = isRewriteEnabled(request.getEnableQueryRewrite());
        if (queryRewriteService == null || !enableRewrite) {
            return new QueriesAndRewrite(List.of(question), null);
        }
        int totalQueries = resolveTotalQueries(request.getRewriteQueryCount());
        if (totalQueries < 1) {
            return new QueriesAndRewrite(List.of(question), null);
        }
        QueryRewriteService.QueryRewriteContext context =
                new QueryRewriteService.QueryRewriteContext(request.getKnowledgeCodes());
        QueryRewriteService.QueryRewriteResult rewriteResult =
                queryRewriteService.generateQueries(question, totalQueries, context);
        String mainQuery = rewriteResult.getMainQuery() != null ? rewriteResult.getMainQuery().trim() : question;
        if (mainQuery.isEmpty()) {
            mainQuery = question;
        }
        List<String> expanded = rewriteResult.getExpandedQueries() != null ? rewriteResult.getExpandedQueries() : List.of();
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(mainQuery);
        for (String q : expanded) {
            if (q != null && !q.isBlank()) {
                ordered.add(q.trim());
            }
        }
        List<String> queries = new ArrayList<>(ordered);
        return new QueriesAndRewrite(queries, queries);
    }

    private boolean isRewriteEnabled(Boolean requestOverride) {
        if (requestOverride != null) {
            return requestOverride;
        }
        return queryRewritePolicyProperties != null && queryRewritePolicyProperties.isEnabled();
    }

    private int resolveTotalQueries(Integer requestOverride) {
        int defaultTotal = queryRewritePolicyProperties != null ? queryRewritePolicyProperties.getDefaultTotalQueries() : 4;
        int maxTotal = queryRewritePolicyProperties != null ? queryRewritePolicyProperties.getMaxTotalQueries() : 8;
        int total = requestOverride != null ? requestOverride : defaultTotal;
        if (total < 1) total = 1;
        if (total > maxTotal) total = maxTotal;
        return total;
    }

    /** 加载会话历史（含总结）；无会话或未配置 DAO 时返回空列表；会话不存在时抛异常。 */
    private List<ChatTurn> loadHistoryForSession(String sessionId) {
        if (sessionId == null || chatSessionDAO == null || chatMessageDAO == null) {
            return List.of();
        }
        ChatSession session = chatSessionDAO.findById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        int maxTurns = qaPolicyProperties != null ? qaPolicyProperties.getMaxHistoryTurns() : 10;
        int summarizeOver = qaPolicyProperties != null ? qaPolicyProperties.getSummarizeWhenTurnsOver() : 6;
        return buildHistoryWithSummarization(sessionId, maxTurns, summarizeOver);
    }

    /** 将答案来源列表转为接口 DTO。 */
    private List<QaSourceItem> mapToSourceItems(AnswerResult result) {
        if (result.getSources() == null) {
            return List.of();
        }
        return result.getSources().stream()
                .map(e -> QaSourceItem.builder()
                        .knowledgeCode(e.getKnowledgeCode())
                        .score(e.getScore())
                        .documentName(e.getDocument() != null ? e.getDocument().getName() : null)
                        .chunkContent(e.getChunkContent())
                        .build())
                .collect(Collectors.toList());
    }

    /** 若有会话则落库本轮问答并可选更新标题，返回助理消息 ID，否则返回 null。 */
    private String persistChatTurnIfNeeded(String sessionId, String question, String answer, List<QaSourceItem> sources) {
        if (sessionId == null || chatMessageDAO == null || sessionApplication == null) {
            return null;
        }
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
        assistantMsg.setContent(answer);
        assistantMsg.setSortOrder(nextOrder + 1);
        try {
            assistantMsg.setSources(objectMapper.writeValueAsString(sources));
        } catch (JsonProcessingException e) {
            assistantMsg.setSources("[]");
        }
        chatMessageDAO.insert(assistantMsg);

        if (nextOrder == 0) {
            String title = question.length() > 30 ? question.substring(0, 30) + "..." : question;
            sessionApplication.updateSessionTitle(sessionId, title);
        }
        return assistantMsg.getId();
    }

    /**
     * 加载会话历史并视情况总结：超过阈值时对更早轮次总结，保留最近若干轮。
     */
    private List<ChatTurn> buildHistoryWithSummarization(String sessionId, int maxHistoryTurns, int summarizeWhenTurnsOver) {
        if (chatMessageDAO == null) {
            return List.of();
        }
        int limit = (maxHistoryTurns + 2) * 2;
        List<ChatMessage> messages = chatMessageDAO.listBySessionIdOrderBySortOrder(sessionId, limit);
        if (messages.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> allTurns = messages.stream()
                .map(m -> ChatTurn.builder().role(m.getRole()).content(m.getContent()).build())
                .collect(Collectors.toList());
        int totalTurns = allTurns.size();
        if (totalTurns <= summarizeWhenTurnsOver * 2 || conversationSummarizer == null) {
            int from = Math.max(0, totalTurns - maxHistoryTurns * 2);
            return allTurns.subList(from, totalTurns);
        }
        int keepRecentCount = summarizeWhenTurnsOver * 2;
        int summarizeSize = totalTurns - keepRecentCount;
        List<ChatTurn> toSummarize = allTurns.subList(0, summarizeSize);
        List<ChatTurn> recent = allTurns.subList(summarizeSize, totalTurns);
        String summary = conversationSummarizer.summarize(toSummarize);
        if (StringUtils.isBlank(summary)) {
            return recent;
        }
        List<ChatTurn> out = new ArrayList<>();
        out.add(ChatTurn.builder().role("user").content("[此前对话摘要] " + summary).build());
        out.addAll(recent);
        return out;
    }
}
