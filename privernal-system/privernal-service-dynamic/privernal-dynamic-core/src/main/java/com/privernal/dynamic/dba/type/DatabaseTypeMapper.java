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

import com.privernal.dynamic.dba.annotation.LengthCount;
import com.privernal.dynamic.dba.vo.CreateTableVo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库类型映射器接口
 * <p>
 * 定义逻辑类型名到各数据库物理类型名的转换，以及字段长度规则，
 * 由 {@link com.privernal.dynamic.dba.dialect.DatabaseDialect} 持有并在构建 SQL 时使用。
 * 支持通过 {@link #buildLengthMap(Class)} 反射构建长度映射表。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see MySQLTypeMapper
 * @see PostgreSQLTypeMapper
 * @see SQLServerTypeMapper
 */
public interface DatabaseTypeMapper {

    /** 逻辑类型：整型 */
    String INT = "int";
    /** 逻辑类型：变长字符 */
    String VARCHAR = "varchar";
    /** 逻辑类型：文本 */
    String TEXT = "text";
    /** 逻辑类型：日期时间 */
    String DATETIME = "datetime";
    /** 逻辑类型：十进制 */
    String DECIMAL = "decimal";
    /** 逻辑类型：双精度浮点 */
    String DOUBLE = "double";
    /** 逻辑类型：定长字符 */
    String CHAR = "char";
    /** 逻辑类型：长整型 */
    String BIGINT = "bigint";
    /** 逻辑类型：位/布尔 */
    String BIT = "bit";
    /** 逻辑类型：时间戳 */
    String TIMESTAMP = "timestamp";
    /** 逻辑类型：日期 */
    String DATE = "date";
    /** 逻辑类型：时间 */
    String TIME = "time";
    /** 逻辑类型：单精度浮点 */
    String FLOAT = "float";
    /** 逻辑类型：JSON */
    String JSON = "json";
    /** 逻辑类型：中等文本 */
    String MEDIUMTEXT = "mediumtext";
    /** 逻辑类型：长文本 */
    String LONGTEXT = "longtext";

    /**
     * 根据逻辑类型名获取当前数据库的物理类型名
     *
     * @param logicalType 逻辑类型（如 {@link #INT}, {@link #VARCHAR}）
     * @return 当前数据库对应的物理类型名
     */
    String getDatabaseTypeName(String logicalType);

    /**
     * 获取当前数据库类型的长度映射表
     * <p>Key 为逻辑类型名，Value 为长度参数个数（0=不需要长度，1=一个长度，2=长度+精度）</p>
     */
    Map<String, Integer> getTypeLengthMap();

    /**
     * 根据数据库方言和类型解析 {@link CreateTableVo} 中的字段类型定义
     *
     * @param vo 字段 VO
     */
    void resolveColumnType(CreateTableVo vo);

    /**
     * 获取指定逻辑类型的长度参数个数
     *
     * @param logicalType 逻辑类型名
     * @return 长度参数个数（0, 1, 2）
     */
    int getLengthCount(String logicalType);

    /**
     * 通过反射扫描常量类中带有 {@link LengthCount} 注解的字段，构建类型长度映射表
     *
     * @param typeConstantClass 常量类
     * @return 逻辑类型 -> 长度个数映射表
     */
    static Map<String, Integer> buildLengthMap(Class<?> typeConstantClass) {
        Map<String, Integer> map = new HashMap<>();
        for (Field field : typeConstantClass.getDeclaredFields()) {
            if (field.getType() == String.class) {
                LengthCount annotation = field.getAnnotation(LengthCount.class);
                if (annotation != null) {
                    try {
                        String typeName = (String) field.get(null);
                        map.put(typeName, annotation.LengthCount());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to access field: " + field.getName(), e);
                    }
                }
            }
        }
        return map;
    }
}