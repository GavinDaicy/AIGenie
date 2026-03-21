package com.genie.query.infrastructure.api;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ResultCode {
    // 通用错误码
    SUCCESS(200, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "方法不允许"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码（示例）
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_DISABLED(1002, "用户已被禁用"),
    INVALID_TOKEN(1003, "无效的令牌"),
    DUPLICATE_DATA(1004, "数据已存在"),

    // 更多业务错误码...
    ;

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}