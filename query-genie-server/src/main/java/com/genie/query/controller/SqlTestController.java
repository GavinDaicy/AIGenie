package com.genie.query.controller;

import com.genie.query.controller.dto.SqlTestRequest;
import com.genie.query.domain.agent.sql.*;
import com.genie.query.domain.schema.dao.DbTableSchemaDAO;
import com.genie.query.domain.schema.model.DbTableSchema;
import com.genie.query.infrastructure.api.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL 端到端内部测试接口（仅开发/测试环境开放）。
 *
 * <p>用于迭代2验收：无需 Agent 层，直接输入自然语言问题验证 Text-to-SQL 全流水线。
 *
 * <p>通过配置项 {@code app.internal-api.enabled}（默认 false）控制是否开放，
 * 生产环境应保持关闭。
 *
 * @author daicy
 * @date 2026/4/2
 */
@RestController
@RequestMapping("/internal/sql")
public class SqlTestController {

    private static final Logger log = LoggerFactory.getLogger(SqlTestController.class);

    @Value("${app.internal-api.enabled:false}")
    private boolean internalApiEnabled;

    @Autowired
    private SqlQueryTool sqlQueryTool;

    @Autowired
    private SchemaLinkingService schemaLinkingService;

    @Autowired
    private SchemaContextBuilder schemaContextBuilder;

    @Autowired
    private SqlGenerationService sqlGenerationService;

    @Autowired
    private DbTableSchemaDAO dbTableSchemaDAO;

    @Autowired
    private ChatModel chatModel;

    /**
     * 端到端测试入口：输入自然语言问题 + 数据源 ID，返回 SQL + 查询结果。
     *
     * <pre>
     * POST /genie/api/internal/sql/test
     * {
     *   "question": "近半年直径20钢筋哪家供应商价格最低",
     *   "datasourceId": 1
     * }
     * </pre>
     */
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> testSql(@RequestBody SqlTestRequest request) {
        if (!internalApiEnabled) {
            return ApiResult.error(403, "内部测试接口未开启，请在配置中设置 app.internal-api.enabled=true");
        }

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ApiResult.error(400, "question 不能为空");
        }
        if (request.getDatasourceId() == null) {
            return ApiResult.error(400, "datasourceId 不能为空");
        }

        log.info("[SqlTest] question={}, datasourceId={}", request.getQuestion(), request.getDatasourceId());

        try {
            SqlQueryResult result = sqlQueryTool.querySql(request.getQuestion(), request.getDatasourceId());
            return ApiResult.success(buildResponse(result));
        } catch (Exception e) {
            log.error("[SqlTest] 执行异常: {}", e.getMessage(), e);
            return ApiResult.error(500, "执行失败: " + e.getMessage());
        }
    }

    /**
     * 调试接口：逐步执行流水线并返回每一步中间结果（Schema Linking → Schema Context → LLM 原始输出）。
     *
     * <pre>
     * POST /genie/api/internal/sql/debug
     * {"question": "查询包含文档个数最多的前三个知识库", "datasourceId": 1}
     * </pre>
     */
    @PostMapping("/debug")
    public ApiResult<Map<String, Object>> debugSql(@RequestBody SqlTestRequest request) {
        if (!internalApiEnabled) {
            return ApiResult.error(403, "内部测试接口未开启，请在配置中设置 app.internal-api.enabled=true");
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        String question = request.getQuestion();
        Long datasourceId = request.getDatasourceId();

        // Step 1: 查看已注册表
        List<DbTableSchema> allTables;
        try {
            allTables = dbTableSchemaDAO.listEnabledByDatasourceId(datasourceId);
            trace.put("step1_registeredTables", allTables.stream()
                    .map(t -> t.getTableName() + "(" + t.getAlias() + ") enabled=" + t.getEnabled())
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            trace.put("step1_error", e.getMessage());
            return ApiResult.success(trace);
        }

        if (allTables.isEmpty()) {
            trace.put("step1_conclusion", "❌ 无已启用的表，需先注册表结构并设置 enabled=1");
            return ApiResult.success(trace);
        }

        // Step 2: Schema Linking 结果
        SchemaLinkingResult linking;
        try {
            linking = schemaLinkingService.link(question, datasourceId);
            trace.put("step2_linkedTables", linking.getTables());
            trace.put("step2_linkedColumns", linking.getColumns());
            trace.put("step2_isEmpty", linking.isEmpty());
        } catch (Exception e) {
            trace.put("step2_error", e.getMessage());
            return ApiResult.success(trace);
        }

        if (linking.isEmpty()) {
            trace.put("step2_conclusion", "❌ Schema Linking 返回空表，LLM 判断无相关表");
            return ApiResult.success(trace);
        }

        // Step 3: 构建 Schema Context
        List<DbTableSchema> linkedTables = allTables.stream()
                .filter(t -> linking.getTables().contains(t.getTableName()))
                .collect(Collectors.toList());
        trace.put("step3_linkedTablesFound", linkedTables.stream()
                .map(DbTableSchema::getTableName).collect(Collectors.toList()));

        String schemaContext = schemaContextBuilder.buildSchemaContext(linkedTables, linking.getColumns());
        trace.put("step3_schemaContext", schemaContext);

        if (schemaContext.isBlank()) {
            trace.put("step3_conclusion", "❌ Schema Context 为空，相关表可能没有字段元数据（columns_json 为空）");
            return ApiResult.success(trace);
        }

        // Step 4: SQL 生成（直接调 LLM，返回原始输出）
        try {
            // 先拿到 prompt 内容再直接调 LLM，把原始输出也暴露出来
            String fewShotSection = "（暂无相似历史示例）";
            String promptText = String.format(
                    "你是一个专业的MySQL查询助手。\n\n" +
                    "## 相关数据库表（已筛选）\n%s\n\n" +
                    "## 参考示例（相似历史问题）\n%s\n\n" +
                    "## 用户问题\n%s\n\n" +
                    "## 生成规则\n" +
                    "1. 只生成 SELECT 语句\n" +
                    "2. 只使用上面列出的表和字段\n" +
                    "3. 无法用数据库回答时，回复：CANNOT_QUERY\n\n" +
                    "## 输出格式\n" +
                    "查询思路：...\nSELECT ...",
                    schemaContext, fewShotSection, question);
            trace.put("step4_prompt", promptText);

            String llmRawOutput = chatModel.call(new Prompt(new UserMessage(promptText)))
                    .getResult().getOutput().getText();
            trace.put("step4_llmRawOutput", llmRawOutput);

            SqlGenerationResult parsed = sqlGenerationService.parseOutput(llmRawOutput);
            trace.put("step4_parsedSql", parsed.getSql());
            trace.put("step4_parsedThought", parsed.getThought());
            trace.put("step4_isCannotQuery", parsed.isCannotQuery());

            if (parsed.isCannotQuery()) {
                trace.put("step4_conclusion",
                        "❌ LLM 返回 CANNOT_QUERY，原因：Schema Context 缺少表间关联字段（如外键），" +
                        "或描述不够清晰。请检查 step3_schemaContext 中的字段是否包含关联键");
            } else {
                trace.put("step4_conclusion", "✅ SQL 生成成功");
            }
        } catch (Exception e) {
            trace.put("step4_error", e.getMessage());
        }

        return ApiResult.success(trace);
    }

    private Map<String, Object> buildResponse(SqlQueryResult result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", result.getStatus().name());
        resp.put("thought", result.getThought());
        resp.put("sql", result.getSql());
        resp.put("errorMessage", result.getErrorMessage());
        resp.put("formattedText", result.getFormattedText());

        if (result.isSuccess() && result.getQueryResult() != null) {
            resp.put("rowCount", result.getQueryResult().rowCount());
            resp.put("executionTimeMs", result.getQueryResult().getExecutionTimeMs());
            resp.put("columns", result.getQueryResult().getColumns());
            resp.put("rows", result.getQueryResult().getRows());
        }
        return resp;
    }
}
