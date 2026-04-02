package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.agent.dao.AgentFeedbackDAO;
import com.genie.query.domain.agent.model.AgentFeedback;
import com.genie.query.infrastructure.dao.mysql.mapper.AgentFeedbackMapper;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Agent 用户评分反馈 DAO 实现。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Repository
public class AgentFeedbackDaoImpl implements AgentFeedbackDAO {

    @Autowired
    private AgentFeedbackMapper agentFeedbackMapper;

    @Override
    public void insert(AgentFeedback feedback) {
        if (feedback.getId() == null) {
            feedback.setId(SnowflakeIdUtils.getNextId());
        }
        if (feedback.getCreatedAt() == null) {
            feedback.setCreatedAt(new Date());
        }
        agentFeedbackMapper.insert(feedback);
    }

    @Override
    public AgentFeedback findByMessageId(String messageId) {
        return agentFeedbackMapper.findByMessageId(messageId);
    }

    @Override
    public List<AgentFeedback> listLowRating(int rating, int limit) {
        return agentFeedbackMapper.listLowRating(rating, limit);
    }
}
