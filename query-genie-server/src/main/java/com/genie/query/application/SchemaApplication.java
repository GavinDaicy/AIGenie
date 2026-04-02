package com.genie.query.application;

import com.alibaba.fastjson2.JSON;
import com.genie.query.controller.dto.DatasourceRequest;
import com.genie.query.controller.dto.TableSchemaRequest;
import com.genie.query.domain.schema.dao.DbDatasourceDAO;
import com.genie.query.domain.schema.dao.DbTableSchemaDAO;
import com.genie.query.domain.schema.model.ColumnMeta;
import com.genie.query.domain.schema.model.DbDatasource;
import com.genie.query.domain.schema.model.DbTableSchema;
import com.genie.query.infrastructure.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Schema 管理应用服务。
 * 负责数据源注册、表结构元数据管理与从 information_schema 自动同步。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class SchemaApplication {

    @Autowired
    private DbDatasourceDAO dbDatasourceDAO;

    @Autowired
    private DbTableSchemaDAO dbTableSchemaDAO;

    // =========================================================
    // 数据源管理
    // =========================================================

    public List<DbDatasource> listDatasources() {
        List<DbDatasource> list = dbDatasourceDAO.listAll();
        list.forEach(ds -> ds.setDbPassword("******"));
        return list;
    }

    public DbDatasource createDatasource(DatasourceRequest req) {
        validateDatasourceRequest(req);
        DbDatasource datasource = new DbDatasource();
        datasource.setName(req.getName());
        datasource.setDescription(req.getDescription());
        datasource.setDbUrl(req.getDbUrl());
        datasource.setDbUsername(req.getDbUsername());
        datasource.setDbPassword(req.getDbPassword());
        datasource.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        dbDatasourceDAO.insert(datasource);
        datasource.setDbPassword("******");
        return datasource;
    }

    public DbDatasource updateDatasource(Long id, DatasourceRequest req) {
        DbDatasource existing = dbDatasourceDAO.findById(id);
        if (existing == null) {
            throw new BusinessException("数据源不存在: " + id);
        }
        validateDatasourceRequest(req);
        existing.setName(req.getName());
        existing.setDescription(req.getDescription());
        existing.setDbUrl(req.getDbUrl());
        existing.setDbUsername(req.getDbUsername());
        if (StringUtils.isNotBlank(req.getDbPassword()) && !"******".equals(req.getDbPassword())) {
            existing.setDbPassword(req.getDbPassword());
        }
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        dbDatasourceDAO.update(existing);
        existing.setDbPassword("******");
        return existing;
    }

    public void deleteDatasource(Long id) {
        dbDatasourceDAO.deleteById(id);
    }

    public boolean testConnection(Long id) {
        DbDatasource datasource = dbDatasourceDAO.findById(id);
        if (datasource == null) {
            throw new BusinessException("数据源不存在: " + id);
        }
        return doTestConnection(datasource.getDbUrl(), datasource.getDbUsername(), datasource.getDbPassword());
    }

    // =========================================================
    // 表结构元数据管理
    // =========================================================

    public List<DbTableSchema> listTableSchemas(Long datasourceId) {
        if (datasourceId != null) {
            return dbTableSchemaDAO.listByDatasourceId(datasourceId);
        }
        throw new BusinessException("datasourceId 不能为空");
    }

    public DbTableSchema getTableSchema(Long id) {
        DbTableSchema schema = dbTableSchemaDAO.findById(id);
        if (schema == null) {
            throw new BusinessException("表结构不存在: " + id);
        }
        return schema;
    }

    public DbTableSchema createTableSchema(TableSchemaRequest req) {
        validateTableSchemaRequest(req);
        DbTableSchema schema = new DbTableSchema();
        schema.setDatasourceId(req.getDatasourceId());
        schema.setTableName(req.getTableName());
        schema.setAlias(req.getAlias());
        schema.setDescription(req.getDescription());
        schema.setColumns(req.getColumns() != null ? req.getColumns() : List.of());
        schema.setSampleQueriesList(req.getSampleQueries() != null ? req.getSampleQueries() : List.of());
        schema.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        dbTableSchemaDAO.insert(schema);
        return schema;
    }

    public DbTableSchema updateTableSchema(Long id, TableSchemaRequest req) {
        DbTableSchema existing = dbTableSchemaDAO.findById(id);
        if (existing == null) {
            throw new BusinessException("表结构不存在: " + id);
        }
        existing.setAlias(req.getAlias());
        existing.setDescription(req.getDescription());
        if (req.getColumns() != null) {
            existing.setColumns(req.getColumns());
        }
        if (req.getSampleQueries() != null) {
            existing.setSampleQueriesList(req.getSampleQueries());
        }
        if (req.getEnabled() != null) {
            existing.setEnabled(req.getEnabled());
        }
        existing.setUpdatedAt(new Date());
        dbTableSchemaDAO.update(existing);
        return existing;
    }

    public void deleteTableSchema(Long id) {
        dbTableSchemaDAO.deleteById(id);
    }

    /**
     * 更新表的字段元数据（含 sample_values），保留其他信息不变。
     */
    public DbTableSchema updateColumns(Long id, List<ColumnMeta> columns) {
        DbTableSchema existing = dbTableSchemaDAO.findById(id);
        if (existing == null) {
            throw new BusinessException("表结构不存在: " + id);
        }
        String columnsJson = JSON.toJSONString(columns);
        dbTableSchemaDAO.updateColumnsJson(id, columnsJson);
        existing.setColumnsJson(columnsJson);
        return existing;
    }

    /**
     * 从目标数据源的 information_schema 自动同步表字段。
     * 已有人工配置的 alias/sample_values 将被保留，仅补充缺失字段。
     */
    public DbTableSchema syncTableSchema(Long datasourceId, String tableName) {
        DbDatasource datasource = dbDatasourceDAO.findById(datasourceId);
        if (datasource == null) {
            throw new BusinessException("数据源不存在: " + datasourceId);
        }

        List<ColumnMeta> syncedColumns = fetchColumnsFromInfoSchema(
                datasource.getDbUrl(), datasource.getDbUsername(), datasource.getDbPassword(), tableName);

        DbTableSchema existing = dbTableSchemaDAO.findByDatasourceIdAndTableName(datasourceId, tableName);

        if (existing == null) {
            DbTableSchema newSchema = new DbTableSchema();
            newSchema.setDatasourceId(datasourceId);
            newSchema.setTableName(tableName);
            newSchema.setColumns(syncedColumns);
            dbTableSchemaDAO.insert(newSchema);
            return newSchema;
        }

        List<ColumnMeta> mergedColumns = mergeColumns(existing.parseColumns(), syncedColumns);
        String columnsJson = JSON.toJSONString(mergedColumns);
        dbTableSchemaDAO.updateColumnsJson(existing.getId(), columnsJson);
        existing.setColumnsJson(columnsJson);
        return existing;
    }

    // =========================================================
    // 内部工具方法
    // =========================================================

    private boolean doTestConnection(String url, String username, String password) {
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过 JDBC 从 information_schema.columns 读取指定表的字段列表。
     */
    private List<ColumnMeta> fetchColumnsFromInfoSchema(String url, String username, String password, String tableName) {
        String dbName = extractDatabaseName(url);
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";

        List<ColumnMeta> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnMeta col = new ColumnMeta();
                    col.setName(rs.getString("COLUMN_NAME"));
                    col.setType(rs.getString("DATA_TYPE"));
                    col.setDescription(rs.getString("COLUMN_COMMENT"));
                    columns.add(col);
                }
            }
        } catch (Exception e) {
            throw new BusinessException("同步表结构失败: " + e.getMessage());
        }
        return columns;
    }

    /**
     * 合并字段列表：以同步结果为基础，保留已有字段的 alias 和 sampleValues。
     */
    private List<ColumnMeta> mergeColumns(List<ColumnMeta> existing, List<ColumnMeta> synced) {
        for (ColumnMeta syncedCol : synced) {
            existing.stream()
                    .filter(e -> e.getName().equals(syncedCol.getName()))
                    .findFirst()
                    .ifPresent(existingCol -> {
                        syncedCol.setAlias(existingCol.getAlias());
                        syncedCol.setSampleValues(existingCol.getSampleValues());
                    });
        }
        return synced;
    }

    /**
     * 从 JDBC URL 中提取数据库名称（取最后一个 / 后、? 前的部分）。
     */
    private String extractDatabaseName(String url) {
        try {
            String path = url.split("\\?")[0];
            String[] parts = path.split("/");
            return parts[parts.length - 1];
        } catch (Exception e) {
            throw new BusinessException("无法从 JDBC URL 解析数据库名称: " + url);
        }
    }

    private void validateDatasourceRequest(DatasourceRequest req) {
        if (StringUtils.isBlank(req.getName())) {
            throw new BusinessException("数据源名称不能为空");
        }
        if (StringUtils.isBlank(req.getDbUrl())) {
            throw new BusinessException("JDBC URL 不能为空");
        }
        if (StringUtils.isBlank(req.getDbUsername())) {
            throw new BusinessException("数据库用户名不能为空");
        }
    }

    private void validateTableSchemaRequest(TableSchemaRequest req) {
        if (req.getDatasourceId() == null) {
            throw new BusinessException("datasourceId 不能为空");
        }
        if (StringUtils.isBlank(req.getTableName())) {
            throw new BusinessException("表名不能为空");
        }
    }
}
