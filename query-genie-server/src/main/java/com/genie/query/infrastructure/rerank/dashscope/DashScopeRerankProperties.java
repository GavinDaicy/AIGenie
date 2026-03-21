package com.genie.query.infrastructure.rerank.dashscope;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rerank 配置项，对应 application.yml 中 app.rerank.*。
 * API Key 可复用 spring.ai.dashscope.api-key，此处可选覆盖。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@ConfigurationProperties(prefix = "app.rerank")
public class DashScopeRerankProperties {

    /** 是否启用 rerank 能力（全局开关） */
    private boolean enabled = false;
    /** DashScope 文本排序模型名，如 gte-rerank-v2、qwen3-rerank */
    private String model = "gte-rerank-v2";
    /** 初检条数 = size * candidateFactor，再 rerank 到 size；1 表示不放大 */
    private int candidateFactor = 1;
    /** 可选：Rerank API Key，不填则使用 spring.ai.dashscope.api-key */
    private String apiKey;
}
