package com.genie.query.infrastructure.consts;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
public class RedisKey {
    // 知识库信息锁
    public static final String KNOWLEDGE_INFO_KEY = "genie:knowledge:info.lock";
    // 文档信息锁
    public static final String DOCUMENT_INFO_KEY = "genie:document:info.lock";
    // 文档知识关系锁
    public static final String DOCUMENT_KNOWLEDGE_MAPPING_KEY = "genie:document:knowledge.Mapping.lock";
}
