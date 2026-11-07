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
 * MySQL 数据库类型映射器
 * <p>
 * 将逻辑类型名（如 {@code varchar}, {@code int}）映射到 MySQL 物理类型名，
 * 同时维护类型的长度参数规则（0 / 1 / 2 个参数）。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see DatabaseTypeMapper
 */
@Component
public class MySQLTypeMapper implements DatabaseTypeMapper {

    private static final Logger log = LoggerFactory.getLogger(MySQLTypeMapper.class);

    private final Map<String, Integer> typeLengthMap;

    /**
     * 逻辑类型 -> MySQL 物理类型的映射表
     */
    private static final Map<String, String> MYSQL_TYPE_MAP = new HashMap<>();

    static {
        MYSQL_TYPE_MAP.put(INT, "int");
        MYSQL_TYPE_MAP.put(VARCHAR, "varchar");
        MYSQL_TYPE_MAP.put(TEXT, "text");
        MYSQL_TYPE_MAP.put(MEDIUMTEXT, "mediumtext");
        MYSQL_TYPE_MAP.put(LONGTEXT, "longtext");
        MYSQL_TYPE_MAP.put(DATETIME, "datetime");
        MYSQL_TYPE_MAP.put(DECIMAL, "decimal");
        MYSQL_TYPE_MAP.put(DOUBLE, "double");
        MYSQL_TYPE_MAP.put(CHAR, "char");
        MYSQL_TYPE_MAP.put(BIGINT, "bigint");
        MYSQL_TYPE_MAP.put(BIT, "bit");
        MYSQL_TYPE_MAP.put(TIMESTAMP, "timestamp");
        MYSQL_TYPE_MAP.put(DATE, "date");
        MYSQL_TYPE_MAP.put(TIME, "time");
        MYSQL_TYPE_MAP.put(FLOAT, "float");
        MYSQL_TYPE_MAP.put(JSON, "json");
    }

    public MySQLTypeMapper() {
        this.typeLengthMap = buildLengthMapFromConstants();
        log.debug("MySQLTypeMapper initialized with {} type mappings", MYSQL_TYPE_MAP.size());
    }

    @Override
    public String getDatabaseTypeName(String logicalType) {
        return MYSQL_TYPE_MAP.getOrDefault(logicalType, logicalType);
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
     * 构建 MySQL 类型的长度参数个数映射表
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
        map.put(DOUBLE, 2);
        map.put(CHAR, 1);
        map.put(BIGINT, 1);
        map.put(BIT, 1);
        map.put(TIMESTAMP, 0);
        map.put(DATE, 0);
        map.put(TIME, 0);
        map.put(FLOAT, 0);
        map.put(JSON, 0);
        return map;
    }
}