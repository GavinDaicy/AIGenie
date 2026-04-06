package com.genie.query.domain.agent.tool.sql.model;

import lombok.Data;

/**
 * SQL EXPLAIN 执行计划校验结果。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class ExplainResult {

    private boolean valid;
    private String error;

    public static ExplainResult valid() {
        ExplainResult r = new ExplainResult();
        r.setValid(true);
        return r;
    }

    public static ExplainResult invalid(String error) {
        ExplainResult r = new ExplainResult();
        r.setValid(false);
        r.setError(error);
        return r;
    }
}
