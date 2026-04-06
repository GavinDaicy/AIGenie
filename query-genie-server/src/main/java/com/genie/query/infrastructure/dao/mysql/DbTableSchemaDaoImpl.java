package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.schema.dao.DbTableSchemaDAO;
import com.genie.query.domain.schema.model.DbTableSchema;
import com.genie.query.infrastructure.dao.mysql.mapper.DbTableSchemaMapper;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 表结构元数据 DAO 实现。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Repository
public class DbTableSchemaDaoImpl implements DbTableSchemaDAO {

    @Autowired
    private DbTableSchemaMapper dbTableSchemaMapper;

    @Override
    public void insert(DbTableSchema schema) {
        if (schema.getId() == null) {
            schema.setId(SnowflakeIdUtils.getNextId());
        }
        if (schema.getEnabled() == null) {
            schema.setEnabled(1);
        }
        if (schema.getColumnsJson() == null) {
            schema.setColumnsJson("[]");
        }
        if (schema.getCreatedAt() == null) {
            schema.setCreatedAt(new Date());
        }
        if (schema.getUpdatedAt() == null) {
            schema.setUpdatedAt(new Date());
        }
        dbTableSchemaMapper.insert(schema);
    }

    @Override
    public DbTableSchema findById(Long id) {
        return dbTableSchemaMapper.findById(id);
    }

    @Override
    public DbTableSchema findByDatasourceIdAndTableName(Long datasourceId, String tableName) {
        return dbTableSchemaMapper.findByDatasourceIdAndTableName(datasourceId, tableName);
    }

    @Override
    public List<DbTableSchema> listByDatasourceId(Long datasourceId) {
        return dbTableSchemaMapper.listByDatasourceId(datasourceId);
    }

    @Override
    public List<DbTableSchema> listEnabledByDatasourceId(Long datasourceId) {
        return dbTableSchemaMapper.listEnabledByDatasourceId(datasourceId);
    }

    @Override
    public void update(DbTableSchema schema) {
        dbTableSchemaMapper.update(schema);
    }

    @Override
    public void updateColumnsJson(Long id, String columnsJson) {
        dbTableSchemaMapper.updateColumnsJson(id, columnsJson);
    }

    @Override
    public void updateEnabled(Long id, Integer enabled) {
        dbTableSchemaMapper.updateEnabled(id, enabled);
    }

    @Override
    public void deleteById(Long id) {
        dbTableSchemaMapper.deleteById(id);
    }
}
