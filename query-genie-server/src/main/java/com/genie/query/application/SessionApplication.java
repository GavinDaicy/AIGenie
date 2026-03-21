package com.genie.query.application;

import com.genie.query.controller.dto.CreateSessionResponse;
import com.genie.query.controller.dto.MessageItem;
import com.genie.query.controller.dto.QaSourceItem;
import com.genie.query.controller.dto.SessionDetail;
import com.genie.query.controller.dto.SessionListItem;
import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.dao.ChatSessionDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.domain.chat.model.ChatSession;
import com.genie.query.infrastructure.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话 CRUD 应用服务。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Service
public class SessionApplication {

    private static final String DEFAULT_TITLE = "新会话";
    private static final int LIST_PAGE_SIZE = 100;
    private static final int MESSAGES_LIMIT = 500;

    @Autowired
    private ChatSessionDAO chatSessionDAO;
    @Autowired
    private ChatMessageDAO chatMessageDAO;

    @Value("${app.qa.sessions.list-limit:100}")
    private int listLimit;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 新建会话。
     */
    public CreateSessionResponse createSession(String knowledgeCodes) {
        ChatSession session = new ChatSession();
        session.setTitle(DEFAULT_TITLE);
        session.setKnowledgeCodes(StringUtils.isBlank(knowledgeCodes) ? null : knowledgeCodes);
        chatSessionDAO.insert(session);
        return CreateSessionResponse.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .build();
    }

    /**
     * 会话列表，按更新时间倒序。
     */
    public List<SessionListItem> listSessions(int offset, int limit) {
        int safeLimit = limit <= 0 ? LIST_PAGE_SIZE : Math.min(limit, listLimit);
        List<ChatSession> list = chatSessionDAO.listOrderByUpdateTimeDesc(offset, safeLimit);
        return list.stream()
                .map(s -> SessionListItem.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .updateTime(s.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 会话详情（含消息列表）。
     */
    public SessionDetail getSessionDetail(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new BusinessException("会话ID不能为空");
        }
        ChatSession session = chatSessionDAO.findById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        List<ChatMessage> messages = chatMessageDAO.listBySessionIdOrderBySortOrder(sessionId, MESSAGES_LIMIT);
        List<MessageItem> messageItems = new ArrayList<>();
        for (ChatMessage m : messages) {
            List<QaSourceItem> sources = parseSources(m.getSources());
            messageItems.add(MessageItem.builder()
                    .id(m.getId())
                    .role(m.getRole())
                    .content(m.getContent())
                    .sources(sources)
                    .build());
        }
        return SessionDetail.builder()
                .id(session.getId())
                .title(session.getTitle())
                .knowledgeCodes(session.getKnowledgeCodes())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .messages(messageItems)
                .build();
    }

    /**
     * 删除会话（同时删除其下所有消息）。
     */
    public void deleteSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new BusinessException("会话ID不能为空");
        }
        chatMessageDAO.deleteBySessionId(sessionId);
        chatSessionDAO.deleteById(sessionId);
    }

    /**
     * 更新会话标题（供 QaApplication 在首条消息后调用）。
     */
    public void updateSessionTitle(String sessionId, String title) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(title)) {
            return;
        }
        chatSessionDAO.updateTitle(sessionId, title);
    }

    private List<QaSourceItem> parseSources(String sourcesJson) {
        if (StringUtils.isBlank(sourcesJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(sourcesJson, new TypeReference<List<QaSourceItem>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
