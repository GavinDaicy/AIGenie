package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.schema.model.DbTableSchema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 表结构元数据 Mapper。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Mapper
public interface DbTableSchemaMapper {

    void insert(DbTableSchema schema);

    DbTableSchema findById(@Param("id") Long id);

    DbTableSchema findByDatasourceIdAndTableName(@Param("datasourceId") Long datasourceId,
                                                  @Param("tableName") String tableName);

    List<DbTableSchema> listByDatasourceId(@Param("datasourceId") Long datasourceId);

    List<DbTableSchema> listEnabledByDatasourceId(@Param("datasourceId") Long datasourceId);

    void update(DbTableSchema schema);

    void updateColumnsJson(@Param("id") Long id, @Param("columnsJson") String columnsJson);

    void updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    void deleteById(@Param("id") Long id);
}
