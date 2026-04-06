package com.genie.query.domain.schema.model;

import lombok.Data;

import java.util.List;

/**
 * 数据库字段元数据（对应 db_table_schema.columns_json 中每个元素）。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class ColumnMeta {
    /** 实际字段名 */
    private String name;
    /** 业务别名 */
    private String alias;
    /** 字段类型，如 varchar/bigint/datetime */
    private String type;
    /** 字段业务描述 */
    private String description;
    /** 样本值列表，帮助 LLM 理解取值范围，如 ["HRB400", "HRB500"] */
    private List<String> sampleValues;
}
