package com.genie.query.infrastructure.api;

import com.alibaba.fastjson.JSON;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一返回值处理
 */
@ControllerAdvice(basePackages = {"com.genie.query.controller"
})
public class GlobalResponseAdvice implements ResponseBodyAdvice {
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResult || body instanceof ResponseEntity || body instanceof SseEmitter) {
            return body;
        }
        if (body instanceof String) {
            return JSON.toJSONString(ApiResult.success(body));
        }
        return ApiResult.success(body);
    }
}
