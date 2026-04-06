package com.genie.query.domain.agent.sql;

import com.genie.query.domain.agent.tool.sql.pipeline.SchemaContextBuilder;

import com.alibaba.fastjson2.JSON;
import com.genie.query.domain.schema.model.ColumnMeta;
import com.genie.query.domain.schema.model.DbTableSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SchemaContextBuilder 单元测试。
 *
 * @author daicy
 * @date 2026/4/2
 */
class SchemaContextBuilderTest {

    private SchemaContextBuilder builder;
    private DbTableSchema steelPriceTable;

    @BeforeEach
    void setUp() {
        builder = new SchemaContextBuilder();

        ColumnMeta supplierName = new ColumnMeta();
        supplierName.setName("supplier_name");
        supplierName.setAlias("供应商名称");
        supplierName.setType("VARCHAR");
        supplierName.setDescription("供应商公司名称");
        supplierName.setSampleValues(List.of("张氏钢材有限公司", "李记钢铁厂"));

        ColumnMeta steelDiameter = new ColumnMeta();
        steelDiameter.setName("steel_diameter");
        steelDiameter.setAlias("钢筋直径(mm)");
        steelDiameter.setType("INT");
        steelDiameter.setDescription("钢筋直径，单位mm");
        steelDiameter.setSampleValues(List.of("8", "10", "12", "16", "20", "25", "32"));

        ColumnMeta unitPrice = new ColumnMeta();
        unitPrice.setName("unit_price");
        unitPrice.setAlias("单价(元/吨)");
        unitPrice.setType("DECIMAL(10,2)");
        unitPrice.setDescription("每吨含税价格");
        unitPrice.setSampleValues(List.of("4180.00", "4280.00", "4350.50"));

        ColumnMeta priceDate = new ColumnMeta();
        priceDate.setName("price_date");
        priceDate.setAlias("报价日期");
        priceDate.setType("DATE");
        priceDate.setDescription("该条价格的报价日期");
        priceDate.setSampleValues(List.of("2024-03-15", "2024-06-01", "2025-01-08"));

        steelPriceTable = new DbTableSchema();
        steelPriceTable.setTableName("steel_price");
        steelPriceTable.setAlias("钢筋价格表");
        steelPriceTable.setDescription("记录各供应商钢筋报价，含规格、单价、日期");
        steelPriceTable.setEnabled(1);
        steelPriceTable.setColumnsJson(JSON.toJSONString(
                List.of(supplierName, steelDiameter, unitPrice, priceDate)));
    }

    @Test
    void buildSchemaContext_shouldIncludeTableNameAndAlias() {
        String context = builder.buildSchemaContext(List.of(steelPriceTable));

        assertThat(context).contains("表名: steel_price");
        assertThat(context).contains("(钢筋价格表)");
        assertThat(context).contains("说明: 记录各供应商钢筋报价");
    }

    @Test
    void buildSchemaContext_shouldIncludeFieldAliasAndType() {
        String context = builder.buildSchemaContext(List.of(steelPriceTable));

        assertThat(context).contains("supplier_name [供应商名称] VARCHAR");
        assertThat(context).contains("steel_diameter [钢筋直径(mm)] INT");
        assertThat(context).contains("unit_price [单价(元/吨)] DECIMAL(10,2)");
    }

    @Test
    void buildSchemaContext_shouldIncludeSampleValues() {
        String context = builder.buildSchemaContext(List.of(steelPriceTable));

        assertThat(context).contains("示例值");
        assertThat(context).contains("张氏钢材有限公司");
        assertThat(context).contains("4180.00");
        assertThat(context).contains("2024-03-15");
    }

    @Test
    void buildSchemaContext_withColumnFilter_shouldOnlyIncludeLinkedColumns() {
        Map<String, List<String>> linkedColumns = Map.of(
                "steel_price", List.of("unit_price", "price_date"));

        String context = builder.buildSchemaContext(List.of(steelPriceTable), linkedColumns);

        assertThat(context).contains("unit_price");
        assertThat(context).contains("price_date");
        assertThat(context).doesNotContain("supplier_name");
        assertThat(context).doesNotContain("steel_diameter");
    }

    @Test
    void buildSchemaContext_withNullColumnFilter_shouldIncludeAllColumns() {
        String context = builder.buildSchemaContext(List.of(steelPriceTable), null);

        assertThat(context).contains("supplier_name");
        assertThat(context).contains("steel_diameter");
        assertThat(context).contains("unit_price");
        assertThat(context).contains("price_date");
    }

    @Test
    void buildSchemaContext_withNoSampleValues_shouldNotAppendSampleValuesLabel() {
        ColumnMeta noSamples = new ColumnMeta();
        noSamples.setName("remark");
        noSamples.setAlias("备注");
        noSamples.setType("VARCHAR");
        noSamples.setDescription("备注信息");

        DbTableSchema table = new DbTableSchema();
        table.setTableName("some_table");
        table.setAlias("某表");
        table.setDescription("描述");
        table.setEnabled(1);
        table.setColumnsJson(JSON.toJSONString(List.of(noSamples)));

        String context = builder.buildSchemaContext(List.of(table));

        assertThat(context).contains("remark [备注] VARCHAR");
        assertThat(context).doesNotContain("示例值");
    }

    @Test
    void buildTablesSummary_shouldFormatEachTableAsOneLine() {
        DbTableSchema orderRecord = new DbTableSchema();
        orderRecord.setTableName("order_record");
        orderRecord.setAlias("订单记录表");
        orderRecord.setDescription("客户订单信息");
        orderRecord.setEnabled(1);

        String summary = builder.buildTablesSummary(List.of(steelPriceTable, orderRecord));

        assertThat(summary).contains("steel_price(钢筋价格表) - 记录各供应商钢筋报价");
        assertThat(summary).contains("order_record(订单记录表) - 客户订单信息");
    }

    @Test
    void buildTablesSummary_shouldExcludeDisabledTables() {
        DbTableSchema disabled = new DbTableSchema();
        disabled.setTableName("archived_data");
        disabled.setAlias("归档表");
        disabled.setDescription("已归档数据");
        disabled.setEnabled(0);

        String summary = builder.buildTablesSummary(List.of(steelPriceTable, disabled));

        assertThat(summary).contains("steel_price");
        assertThat(summary).doesNotContain("archived_data");
    }

    @Test
    void buildSchemaContext_withNonMatchingColumnFilter_shouldFallbackToAllColumns() {
        Map<String, List<String>> wrongCols = Map.of("steel_price", List.of("knowledge_id"));

        String context = builder.buildSchemaContext(List.of(steelPriceTable), wrongCols);

        assertThat(context).contains("supplier_name");
        assertThat(context).contains("steel_diameter");
        assertThat(context).contains("unit_price");
        assertThat(context).contains("price_date");
    }

    @Test
    void buildTablesSummary_shouldReturnEmptyString_whenNoEnabledTables() {
        DbTableSchema disabled = new DbTableSchema();
        disabled.setTableName("some_table");
        disabled.setAlias("某表");
        disabled.setDescription("某表描述");
        disabled.setEnabled(0);

        String summary = builder.buildTablesSummary(List.of(disabled));

        assertThat(summary).isEmpty();
    }
}
