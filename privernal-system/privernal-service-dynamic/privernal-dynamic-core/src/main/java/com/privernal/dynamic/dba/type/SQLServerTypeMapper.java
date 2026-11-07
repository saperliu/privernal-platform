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

package com.privernal.dynamic.dba.type;

import com.privernal.dynamic.dba.vo.CreateTableVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL Server 数据库类型映射器
 * <p>
 * 将逻辑类型名映射到 SQL Server 物理类型名，
 * 如 {@code varchar -> nvarchar}、{@code datetime -> datetime2}、{@code char -> nchar} 等，
 * 统一使用 Unicode 字符类型以支持多语言。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see DatabaseTypeMapper
 */
@Component
public class SQLServerTypeMapper implements DatabaseTypeMapper {

    private static final Logger log = LoggerFactory.getLogger(SQLServerTypeMapper.class);

    private final Map<String, Integer> typeLengthMap;

    /**
     * 逻辑类型 -> SQL Server 物理类型的映射表
     */
    private static final Map<String, String> SQLSERVER_TYPE_MAP = new HashMap<>();

    static {
        SQLSERVER_TYPE_MAP.put(INT, "int");
        SQLSERVER_TYPE_MAP.put(VARCHAR, "nvarchar");
        SQLSERVER_TYPE_MAP.put(TEXT, "nvarchar(max)");
        SQLSERVER_TYPE_MAP.put(MEDIUMTEXT, "nvarchar(max)");
        SQLSERVER_TYPE_MAP.put(LONGTEXT, "nvarchar(max)");
        SQLSERVER_TYPE_MAP.put(DATETIME, "datetime2");
        SQLSERVER_TYPE_MAP.put(DECIMAL, "decimal");
        SQLSERVER_TYPE_MAP.put(DOUBLE, "float");
        SQLSERVER_TYPE_MAP.put(CHAR, "nchar");
        SQLSERVER_TYPE_MAP.put(BIGINT, "bigint");
        SQLSERVER_TYPE_MAP.put(BIT, "bit");
        SQLSERVER_TYPE_MAP.put(TIMESTAMP, "datetime");
        SQLSERVER_TYPE_MAP.put(DATE, "date");
        SQLSERVER_TYPE_MAP.put(TIME, "time");
        SQLSERVER_TYPE_MAP.put(FLOAT, "real");
        SQLSERVER_TYPE_MAP.put(JSON, "nvarchar(max)");
    }

    public SQLServerTypeMapper() {
        this.typeLengthMap = buildLengthMapFromConstants();
        log.debug("SQLServerTypeMapper initialized with {} type mappings", SQLSERVER_TYPE_MAP.size());
    }

    @Override
    public String getDatabaseTypeName(String logicalType) {
        return SQLSERVER_TYPE_MAP.getOrDefault(logicalType, logicalType);
    }

    @Override
    public Map<String, Integer> getTypeLengthMap() {
        return typeLengthMap;
    }

    @Override
    public void resolveColumnType(CreateTableVo vo) {
        String logicalType = vo.getFieldType();
        String dbType = getDatabaseTypeName(logicalType);
        vo.setFieldType(dbType);

        int lengthCount = getLengthCount(logicalType);
        vo.setFieldTypeLength(lengthCount);
    }

    @Override
    public int getLengthCount(String logicalType) {
        return typeLengthMap.getOrDefault(logicalType, 1);
    }

    /**
     * 构建 SQL Server 类型的长度参数个数映射表
     */
    private Map<String, Integer> buildLengthMapFromConstants() {
        Map<String, Integer> map = new HashMap<>();
        map.put(INT, 0);
        map.put(VARCHAR, 1);
        map.put(TEXT, 0);
        map.put(MEDIUMTEXT, 0);
        map.put(LONGTEXT, 0);
        map.put(DATETIME, 0);
        map.put(DECIMAL, 2);
        map.put(DOUBLE, 0);
        map.put(CHAR, 1);
        map.put(BIGINT, 0);
        map.put(BIT, 0);
        map.put(TIMESTAMP, 0);
        map.put(DATE, 0);
        map.put(TIME, 0);
        map.put(FLOAT, 0);
        map.put(JSON, 0);
        return map;
    }
}