package com.genie.query.domain.qa.service;

import com.genie.query.domain.query.model.QueryResultEntry;
import com.genie.query.domain.vectorstore.SearchMode;

import java.io.IOException;
import java.util.List;

/**
 * QA 领域召回服务：基于若干查询句执行多知识库检索，合并并去重。
 * 不包含改写能力，改写由应用层通过 {@link com.genie.query.domain.query.service.QueryRewriteService} 编排。
 */
public interface QaQueryService {

    /**
     * 使用给定的多个查询句依次检索，合并结果并去重。
     *
     * @param queries         用于检索的查询列表（至少一条，已由应用层完成改写或直接使用原问句）
     * @param knowledgeCodes  知识库编码列表，可为空或 null 表示全部
     * @param mode            检索模式
     * @param size            期望返回的最终条数
     * @param searchSize      实际检索条数（可能大于 size，用于 rerank）
     * @param enableRerank    是否对检索结果做 rerank
     * @return 去重后的检索结果列表
     */
    List<QueryResultEntry> searchWithQueries(
            List<String> queries,
            List<String> knowledgeCodes,
            SearchMode mode,
            int size,
            int searchSize,
            boolean enableRerank,
            Boolean useTimeDecay
    ) throws IOException;
}

