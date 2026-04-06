package com.genie.query.domain.agent.tool.sql.pipeline;

import com.genie.query.domain.agent.tool.sql.SqlExecutor;
import com.genie.query.domain.agent.tool.sql.model.SqlQueryResult;
import com.genie.query.domain.agent.tool.sql.model.SqlGenerationResult;
import com.genie.query.domain.agent.tool.sql.model.ValidationResult;
import com.genie.query.domain.agent.tool.sql.model.ExplainResult;
import com.genie.query.domain.agent.tool.sql.model.QueryResult;

import com.genie.query.domain.exception.QueryTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 自修正循环（Step 2-4）：最多循环 3 次，将执行错误反馈给 LLM 重新生成 SQL，直到成功或超限。
 *
 * <p>循环内流程：
 * <ol>
 *   <li>CoT SQL 生成（SqlGenerationService）</li>
 *   <li>安全校验（SqlSecurityValidator）—— 失败不重试，直接拒绝</li>
 *   <li>EXPLAIN 语法验证（SqlExecutor）</li>
 *   <li>执行 SQL（SqlExecutor）</li>
 *   <li>结果为空时提示放宽条件，最多触发一次额外重试</li>
 * </ol>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class SelfCorrectionLoop {

    private static final Logger log = LoggerFactory.getLogger(SelfCorrectionLoop.class);
    private static final int DEFAULT_MAX_RETRIES = 3;

    @Autowired
    private SqlGenerationService sqlGenerationService;

    @Autowired
    private SqlSecurityValidator sqlSecurityValidator;

    @Autowired
    private SqlExecutor sqlExecutor;

    @Autowired
    private DynamicFewShotService dynamicFewShotService;

    /**
     * 执行自修正循环。
     *
     * @param question      用户原始问题
     * @param schemaContext 精简 Schema 文本（含字段别名 + 样本值）
     * @param fewShot       动态 Few-shot 示例文本
     * @param allowedTables 表名白名单（用于安全校验）
     * @param datasourceId  目标数据源 ID
     * @return SQL 查询最终结果
     */
    public SqlQueryResult execute(String question, String schemaContext, String fewShot,
                                  Set<String> allowedTables, Long datasourceId) {
        return execute(question, schemaContext, fewShot, allowedTables, datasourceId, DEFAULT_MAX_RETRIES);
    }

    /**
     * 执行自修正循环（可指定最大重试次数）。
     */
    public SqlQueryResult execute(String question, String schemaContext, String fewShot,
                                  Set<String> allowedTables, Long datasourceId, int maxRetries) {
        String errorFeedback = "";

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("[SelfCorrection] 第 {} 次尝试，question={}", attempt, question);

            // Step 2: CoT SQL 生成
            SqlGenerationResult generated;
            try {
                generated = sqlGenerationService.generate(question, schemaContext, fewShot, errorFeedback);
            } catch (Exception e) {
                log.error("[SelfCorrection] SQL 生成异常: {}", e.getMessage());
                return SqlQueryResult.maxRetriesExceeded("SQL 生成服务异常: " + e.getMessage());
            }

            if (generated.isCannotQuery()) {
                log.info("[SelfCorrection] LLM 返回 CANNOT_QUERY");
                return SqlQueryResult.cannotQuery();
            }

            // Step 3: 安全校验（失败不进入自修正，直接拒绝）
            ValidationResult validation = sqlSecurityValidator.validate(generated.getSql(), allowedTables);
            if (!validation.isPass()) {
                log.warn("[SelfCorrection] 安全校验失败: {}", validation.getReason());
                return SqlQueryResult.securityFail(validation.getReason());
            }

            String safeSql = sqlSecurityValidator.enforceLimitClause(generated.getSql());

            // Step 4a: EXPLAIN 语法验证
            ExplainResult explain = sqlExecutor.explain(safeSql, datasourceId);
            if (!explain.isValid()) {
                errorFeedback = buildErrorFeedback(attempt, safeSql, "SQL 语法错误: " + explain.getError());
                log.warn("[SelfCorrection] 第 {} 次 EXPLAIN 失败: {}", attempt, explain.getError());
                continue;
            }

            // Step 4b: 执行 SQL
            try {
                QueryResult result = sqlExecutor.execute(safeSql, datasourceId);

                if (result.isEmpty()) {
                    if (attempt < maxRetries) {
                        errorFeedback = buildErrorFeedback(attempt, safeSql,
                                "执行结果为空，请检查过滤条件是否过严，尝试适当放宽条件重新生成");
                        log.info("[SelfCorrection] 第 {} 次结果为空，触发放宽条件重试", attempt);
                        continue;
                    }
                    return SqlQueryResult.emptyResult(safeSql,
                            "该查询条件下暂无数据，建议扩大时间范围或调整筛选条件");
                }

                // 执行成功：存入动态 Few-shot 库
                dynamicFewShotService.saveSuccessfulPair(question, safeSql);
                log.info("[SelfCorrection] 第 {} 次成功，行数={}", attempt, result.rowCount());
                return SqlQueryResult.success(safeSql, result, generated.getThought());

            } catch (QueryTimeoutException e) {
                log.warn("[SelfCorrection] 查询超时: {}", safeSql);
                return SqlQueryResult.timeout(safeSql);
            } catch (Exception e) {
                errorFeedback = buildErrorFeedback(attempt, safeSql, "执行异常: " + e.getMessage());
                log.warn("[SelfCorrection] 第 {} 次执行异常: {}", attempt, e.getMessage());
            }
        }

        return SqlQueryResult.maxRetriesExceeded(
                "经过 " + maxRetries + " 次尝试仍无法生成正确 SQL，请尝试换一种描述方式提问");
    }

    private String buildErrorFeedback(int attempt, String sql, String error) {
        return String.format(
                "## 上一次尝试（第%d次）的错误\n生成的SQL：%s\n错误信息：%s\n请根据以上错误修正SQL。",
                attempt, sql, error);
    }
}
