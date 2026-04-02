package com.genie.query.domain.schema.model;

import lombok.Data;

import java.util.Date;

/**
 * 数据源注册实体。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class DbDatasource {
    private Long id;
    private String name;
    private String description;
    private String dbUrl;
    private String dbUsername;
    /** 数据库密码（AES加密存储） */
    private String dbPassword;
    /** 状态: 1=启用 0=禁用 */
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
}
