package com.genie.query.domain.chat.model;

import lombok.Data;

import java.util.Date;

/**
 * 智能问答消息实体。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
public class ChatMessage {
    private String id;
    private String sessionId;
    /** user / assistant */
    private String role;
    private String content;
    /** 引用来源 JSON 字符串，仅 assistant 有 */
    private String sources;
    private Integer sortOrder;
    private Date createTime;
}
