package com.genie.query.infrastructure.exception;

import com.genie.query.infrastructure.api.ApiResult;
import com.genie.query.infrastructure.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<?>> handleBusinessException(BusinessException e,
                                                                HttpServletRequest request) {
        log.warn("业务异常: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.OK) // 业务异常通常返回200，错误码在body中
                .body(ApiResult.error(e.getCode(), e.getMessage(), request.getRequestURI()));
    }

    /**
     * 处理参数校验异常（@Validated）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<?>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("参数校验异常", e);

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.error(ResultCode.PARAM_ERROR.getCode(), message, request.getRequestURI()));
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<?>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少请求参数异常", e);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.error(ResultCode.PARAM_ERROR.getCode(),
                                String.format("缺少必要参数: %s", e.getParameterName()),
                                request.getRequestURI()));
    }

    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<?>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        log.warn("媒体类型不支持", e);

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResult.error(415,
                                String.format("媒体类型 %s 不支持，支持的媒体类型: %s",
                                        e.getContentType(), e.getSupportedMediaTypes()),
                                request.getRequestURI()));
    }

    /**
     * 处理 Spring Security 认证异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<?>> handleAccessDeniedException(
            AccessDeniedException e, HttpServletRequest request) {
        log.warn("访问被拒绝", e);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResult.error(ResultCode.FORBIDDEN.getCode(),
                                "权限不足，访问被拒绝",
                                request.getRequestURI()));
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<?>> handleException(Exception e,
                                                     HttpServletRequest request) {
        log.error("系统内部异常", e);

        // 生产环境隐藏详细错误信息
        String message = "系统内部错误，请联系管理员";
        if (Objects.equals("dev", request.getServletContext().getInitParameter("spring.profiles.active"))) {
            message = e.getMessage();
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(ResultCode.INTERNAL_ERROR.getCode(), message, request.getRequestURI()));
    }
}