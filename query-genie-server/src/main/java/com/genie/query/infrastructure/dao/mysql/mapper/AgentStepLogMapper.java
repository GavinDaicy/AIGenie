package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.agent.model.AgentStepLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 步骤日志 Mapper。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Mapper
public interface AgentStepLogMapper {

    void insert(AgentStepLog stepLog);

    List<AgentStepLog> listBySessionId(@Param("sessionId") String sessionId);

    List<AgentStepLog> listByMessageId(@Param("messageId") String messageId);

    void updateMessageId(@Param("sessionId") String sessionId, @Param("messageId") String messageId);
}
