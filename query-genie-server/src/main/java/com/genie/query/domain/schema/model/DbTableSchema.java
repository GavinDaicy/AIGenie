package com.genie.query.domain.schema.model;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.Data;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 数据库表结构元数据实体。
 * columns_json 和 sample_queries_json 以 JSON 字符串存储于 MySQL，
 * 通过 getColumns() / getSampleQueries() 提供反序列化访问。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class DbTableSchema {
    private Long id;
    private Long datasourceId;
    /** 实际表名 */
    private String tableName;
    /** 业务别名，如"钢筋价格表" */
    private String alias;
    /** 表的业务说明 */
    private String description;
    /** 字段元数据 JSON 字符串，存储 List<ColumnMeta>，对应 DB 列 columns_json */
    private String columnsJson;
    /** 示例问答对 JSON 字符串，存储 List<SampleQuery>，对应 DB 列 sample_queries */
    private String sampleQueries;
    /** 是否启用: 1=启用 0=禁用 */
    private Integer enabled;
    private Date createdAt;
    private Date updatedAt;

    /** 反序列化获取字段元数据列表 */
    public List<ColumnMeta> parseColumns() {
        if (columnsJson == null || columnsJson.isBlank()) {
            return Collections.emptyList();
        }
        return JSON.parseObject(columnsJson, new TypeReference<List<ColumnMeta>>() {});
    }

    /** 反序列化获取示例问答对列表 */
    public List<SampleQuery> parseSampleQueries() {
        if (sampleQueries == null || sampleQueries.isBlank()) {
            return Collections.emptyList();
        }
        return JSON.parseObject(sampleQueries, new TypeReference<List<SampleQuery>>() {});
    }

    /** 将字段列表序列化后设置 */
    public void setColumns(List<ColumnMeta> columns) {
        this.columnsJson = columns == null ? "[]" : JSON.toJSONString(columns);
    }

    /** 将示例问答对列表序列化后设置 */
    public void setSampleQueriesList(List<SampleQuery> queries) {
        this.sampleQueries = queries == null ? "[]" : JSON.toJSONString(queries);
    }
}
