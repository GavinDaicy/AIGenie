package com.genie.query.domain.common;

/**
 * 领域锁键常量，避免领域层依赖基础设施常量定义。
 */
public final class LockKeys {

    private LockKeys() {
    }

    public static final String KNOWLEDGE_INFO_KEY = "genie:knowledge:info.lock";
    public static final String DOCUMENT_INFO_KEY = "genie:document:info.lock";
    public static final String DOCUMENT_KNOWLEDGE_MAPPING_KEY = "genie:document:knowledge.Mapping.lock";
}
