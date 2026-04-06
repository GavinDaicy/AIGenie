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

    /** 工具强制控制（可选，用于对比测试不同工具组合的效果） */
    private ToolForce toolForce;

    /**
     * 工具强制控制开关：true=强制调用，false=强制禁用，null=跟随自动路由。
     */
    @Data
    public static class ToolForce {
        /** 联网搜索（searchWeb）：true=强制调用，false=强制禁用 */
        private Boolean webSearch;
        /** 知识库检索（searchKnowledge）：true=强制调用，false=强制禁用 */
        private Boolean knowledge;
        /** SQL检索（querySql）：true=强制调用，false=强制禁用 */
        private Boolean sql;
    }
}
