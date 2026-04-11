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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OrioSearch 自托管搜索 API 实现（免费，无需 API Key）。
 *
 * <p>OrioSearch 是开源的 Tavily 兼容搜索服务，内部基于 SearXNG 聚合 70+ 搜索引擎，
 * 额外提供 Redis 缓存、熔断重试、语义重排序等生产级特性。
 *
 * <p>部署方式：
 * <pre>
 *   git clone https://github.com/vkfolio/orio-search
 *   cd orio-search
 *   docker compose up --build
 * </pre>
 *
 * <p>API 端点：{@code POST http://{ORIOSEARCH_BASE_URL}/search}
 * <p>健康检查：{@code GET http://{ORIOSEARCH_BASE_URL}/health}
 *
 * @author daicy
 */
public class OrioSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(OrioSearchProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final WebSearchProperties properties;

    public OrioSearchProvider(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                              WebSearchProperties properties) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        String baseUrl = properties.getProviders().getOrioSearch().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("[OrioSearchProvider] ORIOSEARCH_BASE_URL 未配置，跳过搜索。" +
                     "请先部署 OrioSearch: https://github.com/vkfolio/orio-search");
            return List.of();
        }

        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("query", query);
            bodyMap.put("max_results", maxResults);
            String bodyJson = objectMapper.writeValueAsString(bodyMap);

            Request request = new Request.Builder()
                    .url(baseUrl.stripTrailing() + "/search")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[OrioSearchProvider] 请求失败 | status={} | 请确认 OrioSearch 已启动: docker compose up --build",
                            response.code());
                    return List.of();
                }
                String body = response.body().string();
                return parseResponse(body, maxResults);
            }
        } catch (Exception e) {
            log.warn("[OrioSearchProvider] 搜索异常（请确认 OrioSearch 服务已启动，默认端口 8000）: {}", e.getMessage());
            return List.of();
        }
    }

    private List<WebSearchResult> parseResponse(String body, int maxResults) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<WebSearchResult> results = new ArrayList<>();

        JsonNode resultArray = root.path("results");
        if (!resultArray.isArray()) {
            log.warn("[OrioSearchProvider] 响应中无 results 字段，原始内容: {}",
                    body.length() > 200 ? body.substring(0, 200) : body);
            return results;
        }

        int count = 0;
        for (JsonNode item : resultArray) {
            if (count >= maxResults) break;
            String title = textOf(item, "title");
            String url = textOf(item, "url");
            String snippet = textOf(item, "content");
            String source = "OrioSearch";
            String published = textOf(item, "published_date", "publishedDate");
            if (url != null && !url.isBlank()) {
                results.add(new WebSearchResult(title, url, snippet, source, published));
                count++;
            }
        }

        log.info("[OrioSearchProvider] 解析到 {} 条结果", results.size());
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
