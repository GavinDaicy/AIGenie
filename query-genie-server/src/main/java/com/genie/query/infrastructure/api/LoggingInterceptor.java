package com.genie.query.infrastructure.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@Slf4j
public class LoggingInterceptor implements HandlerInterceptor {

    public static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 记录请求开始时间
        request.setAttribute(START_TIME, System.currentTimeMillis());

        // 记录请求信息
        log.info("Request - URI: {}, Method: {}, Parameters: {}, Headers: {}",
                request.getRequestURI(),
                request.getMethod(),
                request.getQueryString(),
                getHeaders(request));

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // 可以在这里记录响应信息（可选）
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 计算请求耗时
        Long startTime = (Long) request.getAttribute(START_TIME);
        long duration = System.currentTimeMillis() - startTime;

        // 记录请求完成信息
        log.info("Response - URI: {}, Status: {}, Duration: {}ms",
                request.getRequestURI(),
                response.getStatus(),
                duration);

        if (ex != null) {
            log.error("Request failed with exception: ", ex);
        }
    }

    private String getHeaders(HttpServletRequest request) {
        StringBuilder headers = new StringBuilder();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            headers.append(headerName).append("=").append(request.getHeader(headerName)).append(", ");
        });
        return headers.length() > 0 ? headers.substring(0, headers.length() - 2) : "";
    }
}