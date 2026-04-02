package com.genie.query.domain.chat.model;

import lombok.Data;

import java.util.Date;

/**
 * 智能问答会话实体。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
public class ChatSession {
    private String id;
    private String title;
    /** 知识库编码列表，JSON 或逗号分隔 */
    private String knowledgeCodes;
    /** 问答模式: RAG | AGENT */
    private String mode;
    private Date createTime;
    private Date updateTime;
}
