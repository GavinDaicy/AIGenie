package com.genie.query.domain.agent.tool.sql;

import com.genie.query.domain.agent.tool.sql.pipeline.SchemaLinkingService;
import com.genie.query.domain.agent.tool.sql.pipeline.SchemaContextBuilder;
import com.genie.query.domain.agent.tool.sql.pipeline.DynamicFewShotService;
import com.genie.query.domain.agent.tool.sql.pipeline.SelfCorrectionLoop;
import com.genie.query.domain.agent.tool.sql.pipeline.ResultFormatter;
import com.genie.query.domain.agent.tool.sql.model.SchemaLinkingResult;
import com.genie.query.domain.agent.tool.sql.model.SqlQueryResult;
import com.genie.query.domain.agent.tool.sql.model.QueryResult;

import com.genie.query.domain.agent.citation.CitationItem;
import com.genie.query.domain.agent.citation.CitationRegistry;
import com.genie.query.domain.schema.dao.DbTableSchemaDAO;
import com.genie.query.domain.schema.model.DbTableSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.genie.query.domain.agent.tool.spi.AgentTool;
import com.genie.query.domain.agent.tool.spi.AgentToolMeta;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Text-to-SQL 四步流水线编排入口。
 *
 * <p>调用链：
 * <pre>
 * querySql(question, datasourceId)
 *   → Step 1: SchemaLinkingService.link()       — LLM 识别相关表
 *   → 加载相关表完整 Schema 对象
 *   → Step 2前: SchemaContextBuilder.buildSchemaContext()  — 构建精简Schema
 *   → Step 2前: DynamicFewShotService.retrieve()           — 动态 Few-shot
 *   → Step 2-4: SelfCorrectionLoop.execute()               — CoT生成+安全校验+执行+自修正
 *   → Step 5:  ResultFormatter.format()                    — 格式化输出
 * </pre>
 *
 * @author daicy
 * @date 2026/4/2
 */
@AgentToolMeta(name = "sql", group = "data", forceable = true, toolForceField = "sql")
@Component
public class SqlQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryTool.class);

    private static final int FEW_SHOT_TOP_K = 3;

    @Autowired
    private SchemaLinkingService schemaLinkingService;

    @Autowired
    private SchemaContextBuilder schemaContextBuilder;

    @Autowired
    private DynamicFewShotService dynamicFewShotService;

    @Autowired
    private SelfCorrectionLoop selfCorrectionLoop;

    @Autowired
    private ResultFormatter resultFormatter;

    @Autowired
    private DbTableSchemaDAO dbTableSchemaDAO;

    /**
     * Spring AI Tool Calling 入口：执行 Text-to-SQL 全流水线，返回格式化后的查询结果文本。
     * 此方法由 Spring AI Agent 框架通过 Function Calling 调用。
     *
     * @param question     用户自然语言问题
     * @param datasourceId 目标数据源 ID
     * @return 格式化结果文本（Markdown 表格或 LLM 摘要）；失败时返回错误说明
     */
    @Tool(description = "将自然语言转为SQL查询业务数据库，适用于价格分析、订单统计、供应商比较等数据聚合问题")
    public String querySql(
            @ToolParam(description = "用户的自然语言问题") String question,
            @ToolParam(description = "目标数据源ID（单个整数，必填）；如有多个可用数据源且问题涉及多个数据源，请对每个ID分别调用本工具") Long datasourceId) {
        SqlQueryResult result = executeQuery(question, datasourceId);
        if (result.isSuccess() && result.getFormattedText() != null) {
            registerCitation(result);
            return result.getFormattedText();
        }
        return result.getErrorMessage() != null ? result.getErrorMessage() : "查询未能返回结果";
    }

    /**
     * 执行 Text-to-SQL 全流水线，返回结构化的查询结果对象。
     * 供内部测试接口（SqlTestController）直接调用。
     *
     * @param question     用户自然语言问题
     * @param datasourceId 目标数据源 ID
     * @return 结构化结果（含 SQL、查询数据、格式化文本）
     */
    public SqlQueryResult executeQuery(String question, Long datasourceId) {
        log.info("[SqlQueryTool] 开始查询 | datasourceId={} | question={}", datasourceId, question);

        // Step 1: Schema Linking — LLM 从全量表中识别相关表和字段
        SchemaLinkingResult linking = schemaLinkingService.link(question, datasourceId);
        if (linking.isEmpty()) {
            log.info("[SqlQueryTool] Schema Linking 无匹配表，返回 CANNOT_QUERY");
            return SqlQueryResult.cannotQuery();
        }

        // 加载相关表的完整 Schema 对象（含 columns_json）
        List<DbTableSchema> allTables = dbTableSchemaDAO.listEnabledByDatasourceId(datasourceId);
        List<DbTableSchema> linkedTables = allTables.stream()
                .filter(t -> linking.getTables().contains(t.getTableName()))
                .collect(Collectors.toList());

        if (linkedTables.isEmpty()) {
            log.warn("[SqlQueryTool] 关联表在数据库中未找到，tables={}", linking.getTables());
            return SqlQueryResult.cannotQuery();
        }

        // Step 2前：构建详细 Schema 上下文（含字段别名 + sample_values）
        String schemaContext = schemaContextBuilder.buildSchemaContext(
                linkedTables, linking.getColumns());

        // Step 2前：动态 Few-shot 检索（迭代2暂返回空）
        String fewShot = dynamicFewShotService.retrieve(question, FEW_SHOT_TOP_K);

        // 白名单：只允许已注册表
        Set<String> allowedTables = allTables.stream()
                .map(t -> t.getTableName().toLowerCase())
                .collect(Collectors.toSet());

        // Step 2-4：CoT 生成 + 安全校验 + 执行 + 自修正循环
        SqlQueryResult result = selfCorrectionLoop.execute(
                question, schemaContext, fewShot, allowedTables, datasourceId);

        if (result.isSuccess()) {
            // Step 5：格式化结果
            String formatted = resultFormatter.format(result.getQueryResult(), result.getSql());
            log.info("[SqlQueryTool] 查询成功 | 行数={}", result.getQueryResult().rowCount());

            SqlQueryResult formattedResult = SqlQueryResult.success(
                    result.getSql(), result.getQueryResult(), result.getThought());
            formattedResult.setFormattedText(formatted);
            return formattedResult;
        }

        log.info("[SqlQueryTool] 查询未成功 | status={} | message={}",
                result.getStatus(), result.getErrorMessage());
        return result;
    }

    private void registerCitation(SqlQueryResult result) {
        QueryResult qr = result.getQueryResult();
        CitationItem item = new CitationItem();
        item.setType(CitationItem.CitationType.SQL);
        item.setSql(result.getSql());
        if (qr != null) {
            item.setColumns(qr.getColumns());
            item.setRowCount(qr.rowCount());
            item.setExecutionTimeMs(qr.getExecutionTimeMs());
            item.setRows(convertRows(qr.getColumns(), qr.getRows()));
        }
        CitationRegistry.register(item);
    }

    private List<List<Object>> convertRows(List<String> columns, List<Map<String, Object>> rawRows) {
        if (rawRows == null || columns == null) return new ArrayList<>();
        List<List<Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            List<Object> values = new ArrayList<>();
            for (String col : columns) {
                values.add(row.get(col));
            }
            result.add(values);
        }
        return result;
    }
}
