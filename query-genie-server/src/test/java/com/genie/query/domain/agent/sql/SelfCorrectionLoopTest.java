package com.genie.query.domain.agent.sql;

import com.genie.query.domain.agent.tool.sql.pipeline.SelfCorrectionLoop;
import com.genie.query.domain.agent.tool.sql.pipeline.SqlGenerationService;
import com.genie.query.domain.agent.tool.sql.pipeline.SqlSecurityValidator;
import com.genie.query.domain.agent.tool.sql.pipeline.DynamicFewShotService;
import com.genie.query.domain.agent.tool.sql.SqlExecutor;
import com.genie.query.domain.agent.tool.sql.model.SqlQueryResult;
import com.genie.query.domain.agent.tool.sql.model.SqlGenerationResult;
import com.genie.query.domain.agent.tool.sql.model.ValidationResult;
import com.genie.query.domain.agent.tool.sql.model.ExplainResult;
import com.genie.query.domain.agent.tool.sql.model.QueryResult;

import com.genie.query.domain.exception.QueryTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SelfCorrectionLoop 集成单元测试：覆盖首次成功/二次修正/超限失败三种场景。
 *
 * @author daicy
 * @date 2026/4/2
 */
@ExtendWith(MockitoExtension.class)
class SelfCorrectionLoopTest {

    @Mock private SqlGenerationService sqlGenerationService;
    @Mock private SqlSecurityValidator sqlSecurityValidator;
    @Mock private SqlExecutor sqlExecutor;
    @Mock private DynamicFewShotService dynamicFewShotService;

    @InjectMocks
    private SelfCorrectionLoop loop;

    private static final String QUESTION = "近半年直径20钢筋最低价";
    private static final String SCHEMA = "表名: steel_price\n字段: unit_price, steel_diameter\n";
    private static final String FEW_SHOT = "";
    private static final Set<String> ALLOWED = Set.of("steel_price");
    private static final Long DS_ID = 1L;
    private static final String SQL = "SELECT supplier_name, MIN(unit_price) FROM steel_price LIMIT 10";

    @Test
    void execute_shouldReturnSuccess_onFirstAttempt() {
        SqlGenerationResult generated = SqlGenerationResult.of("查询steel_price表", SQL);
        when(sqlGenerationService.generate(any(), any(), any(), any())).thenReturn(generated);
        when(sqlSecurityValidator.validate(any(), any())).thenReturn(ValidationResult.pass());
        when(sqlSecurityValidator.enforceLimitClause(any())).thenReturn(SQL);
        when(sqlExecutor.explain(any(), any())).thenReturn(ExplainResult.valid());
        when(sqlExecutor.execute(any(), any())).thenReturn(mockQueryResult(3));

        SqlQueryResult result = loop.execute(QUESTION, SCHEMA, FEW_SHOT, ALLOWED, DS_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getQueryResult().rowCount()).isEqualTo(3);
        verify(sqlGenerationService, times(1)).generate(any(), any(), any(), any());
        verify(dynamicFewShotService).saveSuccessfulPair(eq(QUESTION), any());
    }

    @Test
    void execute_shouldRetryAndSucceed_afterSyntaxError() {
        SqlGenerationResult badGen = SqlGenerationResult.of("错误查询", "SELECT * FROM steel_price WRHERE");
        SqlGenerationResult goodGen = SqlGenerationResult.of("修正后查询", SQL);

        when(sqlGenerationService.generate(any(), any(), any(), any()))
                .thenReturn(badGen)
                .thenReturn(goodGen);
        when(sqlSecurityValidator.validate(any(), any())).thenReturn(ValidationResult.pass());
        when(sqlSecurityValidator.enforceLimitClause(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(sqlExecutor.explain(any(), any()))
                .thenReturn(ExplainResult.invalid("You have an error near 'WRHERE'"))
                .thenReturn(ExplainResult.valid());
        when(sqlExecutor.execute(any(), any())).thenReturn(mockQueryResult(2));

        SqlQueryResult result = loop.execute(QUESTION, SCHEMA, FEW_SHOT, ALLOWED, DS_ID, 3);

        assertThat(result.isSuccess()).isTrue();
        verify(sqlGenerationService, times(2)).generate(any(), any(), any(), any());
    }

    @Test
    void execute_shouldReturnMaxRetriesExceeded_whenAllAttemptsFail() {
        SqlGenerationResult gen = SqlGenerationResult.of("查询", SQL);
        when(sqlGenerationService.generate(any(), any(), any(), any())).thenReturn(gen);
        when(sqlSecurityValidator.validate(any(), any())).thenReturn(ValidationResult.pass());
        when(sqlSecurityValidator.enforceLimitClause(any())).thenReturn(SQL);
        when(sqlExecutor.explain(any(), any())).thenReturn(ExplainResult.invalid("语法错误"));

        SqlQueryResult result = loop.execute(QUESTION, SCHEMA, FEW_SHOT, ALLOWED, DS_ID, 3);

        assertThat(result.getStatus()).isEqualTo(SqlQueryResult.Status.MAX_RETRIES_EXCEEDED);
        verify(sqlGenerationService, times(3)).generate(any(), any(), any(), any());
    }

    @Test
    void execute_shouldReturnCannotQuery_whenLlmReturnsCannotQuery() {
        when(sqlGenerationService.generate(any(), any(), any(), any()))
                .thenReturn(SqlGenerationResult.cannotQuery("无法回答"));

        SqlQueryResult result = loop.execute(QUESTION, SCHEMA, FEW_SHOT, ALLOWED, DS_ID);

        assertThat(result.getStatus()).isEqualTo(SqlQueryResult.Status.CANNOT_QUERY);
        verifyNoInteractions(sqlExecutor);
    }

    @Test
    void execute_shouldReturnSecurityFail_whenValidationFails() {
        SqlGenerationResult gen = SqlGenerationResult.of("查询", "DELETE FROM steel_price");
        when(sqlGenerationService.generate(any(), any(), any(), any())).thenReturn(gen);
        when(sqlSecurityValidator.validate(any(), any()))
                .thenReturn(ValidationResult.fail("包含禁止操作"));

        SqlQueryResult result = loop.execute(QUESTION, SCHEMA, FEW_SHOT, ALLOWED, DS_ID);

        assertThat(result.getStatus()).isEqualTo(SqlQueryResult.Status.SECURITY_FAIL);
        verifyNoInteractions(sqlExecutor);
    }

    @Test
    void execute_shouldReturnTimeout_whenQueryTimesOut() {
        SqlGenerationResult gen = SqlGenerationResult.of("查询", SQL);
        when(sqlGenerationService.generate(any(), any(), any(), any())).thenReturn(gen);
        when(sqlSecurityValidator.validate(any(), any())).thenReturn(ValidationResult.pass());
        when(sqlSecurityValidator.enforceLimitClause(any())).thenReturn(SQL);
        when(sqlExecutor.explain(any(), any())).thenReturn(ExplainResult.valid());
        when(sqlExecutor.execute(any(), any())).thenThrow(new QueryTimeoutException("超时"));

        SqlQueryResult result = loop.execute(QUESTION, SCHEMA, FEW_SHOT, ALLOWED, DS_ID);

        assertThat(result.getStatus()).isEqualTo(SqlQueryResult.Status.TIMEOUT);
        verify(sqlGenerationService, times(1)).generate(any(), any(), any(), any());
    }

    @Test
    void execute_shouldRetryOnEmptyResult_thenReturnEmptyResult() {
        SqlGenerationResult gen = SqlGenerationResult.of("查询", SQL);
        when(sqlGenerationService.generate(any(), any(), any(), any())).thenReturn(gen);
        when(sqlSecurityValidator.validate(any(), any())).thenReturn(ValidationResult.pass());
        when(sqlSecurityValidator.enforceLimitClause(any())).thenReturn(SQL);
        when(sqlExecutor.explain(any(), any())).thenReturn(ExplainResult.valid());
        when(sqlExecutor.execute(any(), any())).thenReturn(mockQueryResult(0));

        SqlQueryResult result = loop.execute(QUESTION, SCHEMA, FEW_SHOT, ALLOWED, DS_ID, 3);

        assertThat(result.getStatus()).isEqualTo(SqlQueryResult.Status.EMPTY_RESULT);
        verify(sqlGenerationService, times(3)).generate(any(), any(), any(), any());
    }

    private QueryResult mockQueryResult(int rowCount) {
        List<String> cols = List.of("supplier_name", "min_price");
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(Map.of("supplier_name", "供应商" + i, "min_price", 4000 + i * 100));
        }
        return QueryResult.of(cols, rows, 45L);
    }
}
