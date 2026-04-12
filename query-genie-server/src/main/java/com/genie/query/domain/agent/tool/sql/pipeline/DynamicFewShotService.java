package com.genie.query.domain.agent.tool.sql.pipeline;

import com.genie.query.domain.agent.fewshot.FewShotDocument;
import com.genie.query.domain.agent.fewshot.FewShotStore;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.List;

/**
 * 动态 Few-shot 服务：从 ES 向量库检索历史成功 Q→SQL 对，点赞后写入。
 *
 * <p>通过 {@code app.fewshot.es.enabled} 控制是否启用 ES 能力（默认 false）。
 * <p>enabled=false 时所有方法快速返回（空字符串 / 静默），不访问 ES。
 * <p>enabled=true 时：retrieve() kNN 检索，saveSuccessfulPair() 向量化后写入。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class DynamicFewShotService {

    private static final Logger log = LoggerFactory.getLogger(DynamicFewShotService.class);

    @Value("${app.fewshot.es.enabled:false}")
    private boolean enabled;

    @Value("${app.fewshot.es.top-k:3}")
    private int defaultTopK;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Autowired(required = false)
    private FewShotStore fewShotStore;

    @PostConstruct
    public void init() {
        if (enabled && fewShotStore != null) {
            try {
                fewShotStore.createIndexIfAbsent();
                log.info("[FewShot] ES 索引初始化完成");
            } catch (Exception e) {
                log.warn("[FewShot] ES 索引初始化失败，FewShot 功能将降级为空实现: {}", e.getMessage());
            }
        }
    }

    /**
     * 检索与问题最相似的历史成功 Q→SQL 案例，构建 Few-shot 文本。
     *
     * @param question 用户问题
     * @param topK     最多返回案例数
     * @return Few-shot 文本（格式：【参考案例N】\n问题：...\nSQL：...\n\n），无案例时返回空字符串
     */
    public String retrieve(String question, int topK) {
        if (!enabled || embeddingModel == null || fewShotStore == null) {
            log.debug("[FewShot] FewShot 未启用或依赖缺失，返回空字符串");
            return "";
        }
        try {
            float[] vector = embeddingModel.embed(question);
            List<FewShotDocument> docs = fewShotStore.knnSearch(vector, null, topK);
            if (docs.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < docs.size(); i++) {
                FewShotDocument doc = docs.get(i);
                sb.append("【参考案例").append(i + 1).append("】\n")
                  .append("问题：").append(doc.getQuestion()).append("\n")
                  .append("SQL：").append(doc.getSql()).append("\n\n");
            }
            log.debug("[FewShot] 检索到 {} 条相似案例 | question={}", docs.size(), question);
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("[FewShot] retrieve 失败，降级返回空字符串 | error={}", e.getMessage());
            return "";
        }
    }

    /**
     * 将执行成功的 Q→SQL 对存入向量库（向后兼容重载，datasourceId 和 feedbackId 为 null）。
     *
     * @param question 原始自然语言问题
     * @param sql      成功执行的 SQL 语句
     */
    public void saveSuccessfulPair(String question, String sql) {
        saveSuccessfulPair(question, sql, null, null);
    }

    /**
     * 将执行成功的 Q→SQL 对存入向量库（主方法，点赞触发调用）。
     *
     * @param question     原始自然语言问题（已做指代消解）
     * @param sql          成功执行的 SQL 语句
     * @param datasourceId 数据源 ID（可为 null）
     * @param feedbackId   关联的点赞反馈 ID（可为 null）
     */
    public void saveSuccessfulPair(String question, String sql, String datasourceId, String feedbackId) {
        if (!enabled || embeddingModel == null || fewShotStore == null) {
            log.debug("[FewShot] FewShot 未启用或依赖缺失，跳过写入");
            return;
        }
        try {
            float[] vector = embeddingModel.embed(question);
            FewShotDocument doc = new FewShotDocument();
            doc.setId(SnowflakeIdUtils.getNextStringId());
            doc.setQuestion(question);
            doc.setSql(sql);
            doc.setDatasourceId(datasourceId);
            doc.setQuestionVector(vector);
            doc.setCreatedAt(new Date());
            doc.setFeedbackId(feedbackId);
            fewShotStore.indexPair(doc);
            log.info("[FewShot] 成功写入 Q→SQL 对 | id={} | question={}", doc.getId(), question);
        } catch (Exception e) {
            log.warn("[FewShot] saveSuccessfulPair 失败，跳过（不影响主流程） | error={}", e.getMessage());
        }
    }

    /**
     * 从 db_table_schema.sample_queries 批量导入种子数据。
     */
    public void importSeedData(List<SeedPair> seeds) {
        if (!enabled || embeddingModel == null || fewShotStore == null) {
            log.info("[FewShot] FewShot 未启用，跳过种子数据导入");
            return;
        }
        log.info("[FewShot] 开始批量导入种子数据，共 {} 条", seeds.size());
        int success = 0;
        for (SeedPair seed : seeds) {
            try {
                saveSuccessfulPair(seed.question(), seed.sql(), null, null);
                success++;
            } catch (Exception e) {
                log.warn("[FewShot] 种子数据导入失败 | question={} | error={}", seed.question(), e.getMessage());
            }
        }
        log.info("[FewShot] 种子数据导入完成 | 成功={} / 总计={}", success, seeds.size());
    }

    /**
     * 种子数据对（问题 + SQL）。
     */
    public record SeedPair(String question, String sql) {}
}
