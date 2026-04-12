package com.genie.query.domain.agent.tool.sql.pipeline;

import com.genie.query.domain.agent.tool.sql.model.QueryResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 结果格式化器（Step 5）：将 QueryResult 转换为易读的 Markdown 文本。
 *
 * <ul>
 *   <li>≤20行 → Markdown 表格（含行数/耗时统计信息）</li>
 *   <li>>20行 → LLM 自然语言摘要（取前20行作为样本）</li>
 * </ul>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Component
public class ResultFormatter {

    private static final Logger log = LoggerFactory.getLogger(ResultFormatter.class);

    private static final int MARKDOWN_TABLE_MAX_ROWS = 20;

    private static final int MAX_ROWS_LIMIT = 500;

    private static final String SUMMARY_PROMPT_TEMPLATE =
            "以下是SQL查询结果（共%d行），请用简洁的中文总结分析结论，不超过200字：\n\n%s";

    @Autowired
    private ChatModel chatModel;

    /**
     * 格式化查询结果。
     *
     * @param result 查询结果
     * @param sql    执行的 SQL（附在输出末尾供前端展示）
     * @return Markdown 格式的结果文本
     */
    public String format(QueryResult result, String sql) {
        if (result == null || result.isEmpty()) {
            return "查询结果为空。";
        }

        String content;
        if (result.rowCount() <= MARKDOWN_TABLE_MAX_ROWS) {
            content = toMarkdownTable(result);
        } else {
            content = toLlmSummary(result);
        }

        return content + buildFooter(result, sql);
    }

    /**
     * 将结果集转为 Markdown 表格（≤20行）。
     */
    public String toMarkdownTable(QueryResult result) {
        List<String> columns = result.getColumns();
        List<Map<String, Object>> rows = result.getRows();

        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
        sb.append("|").append(columns.stream().map(c -> "---|").collect(Collectors.joining())).append("\n");
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            sb.append(columns.stream()
                    .map(col -> formatCell(row.get(col)))
                    .collect(Collectors.joining(" | ")));
            sb.append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 超过20行时调用 LLM 生成自然语言摘要（取前20行样本）。
     */
    private String toLlmSummary(QueryResult result) {
        List<Map<String, Object>> sample = result.getRows().subList(
                0, Math.min(MARKDOWN_TABLE_MAX_ROWS, result.rowCount()));
        String sampleTable = toMarkdownTable(QueryResult.of(result.getColumns(), sample, 0));
        String promptText = String.format(SUMMARY_PROMPT_TEMPLATE, result.rowCount(), sampleTable);

        try {
            String summary = chatModel.call(new Prompt(new UserMessage(promptText)))
                    .getResult().getOutput().getText();
            if (StringUtils.isNotBlank(summary)) {
                return summary.trim() + "\n\n";
            }
        } catch (Exception e) {
            log.warn("[ResultFormatter] LLM 摘要生成失败，降级为表格截断: {}", e.getMessage());
        }

        return sampleTable + "（结果超过" + MARKDOWN_TABLE_MAX_ROWS + "行，仅展示前" + MARKDOWN_TABLE_MAX_ROWS + "行）\n\n";
    }

    private String buildFooter(QueryResult result, String sql) {
        StringBuilder footer = new StringBuilder("\n");
        footer.append("（共 ").append(result.rowCount()).append(" 条记录");
        footer.append("，执行耗时 ").append(result.getExecutionTimeMs()).append("ms）");
        if (result.rowCount() >= MAX_ROWS_LIMIT) {
            footer.append("\n【注意：查询结果已被限制为最多 500 条，实际数据可能更多，请添加更精确的筛选条件后再查询】");
        }
        if (StringUtils.isNotBlank(sql)) {
            footer.append("\n\n<details><summary>查看执行 SQL</summary>\n\n```sql\n")
                  .append(sql.trim())
                  .append("\n```\n</details>");
        }
        return footer.toString();
    }

    private String formatCell(Object value) {
        if (value == null) return "";
        return value.toString().replace("|", "\\|").replace("\n", " ");
    }
}
