package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.chat.dao.ChatSessionDAO;
import com.genie.query.domain.chat.model.ChatSession;
import com.genie.query.infrastructure.dao.mysql.mapper.ChatSessionMapper;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 会话表 DAO 实现。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Repository
public class ChatSessionDaoImpl implements ChatSessionDAO {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Override
    public void insert(ChatSession session) {
        if (session.getId() == null) {
            session.setId(SnowflakeIdUtils.getNextStringId());
        }
        if (session.getMode() == null) {
            session.setMode("RAG");
        }
        if (session.getCreateTime() == null) {
            session.setCreateTime(new Date());
        }
        if (session.getUpdateTime() == null) {
            session.setUpdateTime(new Date());
        }
        chatSessionMapper.insert(session);
    }

    @Override
    public ChatSession findById(String id) {
        return chatSessionMapper.findById(id);
    }

    @Override
    public List<ChatSession> listOrderByUpdateTimeDesc(int offset, int limit) {
        return chatSessionMapper.listOrderByUpdateTimeDesc(offset, limit);
    }

    @Override
    public void updateTitle(String id, String title) {
        chatSessionMapper.updateTitle(id, title);
    }

    @Override
    public void deleteById(String id) {
        chatSessionMapper.deleteById(id);
    }
}
