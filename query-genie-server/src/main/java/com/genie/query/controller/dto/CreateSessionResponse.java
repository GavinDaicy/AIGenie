package com.genie.query.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建会话接口返回。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionResponse {
    private String sessionId;
    private String title;
}
