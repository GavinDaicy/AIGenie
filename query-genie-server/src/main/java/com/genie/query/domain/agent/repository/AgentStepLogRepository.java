package com.genie.query.domain.agent.repository;

import com.genie.query.domain.agent.model.AgentStepLog;

import java.util.List;

/**
 * Agent 步骤日志数据访问接口。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface AgentStepLogRepository {

    void insert(AgentStepLog stepLog);

    List<AgentStepLog> listBySessionId(String sessionId);

    List<AgentStepLog> listByMessageId(String messageId);

    void updateMessageId(String sessionId, String messageId);
}
