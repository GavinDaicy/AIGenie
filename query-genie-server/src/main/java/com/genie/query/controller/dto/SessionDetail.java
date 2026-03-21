package com.genie.query.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 会话详情 DTO（含消息列表）。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDetail {
    private String id;
    private String title;
    private String knowledgeCodes;
    private Date createTime;
    private Date updateTime;
    private List<MessageItem> messages;
}
