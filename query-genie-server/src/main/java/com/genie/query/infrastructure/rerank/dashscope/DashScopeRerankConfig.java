package com.genie.query.infrastructure.rerank.dashscope;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Rerank 相关 Bean：配置属性、RestTemplate（供 DashScope 调用）。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Configuration
@EnableConfigurationProperties(DashScopeRerankProperties.class)
public class DashScopeRerankConfig {

    @Bean
    public RestTemplate rerankRestTemplate() {
        return new RestTemplate();
    }
}
