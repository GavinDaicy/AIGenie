package com.genie.query.domain.chat.dao;

import com.genie.query.domain.chat.model.ChatMessage;

import java.util.List;

/**
 * 会话消息数据访问接口。
 *
 * @author daicy
 * @date 2026/3/8
 */
public interface ChatMessageDAO {

    void insert(ChatMessage message);

    List<ChatMessage> listBySessionIdOrderBySortOrder(String sessionId, int limit);

    int countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
