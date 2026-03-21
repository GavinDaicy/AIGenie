package com.genie.query.infrastructure.exception;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */

import com.genie.query.infrastructure.api.ResultCode;

/**
 * 业务异常基类
 */
public class BusinessException extends com.genie.query.domain.exception.BusinessException {

    public BusinessException(Integer code, String message) {
        super(code, message);
    }

    public BusinessException(Integer code, String message, Object data) {
        super(code, message, data);
    }

    public BusinessException(ResultCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage());
    }

    public BusinessException(String message) {
        super(ResultCode.INTERNAL_ERROR.getCode(), message);
    }
}