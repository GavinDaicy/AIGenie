package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.chat.dao.ChatMessageDAO;
import com.genie.query.domain.chat.model.ChatMessage;
import com.genie.query.infrastructure.dao.mysql.mapper.ChatMessageMapper;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 会话消息表 DAO 实现。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Repository
public class ChatMessageDaoImpl implements ChatMessageDAO {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Override
    public void insert(ChatMessage message) {
        if (message.getId() == null) {
            message.setId(SnowflakeIdUtils.getNextStringId());
        }
        if (message.getCreateTime() == null) {
            message.setCreateTime(new Date());
        }
        if (message.getSortOrder() == null) {
            message.setSortOrder(0);
        }
        chatMessageMapper.insert(message);
    }

    @Override
    public List<ChatMessage> listBySessionIdOrderBySortOrder(String sessionId, int limit) {
        return chatMessageMapper.listBySessionIdOrderBySortOrder(sessionId, limit);
    }

    @Override
    public int countBySessionId(String sessionId) {
        return chatMessageMapper.countBySessionId(sessionId);
    }

    @Override
    @Transactional
    public void deleteBySessionId(String sessionId) {
        chatMessageMapper.deleteBySessionId(sessionId);
    }

    @Override
    public ChatMessage findById(String id) {
        return chatMessageMapper.findById(id);
    }
}
