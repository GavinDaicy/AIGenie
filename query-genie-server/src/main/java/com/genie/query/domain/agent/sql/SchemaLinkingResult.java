package com.genie.query.domain.agent.sql;

import com.genie.query.domain.schema.model.DbTableSchema;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema Linking 结果：LLM 从已注册表中识别出与问题相关的表和字段。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class SchemaLinkingResult {

    /** 与问题相关的表名列表 */
    private List<String> tables;

    /** 各表相关字段列表：key=表名，value=字段名列表 */
    private Map<String, List<String>> columns;

    /** 是否未识别到任何相关表 */
    public boolean isEmpty() {
        return tables == null || tables.isEmpty();
    }

    /** 返回空结果（LLM 返回空或解析失败时使用） */
    public static SchemaLinkingResult empty() {
        SchemaLinkingResult result = new SchemaLinkingResult();
        result.setTables(Collections.emptyList());
        result.setColumns(Collections.emptyMap());
        return result;
    }

    /** 兜底结果：使用全部已注册表（Schema Linking 失败时使用） */
    public static SchemaLinkingResult fallback(List<DbTableSchema> allTables) {
        SchemaLinkingResult result = new SchemaLinkingResult();
        result.setTables(allTables.stream()
                .map(DbTableSchema::getTableName)
                .collect(Collectors.toList()));
        result.setColumns(Collections.emptyMap());
        return result;
    }
}
