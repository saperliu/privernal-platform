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
 * SQLite 数据库类型映射器
 * <p>
 * 逻辑类型 → SQLite 类型名（SQLite 只认 NULL/INTEGER/REAL/TEXT/BLOB 五种存储类，
 * 声明类型仅决定类型亲和性）。长度/精度参数照常声明（SQLite 容忍并忽略），
 * 便于 DDL 与表单定义对齐。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see DatabaseTypeMapper
 */
@Component
public class SQLiteTypeMapper implements DatabaseTypeMapper {

    private static final Logger log = LoggerFactory.getLogger(SQLiteTypeMapper.class);

    private final Map<String, Integer> typeLengthMap;

    /**
     * 逻辑类型 -> SQLite 物理类型的映射表
     */
    private static final Map<String, String> SQLITE_TYPE_MAP = new HashMap<>();

    static {
        SQLITE_TYPE_MAP.put(INT, "INTEGER");
        SQLITE_TYPE_MAP.put(VARCHAR, "TEXT");
        SQLITE_TYPE_MAP.put(TEXT, "TEXT");
        SQLITE_TYPE_MAP.put(MEDIUMTEXT, "TEXT");
        SQLITE_TYPE_MAP.put(LONGTEXT, "TEXT");
        SQLITE_TYPE_MAP.put(DATETIME, "TEXT");
        SQLITE_TYPE_MAP.put(DECIMAL, "NUMERIC");
        SQLITE_TYPE_MAP.put(DOUBLE, "REAL");
        SQLITE_TYPE_MAP.put(CHAR, "TEXT");
        SQLITE_TYPE_MAP.put(BIGINT, "INTEGER");
        SQLITE_TYPE_MAP.put(BIT, "INTEGER");
        SQLITE_TYPE_MAP.put(TIMESTAMP, "TEXT");
        SQLITE_TYPE_MAP.put(DATE, "TEXT");
        SQLITE_TYPE_MAP.put(TIME, "TEXT");
        SQLITE_TYPE_MAP.put(FLOAT, "REAL");
        SQLITE_TYPE_MAP.put(JSON, "TEXT");
    }

    public SQLiteTypeMapper() {
        this.typeLengthMap = buildLengthMapFromConstants();
        log.debug("SQLiteTypeMapper initialized with {} type mappings", SQLITE_TYPE_MAP.size());
    }

    @Override
    public String getDatabaseTypeName(String logicalType) {
        return SQLITE_TYPE_MAP.getOrDefault(logicalType, logicalType);
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
     * 构建 SQLite 类型的长度参数个数映射表（与逻辑类型定义保持一致，SQLite 忽略长度）
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
