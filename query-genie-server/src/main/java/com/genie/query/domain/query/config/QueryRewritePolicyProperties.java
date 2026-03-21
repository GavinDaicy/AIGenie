package com.genie.query.domain.query.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Query 改写业务策略配置（与具体供应商无关）。
 */
@Data
@ConfigurationProperties(prefix = "app.query-rewrite")
public class QueryRewritePolicyProperties {
    /**
     * 是否默认启用 Query 改写。
     */
    private boolean enabled = true;

    /**
     * 默认生成的查询总数（包含 1 个主要查询），至少为 1。
     */
    private int defaultTotalQueries = 4;

    /**
     * 允许的最大查询总数上限，防止滥用。
     */
    private int maxTotalQueries = 8;
}
