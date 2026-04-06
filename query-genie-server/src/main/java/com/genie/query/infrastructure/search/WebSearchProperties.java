package com.genie.query.infrastructure.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 联网搜索配置属性，对应 application.yml 中 app.web-search 节点。
 *
 * @author daicy
 */
@Component
@ConfigurationProperties(prefix = "app.web-search")
public class WebSearchProperties {

    private boolean enabled = true;
    private String provider = "bocha";
    private int maxResults = 5;
    private Providers providers = new Providers();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public Providers getProviders() { return providers; }
    public void setProviders(Providers providers) { this.providers = providers; }

    public static class Providers {
        private Bocha bocha = new Bocha();
        private Baidu baidu = new Baidu();
        private AliIqs aliIqs = new AliIqs();
        private Searxng searxng = new Searxng();

        public Bocha getBocha() { return bocha; }
        public void setBocha(Bocha bocha) { this.bocha = bocha; }

        public Baidu getBaidu() { return baidu; }
        public void setBaidu(Baidu baidu) { this.baidu = baidu; }

        public AliIqs getAliIqs() { return aliIqs; }
        public void setAliIqs(AliIqs aliIqs) { this.aliIqs = aliIqs; }

        public Searxng getSearxng() { return searxng; }
        public void setSearxng(Searxng searxng) { this.searxng = searxng; }

        public static class Bocha {
            private String apiKey = "";
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        }

        public static class Baidu {
            private String apiKey = "";
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        }

        public static class AliIqs {
            private String apiKey = "";
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        }

        public static class Searxng {
            private String baseUrl = "http://localhost:8080";
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        }
    }
}
