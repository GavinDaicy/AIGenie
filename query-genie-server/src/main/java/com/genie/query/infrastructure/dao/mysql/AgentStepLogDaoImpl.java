package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.agent.repository.AgentStepLogRepository;
import com.genie.query.domain.agent.model.AgentStepLog;
import com.genie.query.infrastructure.dao.mysql.mapper.AgentStepLogMapper;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Agent 步骤日志 DAO 实现。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Repository
public class AgentStepLogDaoImpl implements AgentStepLogRepository {

    @Autowired
    private AgentStepLogMapper agentStepLogMapper;

    @Override
    public void insert(AgentStepLog stepLog) {
        if (stepLog.getId() == null) {
            stepLog.setId(SnowflakeIdUtils.getNextId());
        }
        if (stepLog.getCreatedAt() == null) {
            stepLog.setCreatedAt(new Date());
        }
        agentStepLogMapper.insert(stepLog);
    }

    @Override
    public List<AgentStepLog> listBySessionId(String sessionId) {
        return agentStepLogMapper.listBySessionId(sessionId);
    }

    @Override
    public List<AgentStepLog> listByMessageId(String messageId) {
        return agentStepLogMapper.listByMessageId(messageId);
    }

    @Override
    public void updateMessageId(String sessionId, String messageId) {
        agentStepLogMapper.updateMessageId(sessionId, messageId);
    }
}
