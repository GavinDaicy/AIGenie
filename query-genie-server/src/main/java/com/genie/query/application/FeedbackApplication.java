package com.genie.query.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.controller.dto.FeedbackRequest;
import com.genie.query.domain.agent.citation.CitationItem;
import com.genie.query.domain.agent.model.AgentFeedback;
import com.genie.query.domain.agent.repository.AgentFeedbackRepository;
import com.genie.query.domain.agent.tool.sql.pipeline.DynamicFewShotService;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.domain.query.service.QueryRewriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 反馈应用服务：处理用户点赞/点踩，驱动 FewShot ES 写入。
 *
 * <p>流程：
 * <ol>
 *   <li>同步：保存反馈记录到 DB</li>
 *   <li>异步（仅 rating=1 好评）：
 *     <ol>
 *       <li>查找关联 assistant 消息，提取 SQL citation</li>
 *       <li>查找前一条 user 消息作为原始问题</li>
 *       <li>（可选）通过 QueryRewriteService 将问题改写为独立形式（消除代词指代）</li>
 *       <li>调用 DynamicFewShotService 写入 ES 向量库</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * @author daicy
 * @date 2026/4/12
 */
@Service
public class FeedbackApplication {

    private static final Logger log = LoggerFactory.getLogger(FeedbackApplication.class);

    @Autowired
    private AgentFeedbackRepository agentFeedbackRepository;

    @Autowired(required = false)
    private ChatMessageDAO chatMessageDAO;

    @Autowired
    private DynamicFewShotService dynamicFewShotService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private QueryRewriteService queryRewriteService;

    /**
     * 处理用户反馈（同步）：保存反馈记录，如是好评则异步触发 FewShot 写入。
     *
     * @param request 前端提交的反馈请求
     */
    public void handleFeedback(FeedbackRequest request) {
        AgentFeedback feedback = buildAndSaveFeedback(request);
        if (feedback != null && Integer.valueOf(1).equals(feedback.getRating())) {
            triggerFewShotSaveAsync(feedback);
        }
    }

    private AgentFeedback buildAndSaveFeedback(FeedbackRequest request) {
        try {
            AgentFeedback feedback = new AgentFeedback();
            feedback.setMessageId(request.getMessageId());
            feedback.setSessionId(request.getSessionId());
            feedback.setRating(request.getRating());
            feedback.setComment(request.getComment());
            agentFeedbackRepository.insert(feedback);
            log.info("[Feedback] 反馈已保存 | messageId={} | rating={}", request.getMessageId(), request.getRating());
            return feedback;
        } catch (Exception e) {
            log.warn("[Feedback] 反馈保存失败 | error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 异步触发 FewShot 写入（仅好评）。通过 Spring AOP 代理异步执行，不阻塞主请求。
     */
    @Async
    public void triggerFewShotSaveAsync(AgentFeedback feedback) {
        try {
            log.info("[Feedback] 触发 FewShot 写入 | messageId={}", feedback.getMessageId());

            if (chatMessageDAO == null) {
                log.warn("[Feedback] ChatMessageDAO 不可用，跳过 FewShot 写入");
                return;
            }

            // 1. 查找 assistant 消息
            ChatMessage assistantMsg = chatMessageDAO.findById(feedback.getMessageId());
            if (assistantMsg == null || !"assistant".equals(assistantMsg.getRole())) {
                log.warn("[Feedback] 未找到 assistant 消息 | messageId={}", feedback.getMessageId());
                return;
            }

            // 2. 从 citations 中提取全部不重复的 SQL
            List<String> sqls = findAllSqlsFromCitations(assistantMsg.getCitationsJson());
            if (sqls.isEmpty()) {
                log.info("[Feedback] assistant 消息无 SQL citation，跳过 FewShot 写入 | messageId={}", feedback.getMessageId());
                return;
            }

            // 3. 找前一条 user 消息
            ChatMessage userMsg = findPrecedingUserMessage(feedback.getSessionId(), assistantMsg.getSortOrder());
            if (userMsg == null) {
                log.warn("[Feedback] 未找到前置 user 消息 | sessionId={} | assistantSortOrder={}",
                        feedback.getSessionId(), assistantMsg.getSortOrder());
                return;
            }
            String rawQuestion = userMsg.getContent();

            // 4. 将问题改写为独立形式（消除代词指代），失败时用原始问题
            String standaloneQuestion = resolveStandaloneQuestion(
                    rawQuestion, feedback.getSessionId(), assistantMsg.getSortOrder());

            // 5. 每条不重复的 SQL 各写入一对 Q→SQL
            String feedbackId = String.valueOf(feedback.getId());
            for (String sql : sqls) {
                dynamicFewShotService.saveSuccessfulPair(standaloneQuestion, sql, null, feedbackId);
            }
            log.info("[Feedback] FewShot 写入完成 | question={} | sqlCount={} | feedbackId={}",
                    standaloneQuestion, sqls.size(), feedback.getId());
        } catch (Exception e) {
            log.warn("[Feedback] FewShot 异步写入失败（不影响主流程） | error={}", e.getMessage(), e);
        }
    }

    /**
     * 从 citationsJson 中提取所有不重复的 SQL 语句（按出现顺序）。
     *
     * <p>多次 sqlQuery 工具调用各产生一条 SQL citation；SelfCorrectionLoop 重试可能产生
     * 相同 SQL，用 LinkedHashSet 去重后保证每条不同 SQL 只存入 ES 一次。
     */
    private List<String> findAllSqlsFromCitations(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) return List.of();
        try {
            List<CitationItem> citations = objectMapper.readValue(citationsJson, new TypeReference<>() {});
            Set<String> seen = new LinkedHashSet<>();
            for (CitationItem c : citations) {
                if (CitationItem.CitationType.SQL == c.getType()
                        && c.getSql() != null && !c.getSql().isBlank()) {
                    seen.add(c.getSql().trim());
                }
            }
            return new ArrayList<>(seen);
        } catch (Exception e) {
            log.warn("[Feedback] 解析 citationsJson 失败 | error={}", e.getMessage());
            return List.of();
        }
    }

    private ChatMessage findPrecedingUserMessage(String sessionId, int assistantSortOrder) {
        if (sessionId == null) return null;
        try {
            List<ChatMessage> msgs = chatMessageDAO.listBySessionIdOrderBySortOrder(sessionId, 100);
            for (ChatMessage msg : msgs) {
                if (msg.getSortOrder() == assistantSortOrder - 1 && "user".equals(msg.getRole())) {
                    return msg;
                }
            }
        } catch (Exception e) {
            log.warn("[Feedback] 查找前置 user 消息失败 | error={}", e.getMessage());
        }
        return null;
    }

    private String resolveStandaloneQuestion(String rawQuestion, String sessionId, int assistantSortOrder) {
        if (queryRewriteService == null) return rawQuestion;
        try {
            List<String> history = loadHistoryBeforeMessage(sessionId, assistantSortOrder, 4);
            if (history.isEmpty()) return rawQuestion;

            QueryRewriteService.QueryRewriteContext ctx =
                    new QueryRewriteService.QueryRewriteContext(Collections.emptyList(), history);
            QueryRewriteService.QueryRewriteResult result =
                    queryRewriteService.generateQueries(rawQuestion, 1, ctx);
            String mainQuery = result.getMainQuery();
            return (mainQuery != null && !mainQuery.isBlank()) ? mainQuery : rawQuestion;
        } catch (Exception e) {
            log.warn("[Feedback] 问题改写失败，使用原始问题 | error={}", e.getMessage());
            return rawQuestion;
        }
    }

    private List<String> loadHistoryBeforeMessage(String sessionId, int assistantSortOrder, int maxItems) {
        try {
            List<ChatMessage> msgs = chatMessageDAO.listBySessionIdOrderBySortOrder(sessionId, 100);
            List<String> history = new ArrayList<>();
            for (ChatMessage msg : msgs) {
                if (msg.getSortOrder() >= assistantSortOrder) break;
                if ("user".equals(msg.getRole())) {
                    history.add("用户：" + msg.getContent());
                } else if ("assistant".equals(msg.getRole())) {
                    String content = msg.getContent();
                    if (content != null && content.length() > 200) {
                        content = content.substring(0, 200);
                    }
                    history.add("助手：" + content);
                }
            }
            // 取最近 maxItems 条
            int start = Math.max(0, history.size() - maxItems);
            return history.subList(start, history.size());
        } catch (Exception e) {
            log.warn("[Feedback] 加载历史失败 | error={}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
