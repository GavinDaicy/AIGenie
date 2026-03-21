package com.genie.query.controller.dto;

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
    private List<QaSourceItem> sources;
}
