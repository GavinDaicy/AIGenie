package com.genie.query.infrastructure.llm.openai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI Query 改写供应商配置。
 */
@Data
@ConfigurationProperties(prefix = "app.query-rewrite.openai")
public class OpenAiQueryRewriteProperties {
    /**
     * OpenAI API Key。
     */
    private String apiKey;

    /**
     * OpenAI Base URL，可选（例如自建代理），不配置则使用官方默认地址。
     */
    private String baseUrl;

    /**
     * 使用的模型名称。
     */
    private String model = "gpt-4.1-mini";

    /**
     * 采样温度。
     */
    private double temperature = 0.3;

    /**
     * 请求超时时间（毫秒）。
     */
    private int timeoutMs = 10000;
}
