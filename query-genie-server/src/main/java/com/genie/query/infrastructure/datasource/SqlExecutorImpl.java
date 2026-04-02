package com.genie.query.infrastructure.datasource;

import com.genie.query.domain.agent.sql.ExplainResult;
import com.genie.query.domain.agent.sql.QueryResult;
import com.genie.query.domain.agent.sql.SqlExecutor;
import com.genie.query.domain.exception.QueryTimeoutException;
import com.genie.query.domain.schema.dao.DbDatasourceDAO;
import com.genie.query.domain.schema.model.DbDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SqlExecutor 的 JDBC 实现：通过 DynamicDataSourceManager 获取目标数据源后执行 SQL。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class SqlExecutorImpl implements SqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutorImpl.class);

    @Autowired
    private DynamicDataSourceManager dynamicDataSourceManager;

    @Autowired
    private DbDatasourceDAO dbDatasourceDAO;

    @Override
    public ExplainResult explain(String sql, Long datasourceId) {
        DataSource ds = resolveDataSource(datasourceId);
        String explainSql = "EXPLAIN " + sql;
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(explainSql);
            return ExplainResult.valid();
        } catch (SQLSyntaxErrorException e) {
            log.warn("[SqlExecutor] EXPLAIN 语法错误: {}", e.getMessage());
            return ExplainResult.invalid("SQL 语法错误: " + e.getMessage());
        } catch (Exception e) {
            log.warn("[SqlExecutor] EXPLAIN 执行异常: {}", e.getMessage());
            return ExplainResult.invalid("EXPLAIN 执行异常: " + e.getMessage());
        }
    }

    @Override
    public QueryResult execute(String sql, Long datasourceId) {
        DataSource ds = resolveDataSource(datasourceId);
        long start = System.currentTimeMillis();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(10);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                List<String> columns = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(columns.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                }

                long elapsed = System.currentTimeMillis() - start;
                log.debug("[SqlExecutor] 执行完成，行数={}, 耗时={}ms", rows.size(), elapsed);
                return QueryResult.of(columns, rows, elapsed);
            }
        } catch (SQLTimeoutException e) {
            log.warn("[SqlExecutor] 查询超时: {}", sql);
            throw new QueryTimeoutException("SQL 查询超时（超过10秒），建议缩小查询范围", e);
        } catch (Exception e) {
            log.error("[SqlExecutor] 执行异常: {}", e.getMessage());
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        }
    }

    private DataSource resolveDataSource(Long datasourceId) {
        DbDatasource config = dbDatasourceDAO.findById(datasourceId);
        if (config == null) {
            throw new IllegalArgumentException("数据源不存在: id=" + datasourceId);
        }
        return dynamicDataSourceManager.getOrCreate(config);
    }
}
