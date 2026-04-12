package com.genie.query.domain.agent.fewshot;

import java.util.List;

/**
 * FewShot Q→SQL 对的存储抽象：提供索引初始化、写入和向量检索能力。
 *
 * <p>由 infrastructure 层提供具体实现（如 Elasticsearch kNN），通过 Spring DI 注入。
 * 遵循「依赖倒置原则」：domain 层只依赖此接口，不感知底层存储技术。
 *
 * @author daicy
 * @date 2026/4/12
 * @see com.genie.query.domain.vectorstore.VectorStore
 */
public interface FewShotStore {

    /**
     * 幂等创建存储结构（如 ES 索引）。若已存在则跳过，应用启动时调用一次。
     */
    void createIndexIfAbsent();

    /**
     * 写入一条 Q→SQL 对。
     *
     * @param pair 待写入的 FewShot 对（必须包含 id 和 questionVector）
     */
    void indexPair(FewShotDocument pair);

    /**
     * kNN 向量检索：按问题向量相似度返回 Top-K 条历史成功案例。
     *
     * @param vector       查询向量
     * @param datasourceId 数据源 ID 过滤，为 null 时不过滤
     * @param topK         返回数量上限
     * @return 相似度由高到低的文档列表
     */
    List<FewShotDocument> knnSearch(float[] vector, String datasourceId, int topK);
}
