package com.genie.query.domain.agent.sql;

import com.genie.query.domain.agent.tool.sql.pipeline.SchemaLinkingService;
import com.genie.query.domain.agent.tool.sql.pipeline.SchemaContextBuilder;
import com.genie.query.domain.agent.tool.sql.model.SchemaLinkingResult;

import com.genie.query.domain.schema.dao.DbTableSchemaDAO;
import com.genie.query.domain.schema.model.DbTableSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SchemaLinkingService 单元测试。
 *
 * @author daicy
 * @date 2026/4/2
 */
@ExtendWith(MockitoExtension.class)
class SchemaLinkingServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private DbTableSchemaDAO dbTableSchemaDAO;

    @Mock
    private SchemaContextBuilder schemaContextBuilder;

    @InjectMocks
    private SchemaLinkingService schemaLinkingService;

    private List<DbTableSchema> mockTables;

    private String summaryOf(List<DbTableSchema> tables) {
        return tables.stream()
                .filter(t -> t.getEnabled() != null && t.getEnabled() == 1)
                .map(t -> t.getTableName() + "(" + t.getAlias() + ") - " + t.getDescription())
                .collect(Collectors.joining("\n"));
    }

    @BeforeEach
    void setUp() {
        DbTableSchema steelPrice = new DbTableSchema();
        steelPrice.setTableName("steel_price");
        steelPrice.setAlias("钢筋价格表");
        steelPrice.setDescription("记录各供应商钢筋报价，含规格、单价、日期");
        steelPrice.setEnabled(1);

        DbTableSchema orderRecord = new DbTableSchema();
        orderRecord.setTableName("order_record");
        orderRecord.setAlias("订单记录表");
        orderRecord.setDescription("客户订单信息，含订单金额、数量、日期");
        orderRecord.setEnabled(1);

        DbTableSchema customerInfo = new DbTableSchema();
        customerInfo.setTableName("customer_info");
        customerInfo.setAlias("客户信息表");
        customerInfo.setDescription("客户基本信息，含名称、联系方式、地区");
        customerInfo.setEnabled(1);

        mockTables = List.of(steelPrice, orderRecord, customerInfo);
    }

    @Test
    void link_shouldIdentifySteelPriceTable_whenAskingAboutSteelPrice() {
        when(dbTableSchemaDAO.listEnabledByDatasourceId(1L)).thenReturn(mockTables);
        when(schemaContextBuilder.buildTablesSummary(mockTables)).thenReturn(summaryOf(mockTables));
        String llmJson = "{\"tables\": [\"steel_price\"], \"columns\": {\"steel_price\": [\"supplier_name\", \"unit_price\", \"steel_diameter\", \"price_date\"]}}";
        mockLlmResponse(llmJson);

        SchemaLinkingResult result = schemaLinkingService.link("近半年直径20钢筋价格", 1L);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getTables()).containsExactly("steel_price");
        assertThat(result.getColumns()).containsKey("steel_price");
        assertThat(result.getColumns().get("steel_price"))
                .contains("supplier_name", "unit_price", "steel_diameter", "price_date");
    }

    @Test
    void link_shouldFallbackToAllTables_whenLlmReturnsEmptyTables() {
        when(dbTableSchemaDAO.listEnabledByDatasourceId(1L)).thenReturn(mockTables);
        when(schemaContextBuilder.buildTablesSummary(mockTables)).thenReturn(summaryOf(mockTables));
        mockLlmResponse("{\"tables\": [], \"columns\": {}}");

        SchemaLinkingResult result = schemaLinkingService.link("随便问个问题", 1L);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getTables()).containsExactlyInAnyOrder("steel_price", "order_record", "customer_info");
    }

    @Test
    void link_shouldFallbackToAllTables_whenLlmOutputIsMalformed() {
        when(dbTableSchemaDAO.listEnabledByDatasourceId(1L)).thenReturn(mockTables);
        when(schemaContextBuilder.buildTablesSummary(mockTables)).thenReturn(summaryOf(mockTables));
        mockLlmResponse("抱歉我无法理解");

        SchemaLinkingResult result = schemaLinkingService.link("近半年直径20钢筋价格", 1L);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getTables()).hasSize(3);
    }

    @Test
    void link_shouldFallbackToAllTables_whenLlmThrowsException() {
        when(dbTableSchemaDAO.listEnabledByDatasourceId(1L)).thenReturn(mockTables);
        when(schemaContextBuilder.buildTablesSummary(mockTables)).thenReturn(summaryOf(mockTables));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM 服务不可用"));

        SchemaLinkingResult result = schemaLinkingService.link("近半年直径20钢筋价格", 1L);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getTables()).hasSize(3);
    }

    @Test
    void link_shouldReturnEmpty_whenNoTablesRegistered() {
        when(dbTableSchemaDAO.listEnabledByDatasourceId(99L)).thenReturn(List.of());

        SchemaLinkingResult result = schemaLinkingService.link("近半年直径20钢筋价格", 99L);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void link_shouldHandleJsonWithExtraText_fromLlm() {
        when(dbTableSchemaDAO.listEnabledByDatasourceId(1L)).thenReturn(mockTables);
        when(schemaContextBuilder.buildTablesSummary(mockTables)).thenReturn(summaryOf(mockTables));
        String llmOutput = "根据您的问题，识别到以下相关表：\n" +
                "{\"tables\": [\"steel_price\"], \"columns\": {\"steel_price\": [\"unit_price\", \"price_date\"]}}";
        mockLlmResponse(llmOutput);

        SchemaLinkingResult result = schemaLinkingService.link("钢筋价格趋势", 1L);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getTables()).containsExactly("steel_price");
    }

    private void mockLlmResponse(String text) {
        AssistantMessage assistantMessage = new AssistantMessage(text);
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }
}
