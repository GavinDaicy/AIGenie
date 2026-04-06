package com.genie.query.infrastructure.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.domain.agent.search.WebSearchProvider;
import com.genie.query.domain.agent.search.WebSearchResult;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 博查AI Search API 实现。
 *
 * <p>API 端点：POST https://api.bochaai.com/v1/web-search
 * <p>认证：Authorization: Bearer {BOCHA_API_KEY}
 *
 * @author daicy
 */
public class BochaSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(BochaSearchProvider.class);
    private static final String ENDPOINT = "https://api.bochaai.com/v1/web-search";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final WebSearchProperties properties;

    public BochaSearchProvider(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                               WebSearchProperties properties) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        String apiKey = properties.getProviders().getBocha().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[BochaSearchProvider] BOCHA_API_KEY 未配置，跳过搜索");
            return List.of();
        }

        try {
            String bodyJson = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
                put("query", query);
                put("freshness", "oneYear");
                put("summary", true);
                put("count", maxResults);
            }});

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[BochaSearchProvider] 请求失败 | status={}", response.code());
                    return List.of();
                }
                String body = response.body().string();
                return parseResponse(body);
            }
        } catch (Exception e) {
            log.warn("[BochaSearchProvider] 搜索异常: {}", e.getMessage());
            return List.of();
        }
    }

    private List<WebSearchResult> parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<WebSearchResult> results = new ArrayList<>();

        JsonNode data = root.path("data");
        JsonNode webPages = data.path("webPages").path("value");
        if (webPages.isArray()) {
            for (JsonNode item : webPages) {
                String title = textOf(item, "name", "title");
                String url = textOf(item, "url");
                String snippet = textOf(item, "snippet", "description");
                String siteName = textOf(item, "siteName", "provider");
                String published = textOf(item, "datePublished", "dateLastCrawled");
                if (url != null && !url.isBlank()) {
                    results.add(new WebSearchResult(title, url, snippet, siteName, published));
                }
            }
        }

        if (results.isEmpty()) {
            JsonNode refs = root.path("references");
            if (refs.isArray()) {
                for (JsonNode item : refs) {
                    String title = textOf(item, "title", "name");
                    String url = textOf(item, "url");
                    String snippet = textOf(item, "snippet", "content");
                    String siteName = textOf(item, "siteName");
                    String published = textOf(item, "datePublished");
                    if (url != null && !url.isBlank()) {
                        results.add(new WebSearchResult(title, url, snippet, siteName, published));
                    }
                }
            }
        }

        log.info("[BochaSearchProvider] 解析到 {} 条结果", results.size());
        return results;
    }

    private String textOf(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.path(field);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }
}
