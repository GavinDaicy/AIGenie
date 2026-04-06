package com.genie.query.controller;

import com.genie.query.application.SchemaApplication;
import com.genie.query.controller.dto.DatasourceRequest;
import com.genie.query.controller.dto.TableSchemaRequest;
import com.genie.query.domain.schema.model.ColumnMeta;
import com.genie.query.domain.schema.model.DbDatasource;
import com.genie.query.domain.schema.model.DbTableSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Schema 管理接口（数据源注册 + 表结构元数据）。
 *
 * @author daicy
 * @date 2026/4/2
 */
@RestController
@RequestMapping("/schema")
public class SchemaController {

    @Autowired
    private SchemaApplication schemaApplication;

    // =========================================================
    // 数据源管理
    // =========================================================

    /**
     * 获取数据源列表
     */
    @GetMapping("/datasources")
    public List<DbDatasource> listDatasources() {
        return schemaApplication.listDatasources();
    }

    /**
     * 注册新数据源
     */
    @PostMapping("/datasources")
    public DbDatasource createDatasource(@RequestBody DatasourceRequest request) {
        return schemaApplication.createDatasource(request);
    }

    /**
     * 更新数据源
     */
    @PutMapping("/datasources/{id}")
    public DbDatasource updateDatasource(@PathVariable Long id, @RequestBody DatasourceRequest request) {
        return schemaApplication.updateDatasource(id, request);
    }

    /**
     * 删除数据源
     */
    @DeleteMapping("/datasources/{id}")
    public void deleteDatasource(@PathVariable Long id) {
        schemaApplication.deleteDatasource(id);
    }

    /**
     * 测试数据源连接可用性
     */
    @PostMapping("/datasources/{id}/test")
    public Map<String, Object> testConnection(@PathVariable Long id) {
        boolean ok = schemaApplication.testConnection(id);
        return Map.of("success", ok, "message", ok ? "连接成功" : "连接失败");
    }

    // =========================================================
    // 表结构元数据管理
    // =========================================================

    /**
     * 获取表结构列表（按数据源筛选）
     */
    @GetMapping("/tables")
    public List<DbTableSchema> listTableSchemas(@RequestParam Long datasourceId) {
        return schemaApplication.listTableSchemas(datasourceId);
    }

    /**
     * 获取单张表结构详情
     */
    @GetMapping("/tables/{id}")
    public DbTableSchema getTableSchema(@PathVariable Long id) {
        return schemaApplication.getTableSchema(id);
    }

    /**
     * 注册/创建表结构元数据
     */
    @PostMapping("/tables")
    public DbTableSchema createTableSchema(@RequestBody TableSchemaRequest request) {
        return schemaApplication.createTableSchema(request);
    }

    /**
     * 更新表结构元数据（表名/别名/描述/字段/示例问答）
     */
    @PutMapping("/tables/{id}")
    public DbTableSchema updateTableSchema(@PathVariable Long id, @RequestBody TableSchemaRequest request) {
        return schemaApplication.updateTableSchema(id, request);
    }

    /**
     * 删除表结构元数据
     */
    @DeleteMapping("/tables/{id}")
    public void deleteTableSchema(@PathVariable Long id) {
        schemaApplication.deleteTableSchema(id);
    }

    /**
     * 从 information_schema 一键同步表字段（保留已有 alias/sample_values）
     */
    @PostMapping("/tables/sync")
    public DbTableSchema syncTableSchema(@RequestParam Long datasourceId,
                                          @RequestParam String tableName) {
        return schemaApplication.syncTableSchema(datasourceId, tableName);
    }

    /**
     * 单独更新字段元数据（含 sample_values）
     */
    @PutMapping("/tables/{id}/columns")
    public DbTableSchema updateColumns(@PathVariable Long id,
                                        @RequestBody List<ColumnMeta> columns) {
        return schemaApplication.updateColumns(id, columns);
    }
}
