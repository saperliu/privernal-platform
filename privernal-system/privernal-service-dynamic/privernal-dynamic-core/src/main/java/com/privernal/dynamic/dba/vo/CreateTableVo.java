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

package com.privernal.dynamic.dba.vo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 建表字段信息 VO
 * <p>
 * 封装单个数据库列的完整定义信息，用于生成跨数据库的 CREATE TABLE / ALTER TABLE SQL。
 * 通过 {@link com.privernal.dynamic.dba.dialect.DatabaseDialect} 接口的实现类将此对象
 * 转换为特定数据库的 DDL 语法。
 * </p>
 *
 * @author saperliu
 * @version 1.1.0
 * @see com.privernal.dynamic.dba.dialect.DatabaseDialect#buildColumnDefinition(CreateTableVo)
 */
@Data
@Slf4j
public class CreateTableVo implements Cloneable {

    /**
     * 字段名（数据库列名）
     */
    private String fieldName;

    /**
     * 字段类型（如 varchar, int, decimal, timestamp 等），
     * 具体值由 {@link com.privernal.dynamic.dba.type.DatabaseTypeMapper} 映射
     */
    private String fieldType;

    /**
     * 类型长度（如 varchar(100) 中的 100）
     */
    private int fieldLength;

    /**
     * 类型小数长度（如 decimal(10,2) 中的 2）
     */
    private int fieldDecimalLength;

    /**
     * 字段是否允许为 null
     */
    private boolean fieldIsNull;

    /**
     * 字段是否为主键
     */
    private boolean fieldIsKey;

    /**
     * 主键是否自增
     */
    private boolean fieldIsAutoIncrement;

    /**
     * 字段默认值
     */
    private String fieldDefaultValue;

    /**
     * 该类型需要几个长度参数：
     * <ul>
     *   <li>0 - 不需要长度（如 int, datetime）</li>
     *   <li>1 - 一个长度参数（如 varchar(100)）</li>
     *   <li>2 - 两个长度参数（如 decimal(10,2)）</li>
     * </ul>
     * 由 {@link com.privernal.dynamic.dba.type.DatabaseTypeMapper} 自动判定
     */
    private int fieldTypeLength;

    /**
     * 值是否唯一（业务层面校验，区别于数据库唯一约束）
     */
    private boolean fieldIsUnique;

    /**
     * 索引名称（如 idx_fieldName）
     */
    private String fieldIndexName;

    /**
     * 索引字段列表
     */
    private List<String> fieldIndexValue;

    /**
     * 唯一约束名称（如 uni_fieldName）
     */
    private String fieldUniqueName;

    /**
     * 唯一约束字段列表
     */
    private List<String> fieldUniqueValue;

    /**
     * 字段备注/注释
     */
    private String fieldComment;

    /**
     * 前端组件的 ID（用于动态表单字段映射）
     */
    private String componentId;

    /**
     * 原字段名（用于字段重命名场景，表示修改前的字段名）
     */
    private String oldFileName;

    /**
     * 生成列表达式。非空表示该列是数据库维护的生成列（影子列），
     * 由 {@link com.privernal.dynamic.dba.dialect.AbstractDatabaseDialect} 拼成
     * {@code GENERATED ALWAYS AS (expr) VIRTUAL|STORED}，此时不再输出 NOT NULL / DEFAULT。
     */
    private String generatedExpression;

    /**
     * 生成列的存储形态：{@code VIRTUAL}（MySQL，可建二级索引）或 {@code STORED}（PostgreSQL，索引要求）
     */
    private String generatedStorage;

    /**
     * 影子列所来源的业务列名；业务列改名/删除时据此同步维护影子列
     */
    private String shadowOfSourceColumn;

    @Override
    public CreateTableVo clone() {
        CreateTableVo createTableVo = null;
        try {
            createTableVo = (CreateTableVo) super.clone();
        } catch (CloneNotSupportedException e) {
            log.error("克隆建表字段信息失败", e);
        }
        return createTableVo;
    }
}