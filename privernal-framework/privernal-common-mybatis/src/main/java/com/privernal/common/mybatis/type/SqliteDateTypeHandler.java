/*
 * Copyright 2025-2026 saperliu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.privernal.common.mybatis.type;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * {@link Date} 的字符串化 TypeHandler。
 * <p>
 * sqlite-jdbc 默认把 {@code setTimestamp} 写成 epoch 毫秒、TEXT 列读回解析失败，
 * 故写端统一落 {@code yyyy-MM-dd HH:mm:ss}（与 SQLite 迁移数据、SQL 侧
 * {@code datetime('now','localtime')} 同一字典序口径）；读端宽解析。
 * MySQL 下字符串字面量写入 datetime 列同样合法，行为不变。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 */
@MappedTypes(Date.class)
public class SqliteDateTypeHandler extends BaseTypeHandler<Date> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, SqliteDateTimeCodec.format(LocalDateTime.ofInstant(parameter.toInstant(), ZoneId.systemDefault())));
    }

    @Override
    public Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return SqliteDateTimeCodec.parseDate(rs.getString(columnName));
    }

    @Override
    public Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return SqliteDateTimeCodec.parseDate(rs.getString(columnIndex));
    }

    @Override
    public Date getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return SqliteDateTimeCodec.parseDate(cs.getString(columnIndex));
    }
}
