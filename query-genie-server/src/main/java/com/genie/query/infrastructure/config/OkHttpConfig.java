package com.genie.query.infrastructure.config;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        // 创建日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // 设置日志级别
        ;
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)      // 连接超时
                .readTimeout(30, TimeUnit.SECONDS)         // 读取超时
                .writeTimeout(30, TimeUnit.SECONDS)        // 写入超时
                .retryOnConnectionFailure(true)            // 自动重试
                .addInterceptor(loggingInterceptor)        // 添加日志拦截器
                .build();
    }
}