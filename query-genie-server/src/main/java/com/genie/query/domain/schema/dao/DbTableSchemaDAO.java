package com.genie.query.domain.schema.dao;

import com.genie.query.domain.schema.model.DbTableSchema;

import java.util.List;

/**
 * 表结构元数据数据访问接口。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface DbTableSchemaDAO {

    void insert(DbTableSchema schema);

    DbTableSchema findById(Long id);

    DbTableSchema findByDatasourceIdAndTableName(Long datasourceId, String tableName);

    List<DbTableSchema> listByDatasourceId(Long datasourceId);

    List<DbTableSchema> listEnabledByDatasourceId(Long datasourceId);

    void update(DbTableSchema schema);

    void updateColumnsJson(Long id, String columnsJson);

    void updateEnabled(Long id, Integer enabled);

    void deleteById(Long id);
}
