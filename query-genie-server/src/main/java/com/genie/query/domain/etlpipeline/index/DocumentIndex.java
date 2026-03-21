package com.genie.query.domain.etlpipeline.index;

import com.genie.query.domain.etlpipeline.embedding.EmbeddingService;
import com.genie.query.domain.etlpipeline.model.Chunk;
import com.genie.query.domain.etlpipeline.model.IndexChunk;
import com.genie.query.domain.knowledge.model.KLField;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档索引领域服务（etlpipeline 的 index 环节）：对切块做向量化，并转换为通用的 Map 文档结构。
 * 到此 etlpipeline 结束，不涉及向量库写入；写入由调用方（如 ParsedListener）通过 VectorStore 完成。
 *
 * @author daicy
 * @date 2026/1/16
 */
@Service
public class DocumentIndex {

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 对切块做向量化并转换为通用文档表示（Map 列表），约定：标量用 fieldKey，向量用 fieldKey + "_vector" -> float[]。
     *
     * @param knowledgeCode 知识库编码（可选用于上下文）
     * @param docId         源文档 id（可选用于上下文）
     * @param chunks        切块列表（未向量化）
     * @param userFields    知识库用户字段配置（用于识别需语义检索的字段）
     * @return 通用文档列表，可直接交给 VectorStore 写入
     */
    public List<Map<String, Object>> toIndexedDocuments(String knowledgeCode, String docId, List<Chunk> chunks, List<KLField> userFields) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<IndexChunk> indexChunks = buildIndexChunks(chunks, userFields);
        return toDocuments(indexChunks);
    }

    /**
     * 对每个 Chunk 中需要语义检索的字段做向量化，组装为 IndexChunk。
     */
    private List<IndexChunk> buildIndexChunks(List<Chunk> chunks, List<KLField> userFields) {
        List<KLField> semanticFields = userFields.stream()
                .filter(f -> Boolean.TRUE.equals(f.getSemanticSearchable()))
                .collect(Collectors.toList());
        if (semanticFields.isEmpty()) {
            return chunks.stream()
                    .map(c -> {
                        IndexChunk ic = new IndexChunk();
                        ic.setChunk(c);
                        ic.setSemanticVectors(Map.of());
                        return ic;
                    })
                    .collect(Collectors.toList());
        }

        List<IndexChunk> result = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Map<String, float[]> vectors = new LinkedHashMap<>();
            for (KLField field : semanticFields) {
                String fieldKey = field.getFieldKey();
                Object val = chunk.getData() != null ? chunk.getData().get(fieldKey) : null;
                String text = val == null ? "" : StringUtils.trimToEmpty(val.toString());
                if (StringUtils.isBlank(text)) {
                    continue;
                }
                float[] vec = embeddingService.embed(text);
                vectors.put(fieldKey, vec);
            }
            IndexChunk ic = new IndexChunk();
            ic.setChunk(chunk);
            ic.setSemanticVectors(vectors);
            result.add(ic);
        }
        return result;
    }

    /**
     * 将 IndexChunk 转为向量库通用文档表示（Map），约定：标量用 fieldKey，向量用 fieldKey + "_vector" -> float[]。
     */
    private List<Map<String, Object>> toDocuments(List<IndexChunk> indexChunks) {
        List<Map<String, Object>> documents = new ArrayList<>(indexChunks.size());
        for (IndexChunk ic : indexChunks) {
            Map<String, Object> doc = new LinkedHashMap<>();
            if (ic.getChunk() != null && ic.getChunk().getData() != null) {
                doc.putAll(ic.getChunk().getData());
            }
            if (ic.getSemanticVectors() != null) {
                for (Map.Entry<String, float[]> e : ic.getSemanticVectors().entrySet()) {
                    doc.put(e.getKey() + "_vector", e.getValue());
                }
            }
            documents.add(doc);
        }
        return documents;
    }
}
