package com.genie.query.domain.agent.tool.sql.model;

import lombok.Data;

/**
 * SQL 生成结果：CoT 输出的思考过程 + SQL 语句。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class SqlGenerationResult {

    /** LLM 的查询思路描述（Chain-of-Thought 第一部分） */
    private String thought;

    /** 生成的 SQL 语句（Chain-of-Thought 第二部分） */
    private String sql;

    /** 是否为无法查询标记（LLM 返回 CANNOT_QUERY） */
    private boolean cannotQuery;

    public static SqlGenerationResult of(String thought, String sql) {
        SqlGenerationResult result = new SqlGenerationResult();
        result.setThought(thought);
        result.setSql(sql);
        result.setCannotQuery(false);
        return result;
    }

    public static SqlGenerationResult cannotQuery(String thought) {
        SqlGenerationResult result = new SqlGenerationResult();
        result.setThought(thought);
        result.setSql(null);
        result.setCannotQuery(true);
        return result;
    }
}
