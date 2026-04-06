package com.genie.query.domain.agent.sql;

import com.genie.query.domain.agent.tool.sql.pipeline.SqlGenerationService;
import com.genie.query.domain.agent.tool.sql.model.SqlGenerationResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SqlGenerationService 单元测试。
 *
 * @author daicy
 * @date 2026/4/2
 */
@ExtendWith(MockitoExtension.class)
class SqlGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private SqlGenerationService sqlGenerationService;

    private static final String SCHEMA = "表名: steel_price (钢筋价格表)\n说明: 钢筋报价\n字段:\n  - unit_price [单价]\n";
    private static final String FEW_SHOT = "";
    private static final String NO_ERROR = "";

    @Test
    void generate_shouldReturnThoughtAndSql_whenLlmOutputIsWellFormed() {
        String llmOutput = "查询思路：从steel_price表查询，过滤steel_diameter=20且近6个月，按supplier_name分组取MIN(unit_price)。\n" +
                "SELECT supplier_name, MIN(unit_price) as min_price FROM steel_price WHERE steel_diameter = 20 LIMIT 10";
        mockLlmResponse(llmOutput);

        SqlGenerationResult result = sqlGenerationService.generate("近半年直径20钢筋最低价", SCHEMA, FEW_SHOT, NO_ERROR);

        assertThat(result.isCannotQuery()).isFalse();
        assertThat(result.getThought()).contains("steel_price");
        assertThat(result.getSql()).contains("SELECT");
        assertThat(result.getSql()).contains("steel_price");
        assertThat(result.getSql()).contains("LIMIT");
    }

    @Test
    void generate_shouldReturnCannotQuery_whenLlmOutputsCannotQueryMarker() {
        mockLlmResponse("CANNOT_QUERY");

        SqlGenerationResult result = sqlGenerationService.generate("什么是钢筋？", SCHEMA, FEW_SHOT, NO_ERROR);

        assertThat(result.isCannotQuery()).isTrue();
        assertThat(result.getSql()).isNull();
    }

    @Test
    void generate_shouldReturnCannotQuery_whenLlmOutputsCannotQueryWithThought() {
        String llmOutput = "查询思路：此问题无法通过数据库查询回答，应使用知识库。\nCANNOT_QUERY";
        mockLlmResponse(llmOutput);

        SqlGenerationResult result = sqlGenerationService.generate("什么是HRB400？", SCHEMA, FEW_SHOT, NO_ERROR);

        assertThat(result.isCannotQuery()).isTrue();
    }

    @Test
    void generate_shouldThrowException_whenLlmCallFails() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("网络超时"));

        assertThatThrownBy(() -> sqlGenerationService.generate("查询", SCHEMA, FEW_SHOT, NO_ERROR))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SQL 生成 LLM 调用失败");
    }

    @Test
    void parseOutput_shouldHandleMarkdownCodeBlock() {
        String llmOutput = "查询思路：查询steel_price表。\n" +
                "```sql\n" +
                "SELECT * FROM steel_price LIMIT 10\n" +
                "```";

        SqlGenerationResult result = sqlGenerationService.parseOutput(llmOutput);

        assertThat(result.isCannotQuery()).isFalse();
        assertThat(result.getSql()).contains("SELECT * FROM steel_price LIMIT 10");
        assertThat(result.getSql()).doesNotContain("```");
    }

    @Test
    void parseOutput_shouldHandleMultiLineSql() {
        String llmOutput = "查询思路：多表关联查询。\n" +
                "SELECT s.supplier_name, MIN(s.unit_price) as min_price\n" +
                "FROM steel_price s\n" +
                "WHERE s.steel_diameter = 20\n" +
                "  AND s.price_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)\n" +
                "GROUP BY s.supplier_name\n" +
                "ORDER BY min_price ASC\n" +
                "LIMIT 10";

        SqlGenerationResult result = sqlGenerationService.parseOutput(llmOutput);

        assertThat(result.isCannotQuery()).isFalse();
        assertThat(result.getSql()).contains("FROM steel_price s");
        assertThat(result.getSql()).contains("GROUP BY s.supplier_name");
        assertThat(result.getSql()).contains("LIMIT 10");
    }

    @Test
    void parseOutput_shouldReturnCannotQuery_whenOutputIsBlank() {
        SqlGenerationResult result = sqlGenerationService.parseOutput("   ");

        assertThat(result.isCannotQuery()).isTrue();
    }

    @Test
    void parseOutput_shouldExtractThought_fromFirstLine() {
        String llmOutput = "查询思路：从steel_price表统计各规格钢筋的平均价格。\n" +
                "SELECT steel_diameter, AVG(unit_price) FROM steel_price GROUP BY steel_diameter LIMIT 50";

        SqlGenerationResult result = sqlGenerationService.parseOutput(llmOutput);

        assertThat(result.getThought()).isEqualTo("从steel_price表统计各规格钢筋的平均价格。");
        assertThat(result.getSql()).startsWith("SELECT steel_diameter");
    }

    @Test
    void parseOutput_shouldHandleWithClause() {
        String llmOutput = "查询思路：使用CTE查询。\n" +
                "WITH ranked AS (SELECT * FROM steel_price ORDER BY unit_price)\n" +
                "SELECT * FROM ranked LIMIT 10";

        SqlGenerationResult result = sqlGenerationService.parseOutput(llmOutput);

        assertThat(result.isCannotQuery()).isFalse();
        assertThat(result.getSql()).contains("WITH ranked AS");
    }

    @Test
    void generate_shouldPassErrorFeedback_inPrompt() {
        String errorFeedback = "## 上一次尝试（第1次）的错误\n生成的SQL：SELECT ...\n错误信息：字段不存在";
        String llmOutput = "查询思路：修正后的查询。\nSELECT supplier_name FROM steel_price LIMIT 10";
        mockLlmResponse(llmOutput);

        SqlGenerationResult result = sqlGenerationService.generate("查询钢筋价格", SCHEMA, FEW_SHOT, errorFeedback);

        assertThat(result.isCannotQuery()).isFalse();
        assertThat(result.getSql()).isNotBlank();
    }

    private void mockLlmResponse(String text) {
        AssistantMessage assistantMessage = new AssistantMessage(text);
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }
}
