package com.genie.query.controller.dto;

import lombok.Data;

/**
 * 用户反馈请求 DTO。
 *
 * @author daicy
 * @date 2026/4/12
 */
@Data
public class FeedbackRequest {

    /** 关联的助手消息 ID（来自 FINAL_ANSWER SSE 事件中的 messageId） */
    private String messageId;

    /** 会话 ID */
    private String sessionId;

    /** 评分：1=好评，-1=差评 */
    private Integer rating;

    /** 用户文字反馈（可选） */
    private String comment;
}
