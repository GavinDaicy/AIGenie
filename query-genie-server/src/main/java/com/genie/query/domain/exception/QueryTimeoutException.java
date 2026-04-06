package com.genie.query.domain.exception;

/**
 * SQL 查询超时异常。
 *
 * @author daicy
 * @date 2026/4/2
 */
public class QueryTimeoutException extends RuntimeException {

    public QueryTimeoutException(String message) {
        super(message);
    }

    public QueryTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
