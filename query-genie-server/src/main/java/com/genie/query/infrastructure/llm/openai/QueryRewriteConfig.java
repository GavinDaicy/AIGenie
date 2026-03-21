package com.genie.query.infrastructure.llm.openai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI Query 改写配置：启用 app.query-rewrite.openai 配置属性。
 */
@Configuration
@EnableConfigurationProperties(OpenAiQueryRewriteProperties.class)
public class QueryRewriteConfig {
}

