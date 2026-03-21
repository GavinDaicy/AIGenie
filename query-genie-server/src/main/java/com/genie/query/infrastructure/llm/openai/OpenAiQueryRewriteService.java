package com.genie.query.infrastructure.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.domain.query.service.QueryRewritePromptStrategy;
import com.genie.query.domain.query.service.QueryRewriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 OpenAI 接口的 Query 改写与多查询生成实现。
 *
 * 说明：
 * - 使用简单 HTTP 调用，不强依赖特定 SDK，便于在不同部署环境下接入。
 * - 若配置缺失或调用失败，将优雅降级为仅使用原始问题作为 mainQuery。
 */
@Service
@ConditionalOnProperty(prefix = "app.query-rewrite", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenAiQueryRewriteService implements QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQueryRewriteService.class);

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";

    private final OpenAiQueryRewriteProperties properties;
    private final QueryRewritePromptStrategy promptStrategy;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiQueryRewriteService(OpenAiQueryRewriteProperties properties,
                                     QueryRewritePromptStrategy promptStrategy) {
        this.properties = properties;
        this.promptStrategy = promptStrategy;
        this.restTemplate = createRestTemplate(properties.getTimeoutMs());
    }

    @Override
    public QueryRewriteResult generateQueries(String originalQuestion, int totalQueries, QueryRewriteContext context) {
        // 参数兜底与约束
        if (originalQuestion == null || originalQuestion.isBlank()) {
            return new QueryRewriteResult("", Collections.emptyList());
        }
        int safeTotal = Math.max(1, totalQueries);
        // 若未配置 API Key，则直接降级为原始问题
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("[QueryRewrite] apiKey not configured, fallback to original question only");
            return new QueryRewriteResult(originalQuestion.trim(), Collections.emptyList());
        }

        int expandedCount = Math.max(0, safeTotal - 1);
        try {
            String baseUrl = properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                    ? properties.getBaseUrl().trim()
                    : DEFAULT_OPENAI_BASE_URL;

            String url = baseUrl.endsWith("/")
                    ? baseUrl + "chat/completions"
                    : baseUrl + "/chat/completions";

            String systemPrompt = promptStrategy.buildSystemPrompt(expandedCount);
            String userPrompt = promptStrategy.buildUserPrompt(originalQuestion, context);

            Map<String, Object> body = Map.of(
                    "model", properties.getModel(),
                    "temperature", properties.getTemperature(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.getApiKey().trim());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class).getBody();
            if (resp == null || resp.isBlank()) {
                log.warn("[QueryRewrite] empty response from OpenAI, fallback to original question");
                return new QueryRewriteResult(originalQuestion.trim(), Collections.emptyList());
            }

            return parseResult(resp, originalQuestion, expandedCount);
        } catch (Exception e) {
            log.warn("[QueryRewrite] call OpenAI failed, fallback to original question: {}", e.getMessage());
            return new QueryRewriteResult(originalQuestion.trim(), Collections.emptyList());
        }
    }

    private RestTemplate createRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = timeoutMs > 0 ? timeoutMs : 10000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private QueryRewriteResult parseResult(String resp, String fallbackQuestion, int expectedExpandedCount) throws IOException {
        JsonNode root = objectMapper.readTree(resp);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.warn("[QueryRewrite] invalid choices in response, fallback");
            return new QueryRewriteResult(fallbackQuestion.trim(), Collections.emptyList());
        }
        JsonNode first = choices.get(0);
        JsonNode message = first.path("message");
        String content = null;
        if (message.isObject()) {
            content = message.path("content").asText(null);
        }
        if (content == null || content.isBlank()) {
            log.warn("[QueryRewrite] empty content in response, fallback");
            return new QueryRewriteResult(fallbackQuestion.trim(), Collections.emptyList());
        }
        // content 期望是 JSON 字符串
        JsonNode parsed;
        try {
            if (content.startsWith("```json")) {
                content = content.substring(7);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            parsed = objectMapper.readTree(content);
        } catch (Exception e) {
            log.warn("[QueryRewrite] content is not valid JSON, fallback to treating full content as main_query");
            return new QueryRewriteResult(content.trim(), Collections.emptyList());
        }

        String mainQuery = parsed.path("main_query").asText(null);
        if (mainQuery == null || mainQuery.isBlank()) {
            mainQuery = fallbackQuestion.trim();
        }

        List<String> expanded = new ArrayList<>();
        JsonNode expandedNode = parsed.path("expanded_queries");
        if (expandedNode.isArray()) {
            for (JsonNode n : expandedNode) {
                if (n.isTextual()) {
                    String q = n.asText().trim();
                    if (!q.isEmpty()) {
                        expanded.add(q);
                    }
                }
            }
        }
        if (expectedExpandedCount > 0 && expanded.size() > expectedExpandedCount) {
            expanded = expanded.subList(0, expectedExpandedCount);
        }
        return new QueryRewriteResult(mainQuery, expanded);
    }
}

