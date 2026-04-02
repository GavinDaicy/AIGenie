package com.genie.query.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent 问答请求 DTO。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class AgentAskRequest {

    /** 用户问题（必填） */
    private String question;

    /** 会话 ID（可选，传入则携带历史上下文） */
    private String sessionId;

    /** 可访问的知识库编码列表（可选，为空则使用全部） */
    private List<String> knowledgeCodes;

    /** 可访问的数据源 ID 列表（可选，为空则不使用 SQL 工具） */
    private List<Long> datasourceIds;
}
