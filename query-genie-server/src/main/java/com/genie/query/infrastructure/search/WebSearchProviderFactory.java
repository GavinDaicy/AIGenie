package com.genie.query.infrastructure.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.domain.agent.search.WebSearchProvider;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据 {@code app.web-search.provider} 配置创建对应的 {@link WebSearchProvider} Bean。
 *
 * <p>支持的 provider 值：
 * <ul>
 *   <li>{@code bocha}   - 博查AI（默认）</li>
 *   <li>{@code baidu}   - 百度AI搜索（千帆）</li>
 *   <li>{@code ali-iqs} - 阿里云IQS</li>
 *   <li>{@code searxng} - SearXNG自托管（免费）</li>
 * </ul>
 *
 * <p>当 {@code app.web-search.enabled=false} 时不创建任何 Bean。
 *
 * @author daicy
 */
@Configuration
public class WebSearchProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(WebSearchProviderFactory.class);

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebSearchProperties properties;

    @Bean
    @ConditionalOnProperty(prefix = "app.web-search", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebSearchProvider webSearchProvider() {
        String provider = properties.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = "bocha";
        }

        log.info("[WebSearchProviderFactory] 初始化联网搜索 Provider: {}", provider);

        switch (provider.toLowerCase()) {
            case "baidu":
                return new BaiduAISearchProvider(okHttpClient, objectMapper, properties);
            case "ali-iqs":
                return new AliIQSSearchProvider(okHttpClient, objectMapper, properties);
            case "searxng":
                return new SearxngSearchProvider(okHttpClient, objectMapper, properties);
            case "bocha":
            default:
                if (!"bocha".equalsIgnoreCase(provider)) {
                    log.warn("[WebSearchProviderFactory] 未知 provider: {}，降级使用博查AI", provider);
                }
                return new BochaSearchProvider(okHttpClient, objectMapper, properties);
        }
    }
}
