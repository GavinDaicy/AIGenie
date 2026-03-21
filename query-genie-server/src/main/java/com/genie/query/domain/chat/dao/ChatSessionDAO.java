package com.genie.query.domain.chat.dao;

import com.genie.query.domain.chat.model.ChatSession;

import java.util.List;

/**
 * 会话数据访问接口。
 *
 * @author daicy
 * @date 2026/3/8
 */
public interface ChatSessionDAO {

    void insert(ChatSession session);

    ChatSession findById(String id);

    List<ChatSession> listOrderByUpdateTimeDesc(int offset, int limit);

    void updateTitle(String id, String title);

    void deleteById(String id);
}
