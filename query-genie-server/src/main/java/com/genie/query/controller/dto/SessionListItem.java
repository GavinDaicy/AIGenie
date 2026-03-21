package com.genie.query.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话列表项 DTO。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionListItem {
    private String id;
    private String title;
    private Date updateTime;
}
