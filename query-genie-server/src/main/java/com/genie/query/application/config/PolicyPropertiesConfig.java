package com.genie.query.application.config;

import com.genie.query.domain.qa.config.QaPolicyProperties;
import com.genie.query.domain.query.config.QueryRewritePolicyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 业务策略与 LLM 供应商配置注册。
 */
@Configuration
@EnableConfigurationProperties({
        QaPolicyProperties.class,
        QueryRewritePolicyProperties.class
})
public class PolicyPropertiesConfig {
}
