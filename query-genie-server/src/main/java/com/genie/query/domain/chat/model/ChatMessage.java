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
    /** user / assistant / ask_user */
    private String role;
    private String content;
    /** QA 模式引用来源 JSON（List<QaSourceItem>），仅 assistant 消息有 */
    private String sources;
    /** Agent 模式统一引用数据 JSON（List<CitationItem>），仅 assistant 消息有 */
    private String citationsJson;
    private Integer sortOrder;
    private Date createTime;
}
