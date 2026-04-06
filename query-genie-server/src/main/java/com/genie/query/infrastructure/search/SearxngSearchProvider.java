package com.genie.query.infrastructure.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.domain.agent.search.WebSearchProvider;
import com.genie.query.domain.agent.search.WebSearchResult;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * SearXNG 自托管元搜索引擎实现（免费，无需 API Key）。
 *
 * <p>部署方式：{@code docker run -d -p 8080:8080 searxng/searxng}
 * <p>API 端点：GET http://{SEARXNG_BASE_URL}/search?q={query}&format=json
 * <p>适合开发/测试环境，生产环境建议使用商业 Provider。
 *
 * @author daicy
 */
public class SearxngSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SearxngSearchProvider.class);

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final WebSearchProperties properties;

    public SearxngSearchProvider(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                                 WebSearchProperties properties) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        String baseUrl = properties.getProviders().getSearxng().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }

        try {
            HttpUrl url = HttpUrl.parse(baseUrl + "/search").newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("format", "json")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[SearxngSearchProvider] 请求失败 | status={} | 请确认 SearXNG 已启动: docker run -d -p 8080:8080 searxng/searxng",
                            response.code());
                    return List.of();
                }
                String body = response.body().string();
                return parseResponse(body, maxResults);
            }
        } catch (Exception e) {
            log.warn("[SearxngSearchProvider] 搜索异常（请确认 SearXNG 服务已启动）: {}", e.getMessage());
            return List.of();
        }
    }

    private List<WebSearchResult> parseResponse(String body, int maxResults) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<WebSearchResult> results = new ArrayList<>();

        JsonNode resultArray = root.path("results");
        if (!resultArray.isArray()) {
            log.warn("[SearxngSearchProvider] 响应中无 results 字段");
            return results;
        }

        int count = 0;
        for (JsonNode item : resultArray) {
            if (count >= maxResults) break;
            String title = textOf(item, "title");
            String url = textOf(item, "url");
            String snippet = textOf(item, "content");
            String engine = textOf(item, "engine", "engines");
            String published = textOf(item, "publishedDate");
            if (url != null && !url.isBlank()) {
                results.add(new WebSearchResult(title, url, snippet, "SearXNG/" + engine, published));
                count++;
            }
        }

        log.info("[SearxngSearchProvider] 解析到 {} 条结果", results.size());
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
