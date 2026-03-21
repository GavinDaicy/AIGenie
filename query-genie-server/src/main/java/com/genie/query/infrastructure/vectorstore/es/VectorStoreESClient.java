package com.genie.query.infrastructure.vectorstore.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.genie.query.domain.knowledge.model.KLField;
import com.genie.query.domain.vectorstore.ChunkSearchHit;
import com.genie.query.domain.vectorstore.SearchMode;
import com.genie.query.domain.vectorstore.TimeDecayParam;
import com.genie.query.domain.vectorstore.VectorSearchParam;
import com.genie.query.domain.vectorstore.VectorStore;
import com.genie.query.infrastructure.exception.BusinessException;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量库 Elasticsearch 实现，仅做向量库的增删改查，不依赖 Chunk/IndexChunk 等领域模型。
 *
 * @author daicy
 * @date 2026/1/9
 */
@Slf4j
@Component
public class VectorStoreESClient implements VectorStore {

    private static final String DOC_ID_FIELD = "doc_id";
    private static final String DOC_UPDATE_TIME_FIELD = KLFieldMappingBuilder.DOC_UPDATE_TIME_FIELD;
    /**
     * RRF 融合中的 k 参数，控制高排位结果的衰减速度。
     * 经验值通常在 10~60 之间，这里采用 60，兼顾不同检索通道的稳定性。
     */
    private static final double RRF_K = 60.0d;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    /**
     * 单索引检索上下文：仅承载该索引下用于检索的字段配置，供三种检索模式复用。
     */
    private static class IndexSearchContext {
        final List<String> textFields;
        final List<String> vectorFieldsEs;
        final List<Double> vectorFieldWeights;
        final TimeDecayParam timeDecayParam;

        IndexSearchContext(List<String> textFields, List<String> vectorFieldsEs, List<Double> vectorFieldWeights, TimeDecayParam timeDecayParam) {
            this.textFields = textFields;
            this.vectorFieldsEs = vectorFieldsEs;
            this.vectorFieldWeights = vectorFieldWeights;
            this.timeDecayParam = timeDecayParam;
        }
    }

    @Override
    public void createVectorDB(String dbName, List<KLField> klFieldList) throws IOException {
        checkVectorDBExist(dbName);
        klFieldList.sort((o1, o2) -> o1.getOrd() - o2.getOrd());
        Map<String, Property> properties = KLFieldMappingBuilder.buildProperties(klFieldList);
        log.info("createVectorDB: {}", properties);
        elasticsearchClient.indices().create(builder -> builder
                .index(dbName)
                .mappings(mapping -> mapping
                        .properties(properties)));
    }

    @Override
    public void deleteVectorDB(String code) throws IOException {
        if (!elasticsearchClient.indices().exists(builder -> builder.index(code)).value()) {
            return;
        }
        elasticsearchClient.indices().delete(builder -> builder.index(code));
    }

    @Override
    public void batchAddDocuments(String knowledgeCode, String docId, List<Map<String, Object>> documents) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<BulkOperation> ops = new ArrayList<>(documents.size());
        for (Map<String, Object> doc : documents) {
            String chunkId = SnowflakeIdUtils.getNextStringId();
            Map<String, Object> esDoc = toEsDocument(docId, doc);
            ops.add(BulkOperation.of(b -> b.index(IndexOperation.of(i -> i
                    .index(knowledgeCode)
                    .id(chunkId)
                    .document(esDoc)))));
        }
        BulkRequest bulkRequest = BulkRequest.of(r -> r.index(knowledgeCode).operations(ops));
        BulkResponse response = elasticsearchClient.bulk(bulkRequest);
        if (response.errors()) {
            response.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> {
                        var err = item.error();
                        log.error("ES bulk 写入失败: id={}, error={}", item.id(), err != null ? err.reason() : "unknown");
                    });
            throw new IOException("ES bulk 写入存在失败项");
        }
        log.info("向量库批量写入完成。knowledgeCode={}, docId={}, count={}", knowledgeCode, docId, documents.size());
    }

    @Override
    public void deleteByDocId(String knowledgeCode, String docId) throws IOException {
        elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                .index(knowledgeCode)
                .query(Query.of(q -> q.term(t -> t.field(DOC_ID_FIELD).value(v -> v.stringValue(docId)))))));
        log.info("按 doc_id 删除完成。knowledgeCode={}, docId={}", knowledgeCode, docId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listChunksByDocId(String knowledgeCode, String docId) throws IOException {
        BooleanResponse exists = elasticsearchClient.indices().exists(builder -> builder.index(knowledgeCode));
        if (!exists.value()) {
            return List.of();
        }
        SearchResponse<Map<String, Object>> response = elasticsearchClient.search(
                SearchRequest.of(s -> s
                        .index(knowledgeCode)
                        .size(10000)
                        .query(Query.of(q -> q.term(t -> t.field(DOC_ID_FIELD).value(v -> v.stringValue(docId)))))),
                (Class<Map<String, Object>>) (Class<?>) Map.class);
        List<Map<String, Object>> result = new ArrayList<>();
        if (response.hits().hits() == null) {
            return result;
        }
        for (Hit<Map<String, Object>> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) {
                continue;
            }
            Map<String, Object> chunk = source.entrySet().stream()
                    .filter(e -> e.getKey() != null && !e.getKey().endsWith("_vector_system"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
            result.add(chunk);
        }
        return result;
    }

    /**
     * 多知识库检索入口：按参数中的知识库列表依次检索，合并结果后按 score 降序返回。
     * 具体模式（KEYWORD / VECTOR / HYBRID）在单索引内按 {@link SearchMode} 分发，三种实现抽象层次一致。
     */
    @Override
    public List<ChunkSearchHit> search(VectorSearchParam param) throws IOException {
        if (param == null || param.getKnowledgeCodes() == null || param.getKnowledgeCodes().isEmpty()) {
            return List.of();
        }
        List<ChunkSearchHit> allHits = new ArrayList<>();
        for (String index : param.getKnowledgeCodes()) {
            allHits.addAll(searchSingleIndex(param, index));
        }
        allHits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return allHits;
    }

    /**
     * 针对单个 ES 索引执行检索：校验索引存在后解析上下文，按 {@link SearchMode} 分发到对应检索实现。
     */
    private List<ChunkSearchHit> searchSingleIndex(VectorSearchParam param, String index) throws IOException {
        BooleanResponse exists = elasticsearchClient.indices().exists(builder -> builder.index(index));
        if (!exists.value()) {
            return List.of();
        }
        IndexSearchContext ctx = resolveIndexContext(param, index);
        return dispatchByMode(param, index, ctx);
    }

    /**
     * 解析当前索引的检索上下文：全文检索字段列表、向量字段列表（ES 侧带 _vector_system 后缀）。
     */
    private IndexSearchContext resolveIndexContext(VectorSearchParam param, String index) {
        List<String> textFields = param.getTextFieldsPerIndex() != null ? param.getTextFieldsPerIndex().get(index) : null;
        List<String> vectorFieldKeys = param.getVectorFieldsPerIndex() != null ? param.getVectorFieldsPerIndex().get(index) : null;
        List<String> vectorFieldsEs = null;
        List<Double> vectorFieldWeights = null;
        if (vectorFieldKeys != null && !vectorFieldKeys.isEmpty()) {
            vectorFieldsEs = vectorFieldKeys.stream().map(key -> key + "_vector_system").toList();
            Map<String, Double> boosts = param.getFieldBoostsPerIndex() != null
                    ? param.getFieldBoostsPerIndex().get(index)
                    : null;
            List<Double> weights = new ArrayList<>();
            double sum = 0.0d;
            for (String key : vectorFieldKeys) {
                double b = 1.0d;
                if (boosts != null && boosts.get(key) != null && boosts.get(key) > 0) {
                    b = boosts.get(key);
                }
                weights.add(b);
                sum += b;
            }
            if (sum <= 0d) {
                sum = weights.size();
            }
            for (int i = 0; i < weights.size(); i++) {
                weights.set(i, weights.get(i) / sum);
            }
            vectorFieldWeights = weights;
        }
        TimeDecayParam td = param.getTimeDecayPerIndex() != null ? param.getTimeDecayPerIndex().get(index) : null;
        return new IndexSearchContext(textFields, vectorFieldsEs, vectorFieldWeights, td);
    }

    /**
     * 按检索模式分发到三种实现之一：KEYWORD / VECTOR / HYBRID（RRF 融合），抽象层次统一。
     */
    private List<ChunkSearchHit> dispatchByMode(VectorSearchParam param, String index, IndexSearchContext ctx) throws IOException {
        return switch (param.getMode()) {
            case KEYWORD -> searchByKeyword(param, index, ctx);
            case VECTOR -> searchByVector(param, index, ctx);
            case HYBRID -> searchByHybridRrf(param, index, ctx);
        };
    }

    /**
     * 关键字检索：单路 multi_match + script_score 将 BM25 归一化到 0~10，执行一次搜索并转成领域命中列表。
     */
    private List<ChunkSearchHit> searchByKeyword(VectorSearchParam param, String index, IndexSearchContext ctx) throws IOException {
        Query query = buildKeywordOnlyQuery(param, ctx.textFields, ctx.timeDecayParam);
        if (query == null) {
            return List.of();
        }
        SearchResponse<Map<String, Object>> response = executeSearch(index, query, param.getSize());
        return toChunkSearchHits(index, response);
    }

    /**
     * 向量检索：单路 match_all + script_score（cosine 相似度归一化到 0~10），执行一次搜索并转成领域命中列表。
     */
    private List<ChunkSearchHit> searchByVector(VectorSearchParam param, String index, IndexSearchContext ctx) throws IOException {
        Query query = buildVectorOnlyQuery(param, ctx.vectorFieldsEs, ctx.vectorFieldWeights, ctx.timeDecayParam);
        if (query == null) {
            return List.of();
        }
        SearchResponse<Map<String, Object>> response = executeSearch(index, query, param.getSize());
        return toChunkSearchHits(index, response);
    }

    /**
     * 混合检索（RRF）：分别执行关键字检索与向量检索，按 RRF 公式融合排名后再归一化到 0~10 并返回。
     */
    private List<ChunkSearchHit> searchByHybridRrf(VectorSearchParam param, String index, IndexSearchContext ctx) throws IOException {
        Query keywordQuery = buildKeywordQuery(param, ctx.textFields, ctx.timeDecayParam);
        Query vectorQuery = buildVectorOnlyQuery(param, ctx.vectorFieldsEs, ctx.vectorFieldWeights, ctx.timeDecayParam);
        if (keywordQuery == null && vectorQuery == null) {
            return List.of();
        }
        Map<String, Hit<Map<String, Object>>> hitById = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        int size = param.getSize();
        if (keywordQuery != null) {
            SearchResponse<Map<String, Object>> kwResp = executeSearch(index, keywordQuery, size);
            accumulateRrfFromRankedHits(hitById, rrfScores, kwResp.hits().hits());
        }
        if (vectorQuery != null) {
            SearchResponse<Map<String, Object>> vecResp = executeSearch(index, vectorQuery, size);
            accumulateRrfFromRankedHits(hitById, rrfScores, vecResp.hits().hits());
        }
        if (rrfScores.isEmpty()) {
            return List.of();
        }
        return buildChunkHitsFromRrf(index, hitById, rrfScores, size, param.isNormalizeScore());
    }

    /**
     * 构造仅关键字用的 multi_match 查询（供 HYBRID 的 RRF 分支使用；KEYWORD 模式用 {@link #buildKeywordOnlyQuery}）。
     * 策略：most_fields 综合多字段得分提升召回；Or 至少一词匹配；minimumShouldMatch 至少 1 条满足。
     */
    private Query buildKeywordQuery(VectorSearchParam param, List<String> textFields) {
        if ((param.getMode() == SearchMode.KEYWORD || param.getMode() == SearchMode.HYBRID)
                && param.getKeywordQuery() != null && !param.getKeywordQuery().isBlank()
                && textFields != null && !textFields.isEmpty()) {
            List<String> fields = textFields;
            return Query.of(q -> q.multiMatch(m -> m
                    .query(param.getKeywordQuery())
                    .fields(fields)
                    .type(TextQueryType.MostFields)
                    .operator(Operator.Or)
                    .minimumShouldMatch("1")));
        }
        return null;
    }

    private Query buildKeywordQuery(VectorSearchParam param, List<String> textFields, TimeDecayParam td) {
        Query keywordQuery = buildKeywordQuery(param, textFields);
        if (keywordQuery == null) {
            return null;
        }
        if (!isTimeDecayEnabled(td, param)) {
            return keywordQuery;
        }
        if (!isTimeDecayConfigComplete(td)) {
            log.warn("时间衰减配置不完整，已自动降级禁用。td={}", td);
            return keywordQuery;
        }
        // HYBRID 的 keyword 分支用于 RRF 融合：仅做时间加权，不做归一化
        String scriptSource = buildKeywordTimeDecayOnlyScriptSource();
        return Query.of(q -> q.scriptScore(ss -> ss
                .query(keywordQuery)
                .script(s -> applyTimeDecayParams(s.source(scriptSource).lang("painless"), td, true))));
    }

    /**
     * 构造关键字单路检索用 Query。归一化开启时：multi_match + script_score 将 BM25 压到 0~10；关闭时：仅 multi_match，返回 ES 原始 BM25 分。
     */
    private Query buildKeywordOnlyQuery(VectorSearchParam param, List<String> textFields, TimeDecayParam td) {
        Query keywordQuery = buildKeywordQuery(param, textFields);
        if (keywordQuery == null) {
            return null;
        }
        boolean enableTimeDecay = isTimeDecayEnabled(td, param) && isTimeDecayConfigComplete(td);
        if (!param.isNormalizeScore() && !enableTimeDecay) {
            return keywordQuery;
        }
        String scriptSource = buildKeywordScriptSource(param.isNormalizeScore(), enableTimeDecay);
        return Query.of(q -> q.scriptScore(ss -> {
            ss.query(keywordQuery);
            ss.script(s -> {
                s.source(scriptSource).lang("painless");
                if (enableTimeDecay) {
                    applyTimeDecayParams(s, td, true);
                }
                return s;
            });
            return ss;
        }));
    }

    /**
     * 构造向量单路检索用 Query。多语义字段时对每个字段算 cosine 后取最大值融合（多向量融合），再归一化到 0~10。
     */
    private Query buildVectorOnlyQuery(VectorSearchParam param, List<String> vectorFieldsEs, List<Double> vectorFieldWeights, TimeDecayParam td) {
        if (param.getQueryVector() == null || param.getQueryVector().length == 0
                || vectorFieldsEs == null || vectorFieldsEs.isEmpty()) {
            return null;
        }
        List<Float> queryVectorList = toFloatList(param.getQueryVector());
        List<Double> weights = vectorFieldWeights;
        if (weights == null || weights.size() != vectorFieldsEs.size()) {
            weights = new ArrayList<>();
            double w = 1.0d / vectorFieldsEs.size();
            for (int i = 0; i < vectorFieldsEs.size(); i++) {
                weights.add(w);
            }
        }
        boolean enableTimeDecay = isTimeDecayEnabled(td, param) && isTimeDecayConfigComplete(td);
        if (isTimeDecayEnabled(td, param) && !enableTimeDecay) {
            log.warn("时间衰减已请求启用，但配置不完整，已自动降级禁用。td={}", td);
        }
        String vectorBase = param.isNormalizeScore()
                ? buildScriptSourceForVectorOnly(vectorFieldsEs, weights)
                : buildScriptSourceForVectorOnlyRaw(vectorFieldsEs, weights);
        String scriptSource = enableTimeDecay
                ? vectorBase + buildTimeDecayScriptFragment() + "return baseScore * tdWeight;"
                : vectorBase + "return baseScore;";
        return Query.of(q -> q.scriptScore(ss -> ss
                .query(Query.of(q2 -> q2.matchAll(m -> m)))
                .script(s -> {
                    Script.Builder sb = s.source(scriptSource).lang("painless")
                            .params("queryVector", JsonData.of(queryVectorList))
                            .params("vectorWeight", JsonData.of(1.0d))
                            .params("tdEnabled", JsonData.of(enableTimeDecay));
                    if (enableTimeDecay) {
                        applyTimeDecayParams(sb, td, true);
                    }
                    return sb;
                })));
    }

    private boolean isTimeDecayEnabled(TimeDecayParam td, VectorSearchParam param) {
        return td != null && td.isEnabled() && !Boolean.FALSE.equals(param.getUseTimeDecay());
    }

    /**
     * 为避免 ES Java Client 在 JsonData.of(null) 时 NPE，这里要求启用时的关键字段非空。
     * 其它字段允许为空（脚本内有默认值/推导逻辑）。
     */
    private boolean isTimeDecayConfigComplete(TimeDecayParam td) {
        if (td == null) {
            return false;
        }
        if (td.getTimeField() == null || td.getTimeField().isBlank()) {
            return false;
        }
        if (td.getDecayType() == null || td.getDecayType().isBlank()) {
            return false;
        }
        if (td.getScaleMillis() == null || td.getScaleMillis() <= 0L) {
            return false;
        }
        return true;
    }

    private Script.Builder applyTimeDecayParams(Script.Builder s, TimeDecayParam td, boolean setNow) {
        // 前置：调用方保证 td 配置完整；这里仍尽量避免塞入 null
        if (td == null) {
            return s;
        }
        if (td.getTimeField() != null) {
            s.params("tdField", JsonData.of(td.getTimeField()));
        }
        if (td.getDecayType() != null) {
            s.params("tdType", JsonData.of(td.getDecayType()));
        }
        if (td.getScaleMillis() != null) {
            s.params("tdScale", JsonData.of(td.getScaleMillis()));
        }
        if (td.getOffsetMillis() != null) {
            s.params("tdOffset", JsonData.of(td.getOffsetMillis()));
        }
        if (td.getDecay() != null) {
            s.params("tdDecay", JsonData.of(td.getDecay()));
        }
        if (td.getFloor() != null) {
            s.params("tdFloor", JsonData.of(td.getFloor()));
        }
        if (td.getApplyThreshold() != null) {
            s.params("tdApplyThreshold", JsonData.of(td.getApplyThreshold()));
        }
        if (td.getLambda() != null) {
            s.params("tdLambda", JsonData.of(td.getLambda()));
        }
        if (td.getSigma() != null) {
            s.params("tdSigma", JsonData.of(td.getSigma()));
        }
        if (setNow) {
            s.params("tdNow", JsonData.of(System.currentTimeMillis()));
        }
        // 脚本内通过 params.tdEnabled 判断是否启用；这里统一传 true
        s.params("tdEnabled", JsonData.of(true));
        return s;
    }

    /**
     * 关键字检索脚本（仅时间衰减）：直接使用 _score 作为基础分，并按时间加权。
     * 用于 HYBRID 的 keyword 分支（RRF 只看 rank，保持 _score 单调即可）。
     */
    private String buildKeywordTimeDecayOnlyScriptSource() {
        String base = "double baseScore = _score;";
        return base + buildTimeDecayScriptFragment() + "return baseScore * tdWeight;";
    }

    /**
     * 关键字检索脚本（可选归一化 + 可选时间衰减）。
     */
    private String buildKeywordScriptSource(boolean normalize, boolean enableTimeDecay) {
        StringBuilder sb = new StringBuilder();
        sb.append("double baseScore = _score;");
        if (normalize) {
            sb.setLength(0);
            sb.append("double kwRaw = _score;")
              .append("double kwNorm = 10.0 * (1 - Math.exp(-kwRaw));")
              .append("if (kwNorm > 10.0) kwNorm = 10.0;")
              .append("double baseScore = kwNorm;");
        }
        if (!enableTimeDecay) {
            sb.append("return baseScore;");
            return sb.toString();
        }
        return sb + buildTimeDecayScriptFragment() + "return baseScore * tdWeight;";
    }

    private String buildTimeDecayScriptFragment() {
        // 约定：baseScore 已定义；输出 tdWeight（double）
        return ""
                + "double tdWeight = 1.0;"
                + "if (params.tdEnabled != null && params.tdEnabled == true) {"
                + "  if (params.tdApplyThreshold == null || baseScore >= params.tdApplyThreshold) {"
                + "    String f = params.tdField;"
                + "    if (f != null && doc.containsKey(f) && doc[f].size() > 0) {"
                + "      long now = params.tdNow != null ? (long)params.tdNow : System.currentTimeMillis();"
                + "      long t = 0L;"
                + "      try { t = doc[f].value.toInstant().toEpochMilli(); } catch (Exception e) { t = 0L; }"
                + "      if (t > 0L) {"
                + "        long age = now - t;"
                + "        long off = params.tdOffset != null ? (long)params.tdOffset : 0L;"
                + "        if (age < off) { age = 0L; } else { age = age - off; }"
                + "        if (age < 0L) age = 0L;"
                + "        long scale = params.tdScale != null ? (long)params.tdScale : 0L;"
                + "        double floor = params.tdFloor != null ? (double)params.tdFloor : 0.0;"
                + "        if (floor < 0.0) floor = 0.0;"
                + "        if (floor > 1.0) floor = 1.0;"
                + "        String type = params.tdType != null ? params.tdType : 'EXPONENTIAL';"
                + "        double w = 1.0;"
                + "        if (scale <= 0L) { w = 1.0; }"
                + "        else if (type == 'LINEAR') {"
                + "          w = 1.0 - ((double)age / (double)scale);"
                + "        } else if (type == 'GAUSSIAN') {"
                + "          double sigma = params.tdSigma != null ? (double)params.tdSigma : 0.0;"
                + "          if (sigma <= 0.0) {"
                + "            double decay = params.tdDecay != null ? (double)params.tdDecay : 0.5;"
                + "            if (decay <= 0.0) decay = 0.5;"
                + "            sigma = ((double)scale) / Math.sqrt(-2.0 * Math.log(decay));"
                + "          }"
                + "          double x = ((double)age) / sigma;"
                + "          w = Math.exp(-0.5 * x * x);"
                + "        } else {"
                + "          double lambda = params.tdLambda != null ? (double)params.tdLambda : 0.0;"
                + "          if (lambda <= 0.0) {"
                + "            double decay = params.tdDecay != null ? (double)params.tdDecay : 0.5;"
                + "            if (decay <= 0.0) decay = 0.5;"
                + "            lambda = -Math.log(decay) / (double)scale;"
                + "          }"
                + "          w = Math.exp(-lambda * (double)age);"
                + "        }"
                + "        if (w < floor) w = floor;"
                + "        if (w > 1.0) w = 1.0;"
                + "        tdWeight = w;"
                + "      }"
                + "    }"
                + "  }"
                + "}";
    }

    /**
     * 执行 ES 搜索：仅负责发起请求并返回响应，不做打分或转换。
     */
    @SuppressWarnings("unchecked")
    private SearchResponse<Map<String, Object>> executeSearch(String index, Query query, int size) throws IOException {
        return elasticsearchClient.search(
                SearchRequest.of(s -> s.index(index).size(size).query(query)),
                (Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    /**
     * 将一条排序列表按 RRF 公式累加到 hitById / rrfScores：rank 从 1 开始，贡献 1/(RRF_K + rank)。
     */
    private void accumulateRrfFromRankedHits(Map<String, Hit<Map<String, Object>>> hitById,
                                            Map<String, Double> rrfScores,
                                            List<Hit<Map<String, Object>>> hits) {
        if (hits == null) {
            return;
        }
        for (int i = 0; i < hits.size(); i++) {
            Hit<Map<String, Object>> hit = hits.get(i);
            String id = hit.id();
            if (id == null) {
                continue;
            }
            hitById.putIfAbsent(id, hit);
            double add = 1.0d / (RRF_K + (double) (i + 1));
            rrfScores.merge(id, add, (a, b) -> (a != null ? a : 0d) + (b != null ? b : 0d));
        }
    }

    /**
     * 根据 RRF 得分排序、取前 size 条并转换为 {@link ChunkSearchHit}。normalizeScore 为 true 时将得分归一化到 0~10，为 false 时保留原始 RRF 分。
     */
    private List<ChunkSearchHit> buildChunkHitsFromRrf(String index,
                                                       Map<String, Hit<Map<String, Object>>> hitById,
                                                       Map<String, Double> rrfScores,
                                                       int size,
                                                       boolean normalizeScore) {
        double maxScore = rrfScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d);
        if (maxScore <= 0d) {
            return List.of();
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(rrfScores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<ChunkSearchHit> result = new ArrayList<>();
        for (int i = 0; i < sorted.size() && result.size() < size; i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            Hit<Map<String, Object>> hit = hitById.get(entry.getKey());
            if (hit == null || hit.source() == null) {
                continue;
            }
            double score = normalizeScore ? 10.0d * entry.getValue() / maxScore : entry.getValue();
            ChunkSearchHit chunkHit = toChunkSearchHit(index, hit, score);
            if (chunkHit != null) {
                result.add(chunkHit);
            }
        }
        return result;
    }

    /**
     * 向量检索脚本（归一化）：每个向量字段分别计算 cosine 相似度，按权重加权求和后映射到 0~10。
     * 多向量索引时，部分文档可能只有部分向量字段，用 try-catch 包裹避免访问缺失字段导致 all shards failed。
     */
    private String buildScriptSourceForVectorOnly(List<String> vectorFieldsEs, List<Double> weights) {
        StringBuilder sb = new StringBuilder();
        sb.append("double sum = 0.0; ");
        for (int i = 0; i < vectorFieldsEs.size(); i++) {
            String field = vectorFieldsEs.get(i);
            double weightPerField = (i < weights.size() && weights.get(i) != null) ? weights.get(i) : 0.0d;
            String safe = field.replace("\\", "\\\\").replace("'", "\\'");
            sb.append("try { if (doc['").append(safe).append("'].size() > 0) { double vi = cosineSimilarity(params.queryVector, '").append(safe).append("'); sum += ")
                    .append(weightPerField).append(" * (vi + 1.0); } } catch (Exception e) {} ");
        }
        sb.append("double vNorm = sum * 5.0;");
        sb.append("if (vNorm < 0.0) vNorm = 0.0;");
        sb.append("if (vNorm > 10.0) vNorm = 10.0;");
        sb.append("double baseScore = params.vectorWeight * vNorm;");
        return sb.toString();
    }

    /**
     * 向量检索脚本（不归一化）：每个向量字段分别计算 cosine 相似度，按权重加权求和作为最终分值。
     * 多向量索引时，部分文档可能只有部分向量字段，用 try-catch 包裹避免访问缺失字段导致 all shards failed。
     */
    private String buildScriptSourceForVectorOnlyRaw(List<String> vectorFieldsEs, List<Double> weights) {
        StringBuilder sb = new StringBuilder();
        sb.append("double sum = 0.0; ");
        for (int i = 0; i < vectorFieldsEs.size(); i++) {
            String field = vectorFieldsEs.get(i);
            double weightPerField = (i < weights.size() && weights.get(i) != null) ? weights.get(i) : 0.0d;
            String safe = field.replace("\\", "\\\\").replace("'", "\\'");
            sb.append("try { if (doc['").append(safe).append("'].size() > 0) { double vi = cosineSimilarity(params.queryVector, '").append(safe).append("'); sum += ")
                    .append(weightPerField).append(" * (vi + 1.0); } } catch (Exception e) {} ");
        }
        sb.append("double baseScore = params.vectorWeight * sum;");
        return sb.toString();
    }

    /**
     * 仅关键字检索模式下的 painless 脚本：
     * <ul>
     *     <li>读取 BM25 原始 _score；</li>
     *     <li>通过 10 * (1 - exp(-_score)) 压缩到 0~10 区间；</li>
     *     <li>方便调用端用统一的 0~10 分数做过滤或排序展示。</li>
     * </ul>
     */
    private String buildScriptSourceForKeywordOnly() {
        return buildKeywordScriptSource(true, false);
    }

    private ChunkSearchHit toChunkSearchHit(String index,
                                            co.elastic.clients.elasticsearch.core.search.Hit<Map<String, Object>> hit,
                                            double score) {
        Map<String, Object> source = hit.source();
        if (source == null) {
            return null;
        }
        Object docIdObj = source.get(DOC_ID_FIELD);
        String docId = docIdObj == null ? null : docIdObj.toString();
        Map<String, Object> chunkContent = source.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().endsWith("_vector_system"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        return ChunkSearchHit.builder()
                .knowledgeCode(index)
                .docId(docId)
                .score(score)
                .chunkContent(chunkContent)
                .build();
    }

    /**
     * 将 ES 的 SearchResponse 转换为领域层的 {@link ChunkSearchHit} 列表：
     * <ul>
     *     <li>过滤掉内部用于相似度检索的 *_vector_system 字段；</li>
     *     <li>保留 doc_id、score（已归一化）以及其它业务字段；</li>
     *     <li>为每条命中记录打上对应的 knowledgeCode（索引名）。</li>
     * </ul>
     */
    private List<ChunkSearchHit> toChunkSearchHits(String index, SearchResponse<Map<String, Object>> response) {
        if (response.hits().hits() == null) {
            return List.of();
        }
        List<ChunkSearchHit> hits = new ArrayList<>();
        for (Hit<Map<String, Object>> hit : response.hits().hits()) {
            Double scoreObj = hit.score();
            double score = scoreObj != null ? scoreObj : 0d;
            ChunkSearchHit converted = toChunkSearchHit(index, hit, score);
            if (converted != null) {
                hits.add(converted);
            }
        }
        return hits;
    }

    /**
     * 将通用文档 Map 转为 ES 文档：补全 doc_id，并将 *_vector 转为 *_vector_system（List&lt;Float&gt;）。
     */
    private static Map<String, Object> toEsDocument(String docId, Map<String, Object> doc) {
        Map<String, Object> esDoc = new LinkedHashMap<>();
        esDoc.put(DOC_ID_FIELD, docId);
        if (doc == null) {
            return esDoc;
        }
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (DOC_ID_FIELD.equals(key)) {
                continue;
            }
            if (DOC_UPDATE_TIME_FIELD.equals(key)) {
                esDoc.put(DOC_UPDATE_TIME_FIELD, value);
                continue;
            }
            if (key != null && key.endsWith("_vector") && value instanceof float[]) {
                esDoc.put(key + "_system", toFloatList((float[]) value));
            } else {
                esDoc.put(key, value);
            }
        }
        return esDoc;
    }

    private static List<Float> toFloatList(float[] arr) {
        if (arr == null) {
            return List.of();
        }
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    private void checkVectorDBExist(String dbName) {
        try {
            BooleanResponse exists = elasticsearchClient.indices().exists(builder -> builder.index(dbName));
            if (exists.value()) {
                throw new BusinessException("VectorDB already exists");
            }
        } catch (IOException e) {
            log.error("checkVectorDBExist error: {}", e.getMessage());
        }
    }
}
