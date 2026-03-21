package com.genie.query.infrastructure.handle.typehandle;

import com.genie.query.domain.knowledge.model.KLState;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class KLStateTypeHandler extends BaseTypeHandler<KLState> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, KLState parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.getValue()); // 假设枚举有getCode()方法返回整数值
    }

    @Override
    public KLState getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return KLState.fromValue(value); // 假设枚举有fromCode()方法
    }

    @Override
    public KLState getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return KLState.fromValue(value);
    }

    @Override
    public KLState getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return KLState.fromValue(value);
    }
}
