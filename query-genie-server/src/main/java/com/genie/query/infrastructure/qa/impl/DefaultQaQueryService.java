package com.genie.query.infrastructure.qa.impl;

import com.genie.query.domain.qa.service.QaSearchCapability;
import com.genie.query.domain.query.service.QueryService;
import com.genie.query.domain.rerank.RerankService;
import com.genie.query.domain.vectorstore.ChunkSearchHit;
import com.genie.query.domain.vectorstore.SearchMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * QA 检索基础设施适配器：仅保留检索与重排能力调用，不承载策略编排。
 */
@Service
public class DefaultQaQueryService implements QaSearchCapability {
    private final QueryService queryService;

    @Autowired(required = false)
    private RerankService rerankService;

    public DefaultQaQueryService(QueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public List<ChunkSearchHit> searchHits(String query, SearchMode mode, int searchSize, List<String> knowledgeCodes,
                                           Boolean useTimeDecay) throws IOException {
        return queryService.searchHitsOnly(query, mode, searchSize, knowledgeCodes, true, useTimeDecay);
    }

    @Override
    public List<ChunkSearchHit> rerank(String query, List<ChunkSearchHit> candidates, int topN) {
        if (rerankService == null || candidates == null || candidates.isEmpty() || topN <= 0) {
            return candidates == null ? List.of() : candidates;
        }
        return rerankService.rerank(query, candidates, topN);
    }

    @Override
    public boolean supportsRerank() {
        return rerankService != null;
    }
}
