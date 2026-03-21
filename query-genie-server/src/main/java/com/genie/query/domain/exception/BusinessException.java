package com.genie.query.domain.exception;

/**
 * 领域业务异常：由领域与应用层抛出，统一携带业务码与消息。
 */
public class BusinessException extends RuntimeException {
    private static final int DEFAULT_CODE = 500;

    private final Integer code;
    private final Object data;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public BusinessException(Integer code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public BusinessException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
        this.data = null;
    }

    public Integer getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
