package com.genie.query.domain.vectorstore;

/**
 * 检索方式：关键字检索、向量检索、混合检索（关键字 + 向量）
 *
 * @author daicy
 * @date 2026/2/8
 */
public enum SearchMode {
    /** 关键字检索（全文/关键词） */
    KEYWORD,
    /** 向量检索（语义相似度） */
    VECTOR,
    /** 混合检索：关键字 + 向量 */
    HYBRID
}
