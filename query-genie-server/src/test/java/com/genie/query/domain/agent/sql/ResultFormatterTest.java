package com.genie.query.domain.agent.sql;

import com.genie.query.domain.agent.tool.sql.pipeline.ResultFormatter;
import com.genie.query.domain.agent.tool.sql.model.QueryResult;
import com.genie.query.domain.agent.tool.sql.model.SqlQueryResult;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ResultFormatter 单元测试。
 *
 * @author daicy
 * @date 2026/4/2
 */
@ExtendWith(MockitoExtension.class)
class ResultFormatterTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private ResultFormatter formatter;

    private static final String SQL = "SELECT supplier_name, MIN(unit_price) FROM steel_price LIMIT 10";

    @Test
    void format_shouldReturnMarkdownTable_whenRowsLeq20() {
        QueryResult result = buildResult(List.of("supplier_name", "min_price"), 3);

        String output = formatter.format(result, SQL);

        assertThat(output).contains("| supplier_name | min_price |");
        assertThat(output).contains("供应商0");
        assertThat(output).contains("共 3 条记录");
        assertThat(output).contains("执行耗时 45ms");
    }

    @Test
    void format_shouldIncludeSqlInFooter() {
        QueryResult result = buildResult(List.of("col1"), 1);

        String output = formatter.format(result, SQL);

        assertThat(output).contains("SELECT supplier_name");
    }

    @Test
    void toMarkdownTable_shouldFormatHeaderAndSeparatorAndRows() {
        List<String> cols = List.of("name", "price");
        List<Map<String, Object>> rows = List.of(
                buildRow(cols, "张氏钢材", "4180.00"),
                buildRow(cols, "李记钢铁厂", "4210.50")
        );
        QueryResult result = QueryResult.of(cols, rows, 30L);

        String table = formatter.toMarkdownTable(result);

        assertThat(table).contains("| name | price |");
        assertThat(table).contains("| 张氏钢材 | 4180.00 |");
        assertThat(table).contains("| 李记钢铁厂 | 4210.50 |");
    }

    @Test
    void format_shouldCallLlmForSummary_whenRowsGt20() {
        QueryResult result = buildResult(List.of("supplier_name", "price"), 25);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockLlmResponse("共25家供应商参与报价，最低价格约4000元/吨。"));

        String output = formatter.format(result, SQL);

        assertThat(output).contains("共25家供应商参与报价");
        assertThat(output).contains("共 25 条记录");
    }

    @Test
    void format_shouldFallbackToTruncatedTable_whenLlmSummaryFails() {
        QueryResult result = buildResult(List.of("col1"), 25);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM 不可用"));

        String output = formatter.format(result, SQL);

        assertThat(output).contains("结果超过20行");
        assertThat(output).contains("共 25 条记录");
    }

    @Test
    void format_shouldReturnEmptyMessage_whenResultIsNull() {
        String output = formatter.format(null, SQL);
        assertThat(output).isEqualTo("查询结果为空。");
    }

    @Test
    void format_shouldReturnEmptyMessage_whenResultIsEmpty() {
        QueryResult result = QueryResult.of(List.of("col1"), List.of(), 10L);
        String output = formatter.format(result, SQL);
        assertThat(output).isEqualTo("查询结果为空。");
    }

    @Test
    void toMarkdownTable_shouldEscapePipeCharInCell() {
        List<String> cols = List.of("note");
        List<Map<String, Object>> rows = List.of(Map.of("note", "A|B"));
        QueryResult result = QueryResult.of(cols, rows, 5L);

        String table = formatter.toMarkdownTable(result);

        assertThat(table).contains("A\\|B");
    }

    private QueryResult buildResult(List<String> columns, int rowCount) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String col : columns) {
                row.put(col, col.equals("supplier_name") ? "供应商" + i : 4000 + i * 10);
            }
            rows.add(row);
        }
        return QueryResult.of(columns, rows, 45L);
    }

    private Map<String, Object> buildRow(List<String> cols, Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < cols.size(); i++) {
            row.put(cols.get(i), values[i]);
        }
        return row;
    }

    private ChatResponse mockLlmResponse(String text) {
        AssistantMessage msg = new AssistantMessage(text);
        Generation gen = new Generation(msg);
        return new ChatResponse(List.of(gen));
    }
}
