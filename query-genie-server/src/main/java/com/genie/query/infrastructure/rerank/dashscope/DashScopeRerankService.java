package com.genie.query.infrastructure.rerank.dashscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.genie.query.domain.rerank.RerankService;
import com.genie.query.domain.vectorstore.ChunkSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 DashScope 文本排序 API 的 Rerank 实现。
 * 请求体格式（gte-rerank-v2）：model、input.query、input.documents、parameters.top_n。
 * 失败时返回原列表并保留原分数，保证降级可用。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Service
@ConditionalOnProperty(name = "app.rerank.enabled", havingValue = "true")
public class DashScopeRerankService implements RerankService {

    private static final String RERANK_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
    private static final int MAX_DOCUMENTS_PER_REQUEST = 500;
    private static final Logger log = LoggerFactory.getLogger(DashScopeRerankService.class);

    private final RestTemplate restTemplate;
    private final DashScopeRerankProperties properties;
    private final String effectiveApiKey;

    public DashScopeRerankService(
            @Qualifier("rerankRestTemplate") RestTemplate restTemplate,
            DashScopeRerankProperties properties,
            @Value("${spring.ai.dashscope.api-key:}") String dashscopeApiKey) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.effectiveApiKey = (properties.getApiKey() != null && !properties.getApiKey().isBlank())
                ? properties.getApiKey().trim()
                : (dashscopeApiKey != null ? dashscopeApiKey.trim() : "");
    }

    @Override
    public List<ChunkSearchHit> rerank(String query, List<ChunkSearchHit> hits, int topN) {
        if (hits == null || hits.isEmpty() || topN <= 0) {
            return hits == null ? List.of() : hits;
        }
        List<ChunkSearchHit> toUse = hits.size() > MAX_DOCUMENTS_PER_REQUEST
                ? new ArrayList<>(hits.subList(0, MAX_DOCUMENTS_PER_REQUEST))
                : new ArrayList<>(hits);
        List<String> documents = toUse.stream()
                .map(this::extractTextFromChunk)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);
        body.put("input", input);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("top_n", Math.min(topN, toUse.size()));
        parameters.put("return_documents", false);
        body.put("parameters", parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!effectiveApiKey.isEmpty()) {
            headers.setBearerAuth(effectiveApiKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            JsonNode root = restTemplate.postForObject(RERANK_URL, entity, JsonNode.class);
            if (root == null) {
                log.warn("DashScope rerank 返回为空，使用初检结果");
                return hits;
            }
            JsonNode output = root.get("output");
            if (output == null) {
                JsonNode code = root.get("code");
                if (code != null && !code.asText().isEmpty()) {
                    log.warn("DashScope rerank 错误: code={}, message={}", code.asText(), root.path("message").asText(""));
                }
                return hits;
            }
            JsonNode results = output.get("results");
            if (results == null || !results.isArray()) {
                return hits;
            }
            List<ChunkSearchHit> ordered = new ArrayList<>(results.size());
            for (JsonNode item : results) {
                int index = item.has("index") ? item.get("index").asInt(-1) : -1;
                double score = item.has("relevance_score") ? item.get("relevance_score").asDouble(0d) : 0d;
                if (index >= 0 && index < toUse.size()) {
                    ChunkSearchHit hit = toUse.get(index);
                    ordered.add(ChunkSearchHit.builder()
                            .knowledgeCode(hit.getKnowledgeCode())
                            .docId(hit.getDocId())
                            .score(score)
                            .chunkContent(hit.getChunkContent())
                            .build());
                }
            }
            if (ordered.isEmpty()) {
                return hits;
            }
            return ordered;
        } catch (RestClientException e) {
            log.warn("DashScope rerank 调用异常，使用初检结果: {}", e.getMessage());
            return hits;
        }
    }

    /**
     * 从 ChunkSearchHit 的 chunkContent（Map）中拼接出用于 rerank 的文本：
     * 所有字符串值按 key 顺序拼接，过滤 null 与非字符串。
     */
    private String extractTextFromChunk(ChunkSearchHit hit) {
        Map<String, Object> content = hit.getChunkContent();
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() instanceof String)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> (String) e.getValue())
                .collect(Collectors.joining(" "));
    }
}
