package com.genie.query.controller.dto;

import lombok.Data;

/**
 * 数据源新增/更新请求。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class DatasourceRequest {
    private String name;
    private String description;
    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
    /** 状态: 1=启用 0=禁用 */
    private Integer status;
}
