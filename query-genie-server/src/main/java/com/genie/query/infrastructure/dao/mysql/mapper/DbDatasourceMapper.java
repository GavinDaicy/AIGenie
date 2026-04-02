package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.schema.model.DbDatasource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据源表 Mapper。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Mapper
public interface DbDatasourceMapper {

    void insert(DbDatasource datasource);

    DbDatasource findById(@Param("id") Long id);

    List<DbDatasource> listAll();

    void update(DbDatasource datasource);

    void deleteById(@Param("id") Long id);
}
