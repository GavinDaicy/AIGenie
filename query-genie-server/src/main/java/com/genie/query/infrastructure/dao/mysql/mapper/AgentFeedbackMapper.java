package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.agent.model.AgentFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 用户评分反馈 Mapper。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Mapper
public interface AgentFeedbackMapper {

    void insert(AgentFeedback feedback);

    AgentFeedback findByMessageId(@Param("messageId") String messageId);

    List<AgentFeedback> listLowRating(@Param("rating") int rating, @Param("limit") int limit);
}
