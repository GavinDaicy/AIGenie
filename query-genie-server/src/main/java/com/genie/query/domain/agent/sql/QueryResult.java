package com.genie.query.domain.agent.sql;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL 执行结果：列名列表 + 行数据。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class QueryResult {

    /** 列名列表（保持原始顺序） */
    private List<String> columns;

    /** 数据行（每行为 列名→值 的 Map） */
    private List<Map<String, Object>> rows;

    /** 实际执行耗时（毫秒） */
    private long executionTimeMs;

    public boolean isEmpty() {
        return rows == null || rows.isEmpty();
    }

    public int rowCount() {
        return rows == null ? 0 : rows.size();
    }

    public static QueryResult of(List<String> columns, List<Map<String, Object>> rows, long executionTimeMs) {
        QueryResult r = new QueryResult();
        r.setColumns(columns);
        r.setRows(rows);
        r.setExecutionTimeMs(executionTimeMs);
        return r;
    }
}
