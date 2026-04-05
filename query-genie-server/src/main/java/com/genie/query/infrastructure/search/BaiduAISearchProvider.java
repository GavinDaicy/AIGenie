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
 * 百度AI搜索（千帆平台）实现。
 *
 * <p>API 端点：POST https://qianfan.baidubce.com/v2/ai_search/web_search
 * <p>认证：X-Appbuilder-Authorization: Bearer {BAIDU_AI_SEARCH_API_KEY}
 *
 * @author daicy
 */
public class BaiduAISearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(BaiduAISearchProvider.class);
    private static final String ENDPOINT = "https://qianfan.baidubce.com/v2/ai_search/web_search";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final WebSearchProperties properties;

    public BaiduAISearchProvider(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                                 WebSearchProperties properties) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        String apiKey = properties.getProviders().getBaidu().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[BaiduAISearchProvider] BAIDU_AI_SEARCH_API_KEY 未配置，跳过搜索");
            return List.of();
        }

        try {
            List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", query));
            Map<String, Object> bodyMap = Map.of(
                    "messages", messages,
                    "version", "standard"
            );
            String bodyJson = objectMapper.writeValueAsString(bodyMap);

            Request request = new Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("X-Appbuilder-Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(bodyJson, JSON))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[BaiduAISearchProvider] 请求失败 | status={}", response.code());
                    return List.of();
                }
                String body = response.body().string();
                return parseResponse(body);
            }
        } catch (Exception e) {
            log.warn("[BaiduAISearchProvider] 搜索异常: {}", e.getMessage());
            return List.of();
        }
    }

    private List<WebSearchResult> parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<WebSearchResult> results = new ArrayList<>();

        JsonNode refs = root.path("references");
        if (!refs.isArray()) {
            log.warn("[BaiduAISearchProvider] 响应中无 references 字段");
            return results;
        }

        for (JsonNode item : refs) {
            String title = textOf(item, "title", "web_anchor");
            String url = textOf(item, "url");
            String snippet = textOf(item, "content");
            String date = textOf(item, "date");
            if (url != null && !url.isBlank()) {
                results.add(new WebSearchResult(title, url, snippet, "百度AI搜索", date));
            }
        }

        log.info("[BaiduAISearchProvider] 解析到 {} 条结果", results.size());
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
