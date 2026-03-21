package com.genie.query.domain.query.service;

import java.util.List;

/**
 * Query 改写与多查询生成领域服务。
 * 负责基于原始问题生成主要改写查询与若干扩展查询。
 */
public interface QueryRewriteService {

    /**
     * 生成主要改写查询与扩展查询列表。
     *
     * @param originalQuestion 原始用户问题（已校验非空）
     * @param totalQueries     期望生成的查询总数，至少 1
     * @param context          可选上下文信息（如知识库编码等），实现可按需使用
     * @return 生成结果，至少包含一个 mainQuery；扩展查询列表可为空
     */
    QueryRewriteResult generateQueries(String originalQuestion, int totalQueries, QueryRewriteContext context);

    /**
     * Query 改写上下文，可按需扩展字段。
     */
    class QueryRewriteContext {
        private List<String> knowledgeCodes;

        public QueryRewriteContext() {
        }

        public QueryRewriteContext(List<String> knowledgeCodes) {
            this.knowledgeCodes = knowledgeCodes;
        }

        public List<String> getKnowledgeCodes() {
            return knowledgeCodes;
        }

        public void setKnowledgeCodes(List<String> knowledgeCodes) {
            this.knowledgeCodes = knowledgeCodes;
        }
    }

    /**
     * Query 改写结果：一个主要查询 + 若干扩展查询。
     */
    class QueryRewriteResult {
        private final String mainQuery;
        private final List<String> expandedQueries;

        public QueryRewriteResult(String mainQuery, List<String> expandedQueries) {
            this.mainQuery = mainQuery;
            this.expandedQueries = expandedQueries;
        }

        public String getMainQuery() {
            return mainQuery;
        }

        public List<String> getExpandedQueries() {
            return expandedQueries;
        }
    }
}

