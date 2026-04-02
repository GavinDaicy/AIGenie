package com.genie.query.domain.agent.sql;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * CoT SQL 生成服务（Step 2）：基于精简 Schema 上下文 + 动态 Few-shot + 历史错误反馈，
 * 要求 LLM 先描述查询思路（Chain-of-Thought）再生成 SQL。
 *
 * <p>输出格式（必须严格遵守）：
 * <pre>
 * 查询思路：从 steel_price 表查询，过滤 steel_diameter=20 且近 6 个月的记录，...
 * SELECT supplier_name, MIN(unit_price) ...
 * </pre>
 *
 * <p>特殊值：当 LLM 无法用已知表回答问题时，返回 {@code CANNOT_QUERY}。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class SqlGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerationService.class);

    /** LLM 无法回答时的特殊标记 */
    static final String CANNOT_QUERY_MARKER = "CANNOT_QUERY";

    /** 查询思路前缀（LLM 输出的第一行以此开头） */
    private static final String THOUGHT_PREFIX = "查询思路：";

    private static final String PROMPT_TEMPLATE =
            "你是一个专业的MySQL查询助手。\n\n" +
            "## 相关数据库表（已筛选）\n" +
            "%s\n\n" +
            "## 参考示例（相似历史问题）\n" +
            "%s\n\n" +
            "## 用户问题\n" +
            "%s\n\n" +
            "%s" +
            "## 生成规则\n" +
            "1. 只生成 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP 等\n" +
            "2. 只使用上面列出的表和字段，不得引用不存在的表或字段\n" +
            "3. 字段取值请参考 sample_values，确保类型和格式正确\n" +
            "4. 时间相关问题使用 DATE_SUB、DATE_FORMAT 等 MySQL 日期函数\n" +
            "5. 聚合分析使用 GROUP BY、ORDER BY、HAVING\n" +
            "6. 默认添加 LIMIT（不超过500）\n" +
            "7. 无法用数据库回答时，回复：CANNOT_QUERY\n\n" +
            "## 输出格式（必须严格遵守）\n" +
            "第一部分：查询思路（1-3句话描述：用哪张表、过滤条件、如何聚合）\n" +
            "第二部分：SQL语句（只有SQL，无Markdown代码块，无其他文字）\n\n" +
            "示例输出：\n" +
            "查询思路：从steel_price表查询，过滤steel_diameter=20且近6个月的记录，按supplier_name分组取MIN(unit_price)排序。\n" +
            "SELECT supplier_name, MIN(unit_price) as min_price FROM steel_price WHERE ...";

    @Autowired
    private ChatModel chatModel;

    /**
     * 生成 CoT SQL。
     *
     * @param question      用户自然语言问题
     * @param schemaContext 经 Schema Linking 筛选后的详细 Schema 文本
     * @param fewShot       动态 Few-shot 示例文本（可为空字符串）
     * @param errorFeedback 上一轮自修正的错误反馈（首次生成时为空字符串）
     * @return 解析后的生成结果（thought + sql），或 CANNOT_QUERY 标记
     */
    public SqlGenerationResult generate(String question, String schemaContext,
                                        String fewShot, String errorFeedback) {
        String fewShotSection = StringUtils.isBlank(fewShot) ? "（暂无相似历史示例）" : fewShot;
        String errorSection = StringUtils.isBlank(errorFeedback) ? "" : errorFeedback + "\n\n";

        String promptText = String.format(PROMPT_TEMPLATE,
                schemaContext, fewShotSection, question, errorSection);

        String llmOutput;
        try {
            llmOutput = chatModel.call(new Prompt(new UserMessage(promptText)))
                    .getResult().getOutput().getText();
            log.debug("[SqlGeneration] LLM 原始输出:\n{}", llmOutput);
        } catch (Exception e) {
            log.error("[SqlGeneration] LLM 调用失败: {}", e.getMessage());
            throw new RuntimeException("SQL 生成 LLM 调用失败: " + e.getMessage(), e);
        }

        return parseOutput(llmOutput);
    }

    /**
     * 解析 LLM 输出为 SqlGenerationResult。
     * 期望格式：第一部分为查询思路行（"查询思路：..."），其余为 SQL 语句。
     */
    public SqlGenerationResult parseOutput(String llmOutput) {
        if (StringUtils.isBlank(llmOutput)) {
            log.warn("[SqlGeneration] LLM 输出为空");
            return SqlGenerationResult.cannotQuery("LLM 未生成有效输出");
        }

        String trimmed = llmOutput.trim();

        if (trimmed.contains(CANNOT_QUERY_MARKER)) {
            String thought = extractThought(trimmed);
            log.info("[SqlGeneration] LLM 返回 CANNOT_QUERY，thought={}", thought);
            return SqlGenerationResult.cannotQuery(thought);
        }

        String thought = extractThought(trimmed);
        String sql = extractSql(trimmed);

        if (StringUtils.isBlank(sql)) {
            log.warn("[SqlGeneration] 未能从 LLM 输出中提取 SQL，原始输出: {}", trimmed);
            return SqlGenerationResult.cannotQuery("无法从 LLM 输出中提取有效 SQL");
        }

        log.debug("[SqlGeneration] 解析成功 | thought={} | sql={}", thought, sql);
        return SqlGenerationResult.of(thought, sql);
    }

    /**
     * 提取查询思路：取第一行中 "查询思路：" 之后的内容，或整个首行。
     */
    private String extractThought(String output) {
        String[] lines = output.split("\n", -1);
        for (String line : lines) {
            String trimLine = line.trim();
            if (trimLine.startsWith(THOUGHT_PREFIX)) {
                return trimLine.substring(THOUGHT_PREFIX.length()).trim();
            }
            if (!trimLine.isEmpty()) {
                return trimLine;
            }
        }
        return "";
    }

    /**
     * 提取 SQL：跳过查询思路行，取第一个以 SELECT/WITH 开头的片段（忽略大小写）。
     * 同时清理 LLM 可能输出的 markdown 代码块标记。
     */
    private String extractSql(String output) {
        String cleaned = output
                .replaceAll("```sql", "")
                .replaceAll("```", "")
                .trim();

        String[] lines = cleaned.split("\n", -1);
        StringBuilder sqlBuilder = new StringBuilder();
        boolean sqlStarted = false;

        for (String line : lines) {
            String trimLine = line.trim();
            if (!sqlStarted) {
                if (trimLine.toUpperCase().startsWith("SELECT") ||
                    trimLine.toUpperCase().startsWith("WITH")) {
                    sqlStarted = true;
                    sqlBuilder.append(line).append("\n");
                }
            } else {
                sqlBuilder.append(line).append("\n");
            }
        }

        return sqlBuilder.toString().trim();
    }
}
