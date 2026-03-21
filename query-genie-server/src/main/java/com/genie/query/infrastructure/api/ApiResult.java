package com.genie.query.infrastructure.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;
    private String path;

    public static <T> ApiResult<T> success() {
        return ApiResult.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResult<T> success(T data) {
        return ApiResult.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResult<T> error(Integer code, String message) {
        return ApiResult.<T>builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResult<T> error(Integer code, String message, String path) {
        return ApiResult.<T>builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .path(path)
                .build();
    }
}