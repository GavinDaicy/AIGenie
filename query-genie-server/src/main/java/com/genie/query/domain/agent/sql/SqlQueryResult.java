package com.genie.query.domain.agent.sql;

import lombok.Data;

/**
 * SQL 查询最终结果（自修正循环出口）：封装执行状态、SQL、查询结果和思考过程。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class SqlQueryResult {

    public enum Status {
        SUCCESS,
        CANNOT_QUERY,
        SECURITY_FAIL,
        EMPTY_RESULT,
        TIMEOUT,
        MAX_RETRIES_EXCEEDED
    }

    private Status status;
    private String sql;
    private String thought;
    private QueryResult queryResult;
    private String errorMessage;

    /** 格式化后的 Markdown 结果文本（ResultFormatter 输出，仅 SUCCESS 状态有值） */
    private String formattedText;

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public static SqlQueryResult success(String sql, QueryResult result, String thought) {
        SqlQueryResult r = new SqlQueryResult();
        r.setStatus(Status.SUCCESS);
        r.setSql(sql);
        r.setQueryResult(result);
        r.setThought(thought);
        return r;
    }

    public static SqlQueryResult cannotQuery() {
        SqlQueryResult r = new SqlQueryResult();
        r.setStatus(Status.CANNOT_QUERY);
        r.setErrorMessage("该问题无法通过数据库查询回答，请尝试其他方式");
        return r;
    }

    public static SqlQueryResult securityFail(String reason) {
        SqlQueryResult r = new SqlQueryResult();
        r.setStatus(Status.SECURITY_FAIL);
        r.setErrorMessage("SQL 安全校验失败: " + reason);
        return r;
    }

    public static SqlQueryResult emptyResult(String sql, String message) {
        SqlQueryResult r = new SqlQueryResult();
        r.setStatus(Status.EMPTY_RESULT);
        r.setSql(sql);
        r.setErrorMessage(message);
        return r;
    }

    public static SqlQueryResult timeout(String sql) {
        SqlQueryResult r = new SqlQueryResult();
        r.setStatus(Status.TIMEOUT);
        r.setSql(sql);
        r.setErrorMessage("SQL 查询超时（超过10秒），建议缩小查询范围或优化条件");
        return r;
    }

    public static SqlQueryResult maxRetriesExceeded(String message) {
        SqlQueryResult r = new SqlQueryResult();
        r.setStatus(Status.MAX_RETRIES_EXCEEDED);
        r.setErrorMessage(message);
        return r;
    }
}
