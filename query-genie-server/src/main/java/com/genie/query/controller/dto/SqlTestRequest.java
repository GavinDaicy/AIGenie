package com.genie.query.controller.dto;

import lombok.Data;

/**
 * SQL 端到端测试接口请求体。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class SqlTestRequest {

    /** 自然语言问题 */
    private String question;

    /** 目标数据源 ID */
    private Long datasourceId;
}
