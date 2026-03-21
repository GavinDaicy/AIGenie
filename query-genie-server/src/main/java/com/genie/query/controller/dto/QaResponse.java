package com.genie.query.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能问答接口返回。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaResponse {
    /** 会话 ID（多轮时返回） */
    private String sessionId;
    /** 本轮助手消息 ID（多轮时返回，便于前端关联） */
    private String messageId;
    /** 大模型生成的答案 */
    private String answer;
    /** 引用来源列表 */
    private List<QaSourceItem> sources;
    /** 改写后的检索用问句（仅启用 Query 改写时有值，供前端展示） */
    private List<String> rewrittenQueries;
}
