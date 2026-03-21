package com.genie.query.infrastructure.config;

import com.genie.query.infrastructure.api.LoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册日志拦截器，可以指定拦截路径
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/**")  // 拦截所有请求
                .excludePathPatterns("/monitor")  // 排除监控接口
        ;
    }
}