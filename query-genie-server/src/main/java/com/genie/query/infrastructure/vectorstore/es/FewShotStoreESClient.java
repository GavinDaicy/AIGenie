package com.genie.query.infrastructure.vectorstore.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.genie.query.domain.agent.fewshot.FewShotDocument;
import com.genie.query.domain.agent.fewshot.FewShotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link FewShotStore} 的 Elasticsearch 实现。
 *
 * <p>封装 {@code fewshot_qsql_pairs} 索引的创建、写入与 kNN 检索，仅做纯粹的存储操作，
 * 不依赖领域业务逻辑。与 {@link VectorStoreESClient} 同层，职责类似。
 *
 * <p>索引结构：question_vector (dense_vector cosine) + datasource_id (keyword filter)
 *
 * @author daicy
 * @date 2026/4/12
 * @see FewShotStore
 * @see VectorStoreESClient
 */
@Component
public class FewShotStoreESClient implements FewShotStore {

    private static final Logger log = LoggerFactory.getLogger(FewShotStoreESClient.class);

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Value("${app.fewshot.es.index-name:fewshot_qsql_pairs}")
    private String indexName;

    @Value("${spring.ai.dashscope.embedding.options.dimensions:768}")
    private int embeddingDimensions;

    @Override
    public void createIndexIfAbsent() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(indexName)))
                    .value();
            if (exists) {
                log.info("[FewShotEs] 索引 {} 已存在，跳过创建", indexName);
                return;
            }
            final int dims = embeddingDimensions;
            elasticsearchClient.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("id", p -> p.keyword(k -> k))
                            .properties("question", p -> p.text(t -> t))
                            .properties("sql", p -> p.text(t -> t.index(false)))
                            .properties("datasource_id", p -> p.keyword(k -> k))
                            .properties("question_vector", p -> p.denseVector(dv -> dv
                                    .dims(dims)
                                    .index(true)
                                    .similarity(DenseVectorSimilarity.Cosine)))
                            .properties("created_at", p -> p.date(d -> d))
                            .properties("feedback_id", p -> p.keyword(k -> k))
                    )
            );
            log.info("[FewShotEs] 索引 {} 创建成功，向量维度={}", indexName, dims);
        } catch (Exception e) {
            log.warn("[FewShotEs] 索引创建失败（ES可能不可用）: {}", e.getMessage());
            throw new RuntimeException("FewShot ES 索引创建失败", e);
        }
    }

    @Override
    public void indexPair(FewShotDocument doc) {
        try {
            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(indexName)
                    .id(doc.getId())
                    .document(doc));
            log.debug("[FewShotEs] 写入成功 | id={} | result={}", doc.getId(), response.result());
        } catch (Exception e) {
            log.warn("[FewShotEs] 写入失败 | id={} | error={}", doc.getId(), e.getMessage());
            throw new RuntimeException("FewShot ES 写入失败", e);
        }
    }

    @Override
    public List<FewShotDocument> knnSearch(float[] vector, String datasourceId, int topK) {
        try {
            List<Float> vectorList = new ArrayList<>(vector.length);
            for (float f : vector) {
                vectorList.add(f);
            }

            KnnSearch.Builder knnBuilder = new KnnSearch.Builder()
                    .field("question_vector")
                    .queryVector(vectorList)
                    .k(topK)
                    .numCandidates(topK * 5);
            if (datasourceId != null) {
                final String dsId = datasourceId;
                knnBuilder.filter(f -> f.term(t -> t.field("datasource_id").value(FieldValue.of(dsId))));
            }
            KnnSearch knn = knnBuilder.build();

            SearchResponse<FewShotDocument> response = elasticsearchClient.search(s -> s
                    .index(indexName)
                    .knn(knn), FewShotDocument.class);

            List<FewShotDocument> results = new ArrayList<>();
            for (Hit<FewShotDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    results.add(hit.source());
                }
            }
            log.debug("[FewShotEs] kNN 检索完成 | topK={} | 实际返回={}", topK, results.size());
            return results;
        } catch (Exception e) {
            log.warn("[FewShotEs] kNN 检索失败 | error={}", e.getMessage());
            throw new RuntimeException("FewShot ES 检索失败", e);
        }
    }
}
