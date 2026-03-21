package com.genie.query.domain.document.model;

import lombok.Data;

import java.util.Date;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Data
public class Document {
    // 基础信息
    private String id;
    private String name;
    private String originUri;
    private String description;
    private String knowledgeCode;

    // 状态信息
    private DocStatus status;
    private DocType type;
    private DocCategory category;

    // 对象存储信息
    private String objectStorePathOrigin;
    private String objectStorePathParsed;

    // 时间信息
    private Date createTime;
    private Date updateTime;
}
