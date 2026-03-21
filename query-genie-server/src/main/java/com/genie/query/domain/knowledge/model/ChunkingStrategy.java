package com.genie.query.domain.knowledge.model;

/**
 * 知识库分块策略枚举。
 * <p>
 * RECURSIVE_TEXT：长文本递归切块（按段落/长度 + 重叠）；
 * MARKDOWN_HEADING：按 Markdown 标题/章节切块，再在章节内做长度控制；
 * ROW_BASED：按行切块（Excel/CSV，一行一块）；
 * ROW_GROUPED：按多行聚合切块（预留，未来可实现）。
 *
 * @author daicy
 * @date 2026/3/9
 */
public enum ChunkingStrategy {

    /**
     * 默认长文本递归切块。
     */
    RECURSIVE_TEXT,

    /**
     * Markdown 标题/章节感知切块。
     */
    MARKDOWN_HEADING,

    /**
     * 按行切块（Excel/CSV）。
     */
    ROW_BASED,

    /**
     * 按多行聚合切块（预留）。
     */
    ROW_GROUPED
}

