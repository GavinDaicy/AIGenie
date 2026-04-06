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
import java.util.Map;

/**
 * 阿里云 IQS（信息查询服务）通用搜索实现。
 *
 * <p>API 端点：POST https://iqs.aliyuncs.com/v2/api/search/unified
 * <p>认证：Authorization: Bearer {ALI_IQS_API_KEY}
 *
 * @author daicy
 */
public class AliIQSSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(AliIQSSearchProvider.class);
    private static final String ENDPOINT = "https://iqs.aliyuncs.com/v2/api/search/unified";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final WebSearchProperties properties;

    public AliIQSSearchProvider(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                                WebSearchProperties properties) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        String apiKey = properties.getProviders().getAliIqs().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[AliIQSSearchProvider] ALI_IQS_API_KEY 未配置，跳过搜索");
            return List.of();
        }

        try {
            Map<String, Object> bodyMap = Map.of(
                    "query", query,
                    "timeRange", "OneYear",
                    "engineType", "Generic",
                    "contents", Map.of("rerankScore", true)
            );
            String bodyJson = objectMapper.writeValueAsString(bodyMap);

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[AliIQSSearchProvider] 请求失败 | status={}", response.code());
                    return List.of();
                }
                String body = response.body().string();
                return parseResponse(body);
            }
        } catch (Exception e) {
            log.warn("[AliIQSSearchProvider] 搜索异常: {}", e.getMessage());
            return List.of();
        }
    }

    private List<WebSearchResult> parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<WebSearchResult> results = new ArrayList<>();

        JsonNode pageItems = root.path("pageItems");
        if (!pageItems.isArray()) {
            log.warn("[AliIQSSearchProvider] 响应中无 pageItems 字段");
            return results;
        }

        for (JsonNode item : pageItems) {
            String title = textOf(item, "title");
            String url = textOf(item, "link", "url");
            String snippet = textOf(item, "snippet");
            String published = textOf(item, "publishedTime");
            if (url != null && !url.isBlank()) {
                results.add(new WebSearchResult(title, url, snippet, "阿里IQS", published));
            }
        }

        log.info("[AliIQSSearchProvider] 解析到 {} 条结果", results.size());
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
