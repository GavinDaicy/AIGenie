package com.genie.query.domain.agent.tool.sql.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 动态 Few-shot 服务：从 ES 向量库检索历史成功 Q→SQL 对，并在执行成功后存入。
 *
 * <p>迭代2：静态返回空（无 ES 种子数据），接口已就位供迭代5接入动态检索。
 * <p>迭代5：实现 ES 向量检索 + saveSuccessfulPair 写入。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class DynamicFewShotService {

    private static final Logger log = LoggerFactory.getLogger(DynamicFewShotService.class);

    /**
     * 检索与问题最相似的历史成功 Q→SQL 案例，构建 Few-shot 文本。
     *
     * @param question 用户问题
     * @param topK     最多返回案例数
     * @return Few-shot 文本（格式：Q:...\nSQL:...\n\n），无案例时返回空字符串
     */
    public String retrieve(String question, int topK) {
        log.debug("[FewShot] 检索相似历史案例（迭代2暂返回空，等待迭代5 ES 接入）");
        return "";
    }

    /**
     * 将执行成功的 Q→SQL 对存入向量库，供后续检索使用。
     *
     * @param question 原始自然语言问题
     * @param sql      成功执行的 SQL 语句
     */
    public void saveSuccessfulPair(String question, String sql) {
        log.info("[FewShot] 记录成功案例（迭代2暂不存储，等待迭代5 ES 接入）: question={}", question);
    }

    /**
     * 从 db_table_schema.sample_queries 批量导入种子数据（迭代5实现）。
     */
    public void importSeedData(List<SeedPair> seeds) {
        log.info("[FewShot] 批量导入种子数据共 {} 条（迭代5实现）", seeds.size());
    }

    /**
     * 种子数据对（问题 + SQL）。
     */
    public record SeedPair(String question, String sql) {}
}
