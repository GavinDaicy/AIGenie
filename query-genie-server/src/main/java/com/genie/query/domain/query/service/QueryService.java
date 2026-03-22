package com.genie.query.domain.query.service;

import com.genie.query.domain.cache.CacheService;
import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.document.service.DocumentService;
import com.genie.query.domain.etlpipeline.embedding.EmbeddingService;
import com.genie.query.domain.exception.BusinessException;
import com.genie.query.domain.knowledge.model.Knowledge;
import com.genie.query.domain.knowledge.model.TimeDecayConfig;
import com.genie.query.domain.knowledge.model.TimeDecayFieldSource;
import com.genie.query.domain.knowledge.model.TimeDecayType;
import com.genie.query.domain.knowledge.service.KnowledgeService;
import com.genie.query.domain.query.model.QueryResultEntry;
import com.genie.query.domain.vectorstore.ChunkSearchHit;
import com.genie.query.domain.vectorstore.SearchMode;
import com.genie.query.domain.vectorstore.TimeDecayParam;
import com.genie.query.domain.vectorstore.VectorSearchParam;
import com.genie.query.domain.rerank.RerankService;
import com.genie.query.domain.vectorstore.VectorStore;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 查询领域服务：具备完整查询能力。负责知识库解析、检索字段推导、检索模式解析、调用向量库检索并补全文档信息。
 * 多知识库检索时，每个知识库各保留 size 条结果。不依赖 controller 层，入参为原始类型。
 *
 * @author daicy
 * @date 2026/2/9
 */
@Service
public class QueryService {

    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private DocumentService documentService;
    @Autowired(required = false)
    private RerankService rerankService;
    @Autowired(required = false)
    private CacheService cacheService;

    @Value("${app.search.embedding-cache.enabled:true}")
    private boolean embeddingCacheEnabled;
    @Value("${app.search.embedding-cache.ttl-ms:15000}")
    private long embeddingCacheTtlMs;

    /**
     * 执行多知识库检索：准备参数、调用向量库、按 hit 补全文档信息并返回领域结果。
     * 多个知识库时，每个知识库各保留最多 size 条结果（总条数 = 各知识库结果数之和）。
     *
     * @param queryText      查询关键词/问句（已校验非空）
     * @param requestedMode  请求的检索方式
     * @param size           每个知识库返回条数上限
     * @param knowledgeCodes 指定知识库编码，为空则检索所有具备字段配置的知识库
     * @return 检索结果列表（领域模型），可为空
     */
    public List<QueryResultEntry> search(String queryText, SearchMode requestedMode, int size,
                                         List<String> knowledgeCodes) throws IOException {
        return search(queryText, requestedMode, size, knowledgeCodes, true, false, size, null);
    }

    /**
     * 执行多知识库检索（可指定是否归一化得分）。normalizeScore 为 false 时返回 ES 原始分，便于对比。
     */
    public List<QueryResultEntry> search(String queryText, SearchMode requestedMode, int size,
                                         List<String> knowledgeCodes, boolean normalizeScore) throws IOException {
        return search(queryText, requestedMode, size, knowledgeCodes, normalizeScore, false, size, null);
    }

    /**
     * 执行多知识库检索，支持可选 rerank：当 enableRerank 为 true 时，先按 searchSize 初检，再经 RerankService 重排后保留 topN 条。
     *
     * @param searchSize 初检时每知识库条数上限（enableRerank 时由调用方设为 size * candidateFactor）
     * @param enableRerank 是否对初检结果做 rerank
     * @param topN 最终返回条数上限（rerank 时取前 topN，未 rerank 时即 searchSize 条）
     */
    public List<QueryResultEntry> search(String queryText, SearchMode requestedMode, int searchSize,
                                         List<String> knowledgeCodes, boolean normalizeScore,
                                         boolean enableRerank, int topN,
                                         Boolean useTimeDecay) throws IOException {
        VectorSearchParam param = prepareSearchParam(queryText, requestedMode, searchSize, knowledgeCodes, normalizeScore, useTimeDecay);
        if (param.getKnowledgeCodes() == null || param.getKnowledgeCodes().isEmpty()) {
            return List.of();
        }
        List<ChunkSearchHit> hits = vectorStore.search(param);
        boolean didRerank = false;
        if (enableRerank && rerankService != null && !hits.isEmpty() && topN > 0) {
            hits = rerankService.rerank(queryText, hits, topN);
            didRerank = true;
        }
        List<QueryResultEntry> result = new ArrayList<>(hits.size());
        for (ChunkSearchHit hit : hits) {
            Document doc = null;
            if (hit.getDocId() != null) {
                doc = documentService.getDocumentById(hit.getDocId());
            }
            // Rerank 返回 0~1 的 relevance_score；当用户开启得分归一化时统一缩放到 0~10，与 ES 初检展示一致
            double score = hit.getScore();
            if (didRerank && normalizeScore) {
                score = score * 10;
            }
            result.add(QueryResultEntry.builder()
                    .knowledgeCode(hit.getKnowledgeCode())
                    .score(score)
                    .chunkContent(hit.getChunkContent())
                    .document(doc)
                    .build());
        }
        return result;
    }

    /**
     * 仅执行向量库检索，不做 rerank、不补全文档。用于多查询合并后统一 rerank 的场景。
     *
     * @param queryText      查询关键词/问句
     * @param requestedMode  检索模式
     * @param searchSize     每知识库检索条数上限
     * @param knowledgeCodes 知识库编码列表
     * @param normalizeScore 是否归一化初检得分
     * @return 原始命中列表（ChunkSearchHit），未做 rerank
     */
    public List<ChunkSearchHit> searchHitsOnly(String queryText, SearchMode requestedMode, int searchSize,
                                               List<String> knowledgeCodes, boolean normalizeScore) throws IOException {
        return searchHitsOnly(queryText, requestedMode, searchSize, knowledgeCodes, normalizeScore, null);
    }

    public List<ChunkSearchHit> searchHitsOnly(String queryText, SearchMode requestedMode, int searchSize,
                                               List<String> knowledgeCodes, boolean normalizeScore,
                                               Boolean useTimeDecay) throws IOException {
        VectorSearchParam param = prepareSearchParam(queryText, requestedMode, searchSize, knowledgeCodes, normalizeScore, useTimeDecay);
        if (param.getKnowledgeCodes() == null || param.getKnowledgeCodes().isEmpty()) {
            return List.of();
        }
        return vectorStore.search(param);
    }

    /**
     * 将 ChunkSearchHit 列表转换为 QueryResultEntry 列表（补全文档信息）。
     * 若 scaleRerankScoreToTen 为 true，会将 0~1 的 rerank 得分缩放到 0~10 便于展示。
     *
     * @param hits                  命中列表（通常为 rerank 后的结果）
     * @param scaleRerankScoreToTen 是否将得分乘以 10（rerank 模型返回 0~1 时使用）
     * @return 领域结果列表
     */
    public List<QueryResultEntry> buildEntriesFromHits(List<ChunkSearchHit> hits, boolean scaleRerankScoreToTen) {
        List<QueryResultEntry> result = new ArrayList<>(hits.size());
        for (ChunkSearchHit hit : hits) {
            Document doc = null;
            if (hit.getDocId() != null) {
                doc = documentService.getDocumentById(hit.getDocId());
            }
            double score = hit.getScore();
            if (scaleRerankScoreToTen) {
                score = score * 10;
            }
            result.add(QueryResultEntry.builder()
                    .knowledgeCode(hit.getKnowledgeCode())
                    .score(score)
                    .chunkContent(hit.getChunkContent())
                    .document(doc)
                    .build());
        }
        return result;
    }

    /**
     * 准备检索参数：解析知识库列表、推导检索字段、解析检索模式（含降级与校验）、必要时生成查询向量，并构建 VectorSearchParam。
     *
     * @param queryText     查询关键词/问句（已校验非空）
     * @param requestedMode 请求的检索方式
     * @param size          返回条数上限
     * @param knowledgeCodes 指定知识库编码，为空则检索所有具备字段配置的知识库
     * @return 可直接用于 VectorStore.search 的参数
     */
    public VectorSearchParam prepareSearchParam(String queryText, SearchMode requestedMode, int size,
                                                List<String> knowledgeCodes) throws IOException {
        return prepareSearchParam(queryText, requestedMode, size, knowledgeCodes, true);
    }

    public VectorSearchParam prepareSearchParam(String queryText, SearchMode requestedMode, int size,
                                                List<String> knowledgeCodes, boolean normalizeScore) throws IOException {
        return prepareSearchParam(queryText, requestedMode, size, knowledgeCodes, normalizeScore, null);
    }

    public VectorSearchParam prepareSearchParam(String queryText, SearchMode requestedMode, int size,
                                                List<String> knowledgeCodes, boolean normalizeScore,
                                                Boolean useTimeDecay) throws IOException {
        List<Knowledge> knowledgeList = resolveKnowledgeListForSearch(knowledgeCodes);
        if (knowledgeList.isEmpty()) {
            return VectorSearchParam.builder()
                    .knowledgeCodes(List.of())
                    .keywordQuery(queryText)
                    .queryVector(null)
                    .mode(requestedMode)
                    .textFieldsPerIndex(Map.of())
                    .vectorFieldsPerIndex(Map.of())
                    .size(size)
                    .normalizeScore(normalizeScore)
                    .useTimeDecay(useTimeDecay)
                    .timeDecayPerIndex(Map.of())
                    .build();
        }

        List<String> codes = knowledgeList.stream().map(Knowledge::getCode).toList();
        Map<String, List<String>> textFieldsPerIndex = new LinkedHashMap<>();
        Map<String, List<String>> vectorFieldsPerIndex = new LinkedHashMap<>();
        Map<String, Map<String, Double>> fieldBoostsPerIndex = new LinkedHashMap<>();
        buildSearchFieldConfig(knowledgeList, textFieldsPerIndex, vectorFieldsPerIndex, fieldBoostsPerIndex);
        Map<String, TimeDecayParam> timeDecayPerIndex = buildTimeDecayPerIndex(knowledgeList, useTimeDecay);

        SearchMode resolvedMode = resolveSearchMode(requestedMode, !vectorFieldsPerIndex.isEmpty(), !textFieldsPerIndex.isEmpty());
        if (resolvedMode == SearchMode.KEYWORD && textFieldsPerIndex.isEmpty()) {
            throw new BusinessException("所选知识库未配置全文检索字段，无法使用关键字检索");
        }

        String trimmedQuery = queryText.trim();
        float[] queryVector = null;
        if (resolvedMode == SearchMode.VECTOR || resolvedMode == SearchMode.HYBRID) {
            queryVector = embedWithShortTtlCache(trimmedQuery);
        }

        return VectorSearchParam.builder()
                .knowledgeCodes(codes)
                .keywordQuery(trimmedQuery)
                .queryVector(queryVector)
                .mode(resolvedMode)
                .textFieldsPerIndex(textFieldsPerIndex)
                .vectorFieldsPerIndex(vectorFieldsPerIndex)
                .fieldBoostsPerIndex(fieldBoostsPerIndex)
                .size(size)
                .normalizeScore(normalizeScore)
                .useTimeDecay(useTimeDecay)
                .timeDecayPerIndex(timeDecayPerIndex)
                .build();
    }

    /**
     * 对查询向量做短 TTL Redis 缓存，降低同问句在短时间内（流式/非流式、重试）重复 embedding 成本。
     */
    private float[] embedWithShortTtlCache(String queryText) {
        if (!embeddingCacheEnabled || embeddingCacheTtlMs <= 0 || cacheService == null) {
            return embeddingService.embed(queryText);
        }
        String cacheKey = buildEmbeddingCacheKey(queryText);
        try {
            String cachedRaw = cacheService.get(cacheKey);
            float[] cached = parseVector(cachedRaw);
            if (cached != null) {
                return cached;
            }
        } catch (Exception ignore) {
            // 缓存异常不影响主流程
        }

        float[] vector = embeddingService.embed(queryText);
        try {
            cacheService.set(cacheKey, encodeVector(vector), embeddingCacheTtlMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignore) {
            // 缓存异常不影响主流程
        }
        return vector;
    }

    private String buildEmbeddingCacheKey(String queryText) {
        return "query:embedding:" + sha256(queryText);
    }

    private String encodeVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(vector.length * 10);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private float[] parseVector(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String[] parts = raw.split(",");
        float[] vec = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Float.parseFloat(parts[i]);
            }
            return vec;
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private Map<String, TimeDecayParam> buildTimeDecayPerIndex(List<Knowledge> knowledgeList, Boolean requestUseTimeDecay) {
        Map<String, TimeDecayParam> out = new LinkedHashMap<>();
        if (knowledgeList == null || knowledgeList.isEmpty()) {
            return out;
        }
        for (Knowledge kl : knowledgeList) {
            if (kl == null || StringUtils.isBlank(kl.getCode())) {
                continue;
            }
            TimeDecayConfig cfg = kl.getTimeDecayConfig();
            boolean enabledByDefault = cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
            boolean enabled = requestUseTimeDecay != null ? Boolean.TRUE.equals(requestUseTimeDecay) : enabledByDefault;
            if (!enabled || cfg == null) {
                out.put(kl.getCode(), TimeDecayParam.builder().enabled(false).build());
                continue;
            }

            String timeField = null;
            if (cfg.getFieldSource() == TimeDecayFieldSource.SYSTEM_DOC_UPDATE_TIME) {
                timeField = "doc_update_time";
            } else if (cfg.getFieldSource() == TimeDecayFieldSource.BUSINESS_FIELD) {
                String fieldKey = cfg.getFieldKey();
                if (StringUtils.isNotBlank(fieldKey) && kl.getFields() != null) {
                    boolean ok = kl.getFields().stream().anyMatch(f -> f != null
                            && fieldKey.equals(f.getFieldKey())
                            && f.getType() == com.genie.query.domain.knowledge.model.KLFieldType.DATE);
                    if (ok) {
                        timeField = fieldKey;
                    }
                }
            }
            if (StringUtils.isBlank(timeField)) {
                out.put(kl.getCode(), TimeDecayParam.builder().enabled(false).build());
                continue;
            }

            Long scaleMillis = parseDurationMillis(cfg.getScale());
            Long offsetMillis = parseDurationMillis(cfg.getOffset());
            String decayType = cfg.getDecayType() != null ? cfg.getDecayType().name() : TimeDecayType.EXPONENTIAL.name();

            out.put(kl.getCode(), TimeDecayParam.builder()
                    .enabled(true)
                    .timeField(timeField)
                    .decayType(decayType)
                    .scaleMillis(scaleMillis)
                    .offsetMillis(offsetMillis)
                    .decay(cfg.getDecay())
                    .floor(cfg.getFloor())
                    .applyThreshold(cfg.getApplyThreshold())
                    .lambda(cfg.getLambda())
                    .sigma(cfg.getSigma())
                    .build());
        }
        return out;
    }

    /**
     * 解析简单的时间跨度表达式为毫秒。支持：ms/s/m/h/d 后缀；纯数字视为毫秒。
     * 例如：\"30d\"、\"7d\"、\"12h\"、\"90m\"、\"10s\"、\"500ms\"。
     */
    private static Long parseDurationMillis(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String s = raw.trim().toLowerCase();
        try {
            if (s.endsWith("ms")) {
                return Long.parseLong(s.substring(0, s.length() - 2).trim());
            }
            if (s.endsWith("s")) {
                long v = Long.parseLong(s.substring(0, s.length() - 1).trim());
                return TimeUnit.SECONDS.toMillis(v);
            }
            if (s.endsWith("m")) {
                long v = Long.parseLong(s.substring(0, s.length() - 1).trim());
                return TimeUnit.MINUTES.toMillis(v);
            }
            if (s.endsWith("h")) {
                long v = Long.parseLong(s.substring(0, s.length() - 1).trim());
                return TimeUnit.HOURS.toMillis(v);
            }
            if (s.endsWith("d")) {
                long v = Long.parseLong(s.substring(0, s.length() - 1).trim());
                return TimeUnit.DAYS.toMillis(v);
            }
            return Long.parseLong(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 解析要参与检索的知识库列表：未指定则返回所有具备字段配置的知识库；指定则校验存在并过滤出有字段配置的。
     */
    public List<Knowledge> resolveKnowledgeListForSearch(List<String> knowledgeCodes) {
        List<Knowledge> all = knowledgeService.queryKnowledgeList();
        List<Knowledge> withFields = all.stream()
                .filter(k -> k.getFields() != null && !k.getFields().isEmpty())
                .filter(QueryService::isRetrievalEnabled)
                .collect(Collectors.toList());

        if (knowledgeCodes == null || knowledgeCodes.isEmpty()) {
            return withFields;
        }
        List<String> codes = knowledgeCodes.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .toList();
        if (codes.isEmpty()) {
            return withFields;
        }

        List<Knowledge> selected = new ArrayList<>();
        for (String code : codes) {
            Knowledge k = knowledgeService.getKnowledgeByCode(code);
            if (k == null) {
                throw new BusinessException("知识库不存在: " + code);
            }
            if (!isRetrievalEnabled(k)) {
                throw new BusinessException("知识库已禁用，无法参与检索: " + code);
            }
            if (k.getFields() != null && !k.getFields().isEmpty()) {
                selected.add(k);
            }
        }
        return selected;
    }

    /** null 或未显式关闭时视为可检索（兼容旧数据与默认启用） */
    private static boolean isRetrievalEnabled(Knowledge k) {
        return k == null || k.getEnabled() == null || Boolean.TRUE.equals(k.getEnabled());
    }

    /**
     * 根据知识库列表推导每个知识库的全文检索字段与语义检索字段配置。
     * 每个知识库下所有 semanticSearchable 的字段组成该库的向量字段列表。
     */
    public void buildSearchFieldConfig(List<Knowledge> knowledgeList,
                                       Map<String, List<String>> textFieldsPerIndex,
                                       Map<String, List<String>> vectorFieldsPerIndex,
                                       Map<String, Map<String, Double>> fieldBoostsPerIndex) {
        textFieldsPerIndex.clear();
        if (vectorFieldsPerIndex != null) {
            vectorFieldsPerIndex.clear();
        }
        if (fieldBoostsPerIndex != null) {
            fieldBoostsPerIndex.clear();
        }
        for (Knowledge kl : knowledgeList) {
            if (kl.getFields() == null) {
                continue;
            }
            Map<String, Double> boosts = new LinkedHashMap<>();
            List<String> textFields = kl.getFields().stream()
                    .filter(f -> Boolean.TRUE.equals(f.getFullTextSearchable()))
                    .map(field -> {
                        double boost = field.getBoost() != null && field.getBoost() > 0 ? field.getBoost() : 1.0d;
                        boosts.put(field.getFieldKey(), boost);
                        return field.getFieldKey() + "^" + boost;
                    })
                    .toList();
            if (!textFields.isEmpty()) {
                textFieldsPerIndex.put(kl.getCode(), textFields);
            }
            List<String> vectorFields = kl.getFields().stream()
                    .filter(f -> Boolean.TRUE.equals(f.getSemanticSearchable()))
                    .map(field -> {
                        double boost = field.getBoost() != null && field.getBoost() > 0 ? field.getBoost() : 1.0d;
                        boosts.putIfAbsent(field.getFieldKey(), boost);
                        return field.getFieldKey();
                    })
                    .toList();
            if (!vectorFields.isEmpty() && vectorFieldsPerIndex != null) {
                vectorFieldsPerIndex.put(kl.getCode(), vectorFields);
            }
            if (!boosts.isEmpty() && fieldBoostsPerIndex != null) {
                fieldBoostsPerIndex.put(kl.getCode(), boosts);
            }
        }
    }

    /**
     * 解析实际使用的检索模式：若请求向量检索但无语义字段则降级为关键字检索；若仅关键字且无全文字段由调用方校验并抛异常。
     */
    public SearchMode resolveSearchMode(SearchMode requestedMode, boolean hasVectorField, boolean hasTextField) {
        if (requestedMode == SearchMode.VECTOR || requestedMode == SearchMode.HYBRID) {
            if (!hasVectorField) {
                if (requestedMode == SearchMode.VECTOR) {
                    throw new BusinessException("所选知识库未配置语义检索字段，无法使用向量检索");
                }
                return SearchMode.KEYWORD;
            }
        }
        return requestedMode;
    }
}
