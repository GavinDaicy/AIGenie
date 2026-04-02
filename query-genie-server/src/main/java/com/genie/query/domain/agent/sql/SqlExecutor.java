package com.genie.query.domain.agent.sql;

/**
 * SQL 执行器领域接口（Step 4）：EXPLAIN 语法验证 + 带超时控制的 SQL 执行。
 * 由 infrastructure 层提供具体实现（DynamicDataSourceManager + JDBC）。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface SqlExecutor {

    /**
     * 通过 EXPLAIN 验证 SQL 语法合法性（不实际执行）。
     *
     * @param sql          待验证的 SQL
     * @param datasourceId 目标数据源 ID
     * @return 验证结果；若语法有误则包含错误信息
     */
    ExplainResult explain(String sql, Long datasourceId);

    /**
     * 执行 SQL 并返回结果集。
     *
     * @param sql          待执行的 SQL（已经过安全校验和 LIMIT 处理）
     * @param datasourceId 目标数据源 ID
     * @return 查询结果（列名列表 + 行数据 + 耗时）
     * @throws com.genie.query.domain.exception.QueryTimeoutException 查询超时时抛出
     */
    QueryResult execute(String sql, Long datasourceId);
}
