package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.schema.dao.DbDatasourceDAO;
import com.genie.query.domain.schema.model.DbDatasource;
import com.genie.query.infrastructure.dao.mysql.mapper.DbDatasourceMapper;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 数据源 DAO 实现。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Repository
public class DbDatasourceDaoImpl implements DbDatasourceDAO {

    @Autowired
    private DbDatasourceMapper dbDatasourceMapper;

    @Override
    public void insert(DbDatasource datasource) {
        if (datasource.getId() == null) {
            datasource.setId(SnowflakeIdUtils.getNextId());
        }
        if (datasource.getStatus() == null) {
            datasource.setStatus(1);
        }
        if (datasource.getCreatedAt() == null) {
            datasource.setCreatedAt(new Date());
        }
        if (datasource.getUpdatedAt() == null) {
            datasource.setUpdatedAt(new Date());
        }
        dbDatasourceMapper.insert(datasource);
    }

    @Override
    public DbDatasource findById(Long id) {
        return dbDatasourceMapper.findById(id);
    }

    @Override
    public List<DbDatasource> listAll() {
        return dbDatasourceMapper.listAll();
    }

    @Override
    public void update(DbDatasource datasource) {
        dbDatasourceMapper.update(datasource);
    }

    @Override
    public void deleteById(Long id) {
        dbDatasourceMapper.deleteById(id);
    }
}
