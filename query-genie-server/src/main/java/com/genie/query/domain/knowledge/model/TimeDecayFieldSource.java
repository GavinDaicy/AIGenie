package com.genie.query.domain.knowledge.model;

/**
 * 时间衰减字段来源
 *
 * <p>BUSINESS_FIELD：使用知识库 schema 中配置的日期字段</p>
 * <p>SYSTEM_DOC_UPDATE_TIME：使用系统维护的文档更新时间</p>
 *
 * @author daicy
 * @date 2026/3/17
 */
public enum TimeDecayFieldSource {
    BUSINESS_FIELD,
    SYSTEM_DOC_UPDATE_TIME
}

