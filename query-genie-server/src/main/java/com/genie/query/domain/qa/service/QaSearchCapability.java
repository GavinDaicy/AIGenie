package com.genie.query.domain.qa.service;

import com.genie.query.domain.vectorstore.ChunkSearchHit;
import com.genie.query.domain.vectorstore.SearchMode;

import java.io.IOException;
import java.util.List;

/**
 * QA 检索能力端口：由基础设施层实现，领域层只依赖能力调用。
 */
public interface QaSearchCapability {
    List<ChunkSearchHit> searchHits(String query,
                                    SearchMode mode,
                                    int searchSize,
                                    List<String> knowledgeCodes,
                                    Boolean useTimeDecay) throws IOException;

    List<ChunkSearchHit> rerank(String query, List<ChunkSearchHit> candidates, int topN);

    boolean supportsRerank();
}
