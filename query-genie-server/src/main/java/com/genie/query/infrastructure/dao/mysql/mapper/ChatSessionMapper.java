package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.chat.model.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话表 Mapper。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Mapper
public interface ChatSessionMapper {

    void insert(ChatSession session);

    ChatSession findById(String id);

    List<ChatSession> listOrderByUpdateTimeDesc(@Param("offset") int offset, @Param("limit") int limit);

    void updateTitle(@Param("id") String id, @Param("title") String title);

    void deleteById(String id);
}
