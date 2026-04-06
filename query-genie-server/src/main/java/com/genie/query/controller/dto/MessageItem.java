package com.genie.query.controller.dto;

import com.genie.query.domain.agent.citation.CitationItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话消息项 DTO（用于会话详情中的消息列表）。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageItem {
    private String id;
    private String role;
    private String content;
    /** QA 模式引用来源（Qa.vue 兼容字段） */
    private List<QaSourceItem> sources;
    /** 统一引用数据（Agent 模式 / QA 模式转换后），前端统一使用此字段 */
    private List<CitationItem> citations;
}
