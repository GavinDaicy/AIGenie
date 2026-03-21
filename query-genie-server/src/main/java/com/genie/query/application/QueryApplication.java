package com.genie.query.application;

import com.genie.query.controller.dto.QueryRequest;
import com.genie.query.controller.dto.QueryResultItem;
import com.genie.query.domain.query.model.QueryResultEntry;
import com.genie.query.domain.query.service.QueryService;
import com.genie.query.domain.vectorstore.SearchMode;
import com.genie.query.infrastructure.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 查询应用层：仅做入参校验、调用领域查询服务、将领域结果转为 DTO 返回。
 * 完整查询能力在 domain 层 {@link QueryService}，应用层不再直接调用 VectorStore。
 *
 * @author daicy
 * @date 2026/2/8
 */
@Service
public class QueryApplication {

    @Autowired
    private QueryService queryService;

    @Value("${app.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Value("${app.rerank.candidate-factor:1}")
    private int rerankCandidateFactor;

    /**
     * 多知识库检索：支持指定知识库或查全部，支持关键字/向量/混合检索。
     * 多个知识库时，每个知识库各保留 size 条结果。返回分块内容及文档信息。
     */
    public List<QueryResultItem> search(QueryRequest request) throws IOException {
        if (request == null || StringUtils.isBlank(request.getQuery())) {
            throw new BusinessException("查询关键词不能为空");
        }
        SearchMode mode = request.getMode() != null ? request.getMode() : SearchMode.HYBRID;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 10;
        boolean normalizeScore = request.getNormalizeScore() != Boolean.FALSE;
        boolean enableRerank = Boolean.TRUE.equals(request.getRerank()) && rerankEnabled;
        int searchSize = enableRerank ? size * Math.max(1, rerankCandidateFactor) : size;

        List<QueryResultEntry> entries = queryService.search(
                request.getQuery(),
                mode,
                searchSize,
                request.getKnowledgeCodes(),
                normalizeScore,
                enableRerank,
                size,
                request.getUseTimeDecay());

        return entries.stream()
                .map(e -> QueryResultItem.builder()
                        .knowledgeCode(e.getKnowledgeCode())
                        .score(e.getScore())
                        .chunkContent(e.getChunkContent())
                        .document(e.getDocument())
                        .build())
                .collect(Collectors.toList());
    }
}
