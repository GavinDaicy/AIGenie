package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.chat.model.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话消息表 Mapper。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Mapper
public interface ChatMessageMapper {

    void insert(ChatMessage message);

    List<ChatMessage> listBySessionIdOrderBySortOrder(@Param("sessionId") String sessionId, @Param("limit") int limit);

    int countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
