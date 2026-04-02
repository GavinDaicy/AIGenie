package com.genie.query.domain.schema.model;

import lombok.Data;

/**
 * 示例问答对（对应 db_table_schema.sample_queries 中每个元素）。
 * 用于 Text-to-SQL Few-shot 示例。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class SampleQuery {
    /** 自然语言问题 */
    private String question;
    /** 对应的 SQL 语句 */
    private String sql;
}
