package com.genie.query.domain.agent.tool.sql.pipeline;

import com.genie.query.domain.agent.tool.sql.model.SchemaLinkingResult;

import com.alibaba.fastjson2.JSON;
import com.genie.query.domain.schema.dao.DbTableSchemaDAO;
import com.genie.query.domain.schema.model.DbTableSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Schema Linking 服务（Step 1）：调用 LLM 从已注册表摘要中识别回答问题所需的相关表和字段。
 *
 * <p>流程：
 * <ol>
 *   <li>从 DB 加载指定数据源下已启用的表摘要（轻量：仅表名/别名/描述）</li>
 *   <li>将摘要 + 用户问题组装为 Prompt，单次调用 LLM</li>
 *   <li>解析 LLM 输出的 JSON，得到相关表名列表和字段列表</li>
 *   <li>兜底：若 LLM 返回空或解析失败，使用全部已注册表</li>
 * </ol>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class SchemaLinkingService {

    private static final Logger log = LoggerFactory.getLogger(SchemaLinkingService.class);

    private static final String PROMPT_TEMPLATE =
            "你是一个数据库专家。请从以下数据库表列表中，识别回答用户问题所需的表和字段。\n\n" +
            "## 可用的数据库表（摘要）\n" +
            "%s\n" +
            "（格式：表名(别名) - 说明）\n\n" +
            "## 用户问题\n" +
            "%s\n\n" +
            "## 输出要求\n" +
            "只输出JSON，不要任何解释：\n" +
            "{\"tables\": [\"table1\", \"table2\"], \"columns\": {\"table1\": [\"col1\", \"col2\"], \"table2\": [\"col3\"]}}\n\n" +
            "如果没有任何表可以回答此问题，输出：{\"tables\": [], \"columns\": {}}";

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private DbTableSchemaDAO dbTableSchemaDAO;

    @Autowired
    private SchemaContextBuilder schemaContextBuilder;

    /**
     * 识别回答问题所需的相关表和字段。
     *
     * @param question     用户自然语言问题
     * @param datasourceId 数据源 ID
     * @return Schema Linking 结果；若无匹配表则返回兜底全量结果
     */
    public SchemaLinkingResult link(String question, Long datasourceId) {
        List<DbTableSchema> allTables = dbTableSchemaDAO.listEnabledByDatasourceId(datasourceId);
        if (allTables.isEmpty()) {
            log.warn("[SchemaLinking] 数据源 {} 无已启用的表，跳过 LLM 识别", datasourceId);
            return SchemaLinkingResult.empty();
        }

        String tablesSummary = schemaContextBuilder.buildTablesSummary(allTables);
        String promptText = String.format(PROMPT_TEMPLATE, tablesSummary, question);

        String llmOutput;
        try {
            llmOutput = chatModel.call(new Prompt(new UserMessage(promptText)))
                    .getResult().getOutput().getText();
            log.debug("[SchemaLinking] LLM 原始输出: {}", llmOutput);
        } catch (Exception e) {
            log.error("[SchemaLinking] LLM 调用失败，兜底使用全部 {} 张表: {}", allTables.size(), e.getMessage());
            return SchemaLinkingResult.fallback(allTables);
        }

        SchemaLinkingResult result = parseResult(llmOutput);
        if (result.isEmpty()) {
            log.info("[SchemaLinking] LLM 返回空表列表，兜底使用全部 {} 张表", allTables.size());
            return SchemaLinkingResult.fallback(allTables);
        }

        log.info("[SchemaLinking] 问题「{}」命中表: {}", question, result.getTables());
        return result;
    }

    private SchemaLinkingResult parseResult(String llmOutput) {
        try {
            String json = extractJson(llmOutput);
            SchemaLinkingResult result = JSON.parseObject(json, SchemaLinkingResult.class);
            return result != null ? result : SchemaLinkingResult.empty();
        } catch (Exception e) {
            log.warn("[SchemaLinking] JSON 解析失败: {}，原始输出: {}", e.getMessage(), llmOutput);
            return SchemaLinkingResult.empty();
        }
    }

    /**
     * 从 LLM 输出中提取 JSON 内容（处理 LLM 可能附带的多余说明文字或 markdown 代码块）。
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }
}
