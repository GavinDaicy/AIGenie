package com.genie.query.domain.agent.sql;

import com.genie.query.domain.schema.model.ColumnMeta;
import com.genie.query.domain.schema.model.DbTableSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema 上下文构建器：为 SQL 生成 Prompt 提供两种 Schema 文本。
 *
 * <ul>
 *   <li>{@link #buildSchemaContext} — 详细 Schema（含字段别名 + 样本值），供 Step 2 CoT SQL 生成使用</li>
 *   <li>{@link #buildTablesSummary} — 轻量摘要（仅表名/别名/描述），供 Step 1 Schema Linking 使用</li>
 * </ul>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Component
public class SchemaContextBuilder {

    /**
     * 构建详细 Schema 上下文文本（含字段别名 + sample_values），供 CoT SQL 生成 Prompt 使用。
     *
     * @param relevantTables 经 Schema Linking 筛选出的相关表（含完整字段元数据）
     * @return 结构化 Schema 描述文本
     */
    public String buildSchemaContext(List<DbTableSchema> relevantTables) {
        StringBuilder sb = new StringBuilder();
        for (DbTableSchema table : relevantTables) {
            sb.append("表名: ").append(table.getTableName());
            sb.append(" (").append(table.getAlias()).append(")\n");
            sb.append("说明: ").append(table.getDescription()).append("\n");
            sb.append("字段:\n");
            for (ColumnMeta col : table.parseColumns()) {
                sb.append("  - ").append(col.getName())
                  .append(" [").append(col.getAlias()).append("] ")
                  .append(col.getType()).append(" - ")
                  .append(col.getDescription());
                if (col.getSampleValues() != null && !col.getSampleValues().isEmpty()) {
                    sb.append("，示例值: ").append(col.getSampleValues());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 按 Schema Linking 结果过滤字段，只保留 LLM 识别出的相关字段。
     * 若 linking 结果中未指定某表的字段列表，则保留该表所有字段。
     *
     * @param relevantTables 相关表（含完整字段元数据）
     * @param linkedColumns  Schema Linking 输出的各表相关字段名（可为 null 或空）
     * @return 结构化 Schema 描述文本（字段已按 linking 结果过滤）
     */
    public String buildSchemaContext(List<DbTableSchema> relevantTables,
                                     Map<String, List<String>> linkedColumns) {
        if (linkedColumns == null || linkedColumns.isEmpty()) {
            return buildSchemaContext(relevantTables);
        }
        StringBuilder sb = new StringBuilder();
        for (DbTableSchema table : relevantTables) {
            List<String> allowedCols = linkedColumns.get(table.getTableName());
            List<ColumnMeta> cols = table.parseColumns();
            if (allowedCols != null && !allowedCols.isEmpty()) {
                cols = cols.stream()
                        .filter(c -> allowedCols.contains(c.getName()))
                        .collect(Collectors.toList());
            }

            sb.append("表名: ").append(table.getTableName());
            sb.append(" (").append(table.getAlias()).append(")\n");
            sb.append("说明: ").append(table.getDescription()).append("\n");
            sb.append("字段:\n");
            for (ColumnMeta col : cols) {
                sb.append("  - ").append(col.getName())
                  .append(" [").append(col.getAlias()).append("] ")
                  .append(col.getType()).append(" - ")
                  .append(col.getDescription());
                if (col.getSampleValues() != null && !col.getSampleValues().isEmpty()) {
                    sb.append("，示例值: ").append(col.getSampleValues());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建轻量表摘要字符串（仅含表名/别名/描述），供 Schema Linking Prompt 使用。
     *
     * @param allTables 数据源下全部已启用的表
     * @return 每行格式为 "表名(别名) - 描述" 的摘要文本
     */
    public String buildTablesSummary(List<DbTableSchema> allTables) {
        return allTables.stream()
                .filter(t -> t.getEnabled() != null && t.getEnabled() == 1)
                .map(t -> t.getTableName() + "(" + t.getAlias() + ") - " + t.getDescription())
                .collect(Collectors.joining("\n"));
    }
}
