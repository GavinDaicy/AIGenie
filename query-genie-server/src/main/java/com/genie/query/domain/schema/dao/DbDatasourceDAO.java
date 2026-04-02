package com.genie.query.domain.schema.dao;

import com.genie.query.domain.schema.model.DbDatasource;

import java.util.List;

/**
 * 数据源数据访问接口。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface DbDatasourceDAO {

    void insert(DbDatasource datasource);

    DbDatasource findById(Long id);

    List<DbDatasource> listAll();

    void update(DbDatasource datasource);

    void deleteById(Long id);
}
