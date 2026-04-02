package com.genie.query.controller;

import com.genie.query.controller.dto.SqlTestRequest;
import com.genie.query.domain.agent.sql.SqlQueryResult;
import com.genie.query.domain.agent.sql.SqlQueryTool;
import com.genie.query.infrastructure.api.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

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
