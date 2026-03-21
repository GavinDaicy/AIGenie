package com.genie.query.domain.rerank;

import com.genie.query.domain.vectorstore.ChunkSearchHit;

import java.util.List;

/**
 * 检索结果重排序：根据 query 与候选文档的相关性对初检结果重新打分并排序。
 * 实现类可调用外部 rerank 模型（如 DashScope）或本地模型。
 *
 * @author daicy
 * @date 2026/3/8
 */
public interface RerankService {

    /**
     * 对初检命中结果进行重排序。
     *
     * @param query 用户查询文本
     * @param hits  初检命中的分块列表（顺序与分数可被忽略，由实现重排）
     * @param topN 最终保留条数
     * @return 按相关性重排后的列表（长度 ≤ topN），每条 score 为 rerank 模型给出的 relevance_score（0~1）；
     *         若未启用或调用失败，实现应返回原列表或原顺序以保证降级
     */
    List<ChunkSearchHit> rerank(String query, List<ChunkSearchHit> hits, int topN);
}
