package com.genie.query.controller.dto;

import com.genie.query.domain.schema.model.ColumnMeta;
import com.genie.query.domain.schema.model.SampleQuery;
import lombok.Data;

import java.util.List;

/**
 * 表结构元数据新增/更新请求。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class TableSchemaRequest {
    private Long datasourceId;
    private String tableName;
    private String alias;
    private String description;
    private List<ColumnMeta> columns;
    private List<SampleQuery> sampleQueries;
    /** 是否启用: 1=启用 0=禁用 */
    private Integer enabled;
}
