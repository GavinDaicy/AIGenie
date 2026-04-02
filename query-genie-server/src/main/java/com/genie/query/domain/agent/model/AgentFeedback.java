package com.genie.query.domain.agent.model;

import lombok.Data;

import java.util.Date;

/**
 * Agent 用户评分反馈实体。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class AgentFeedback {
    private Long id;
    /** 关联消息ID（chat_message.id） */
    private String messageId;
    /** 关联会话ID（chat_session.id） */
    private String sessionId;
    /** 评分: 1=好评 -1=差评 */
    private Integer rating;
    /** 用户反馈文字（可选） */
    private String comment;
    private Date createdAt;
}
