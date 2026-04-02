package com.genie.query.domain.agent.dao;

import com.genie.query.domain.agent.model.AgentFeedback;

import java.util.List;

/**
 * Agent 用户评分反馈数据访问接口。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface AgentFeedbackDAO {

    void insert(AgentFeedback feedback);

    AgentFeedback findByMessageId(String messageId);

    List<AgentFeedback> listLowRating(int rating, int limit);
}
