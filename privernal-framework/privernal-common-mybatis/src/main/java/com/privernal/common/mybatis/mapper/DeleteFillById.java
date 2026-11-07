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

package com.privernal.common.mybatis.mapper;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DeleteFillById extends AbstractMethod {

    /**
     * mapper 对应的方法名
     */
    public static final String MAPPER_METHOD = "deleteFillById";

    protected DeleteFillById(String methodName) {
        super(methodName);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        SqlMethod sqlMethod = SqlMethod.LOGIC_DELETE_BY_ID;
        String sql;
        if (tableInfo.isWithLogicDelete()) {

            List<TableFieldInfo> fieldInfos = (List)tableInfo.getFieldList().stream().filter((f)->{
                return f.getField().getAnnotation(DeleteFill.class)!=null;
            }).collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(fieldInfos)) {
                String sqlSet = "SET " + SqlScriptUtils.convertIf((String)fieldInfos.stream().map((i) -> {
                    return i.getSqlSet("");
                }).collect(Collectors.joining("")), "!@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(_parameter.getClass())", true) + tableInfo.getLogicDeleteSql(false, false);
                sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), sqlSet, tableInfo.getKeyColumn(), tableInfo.getKeyProperty(), tableInfo.getLogicDeleteSql(true, true));
            } else {
                sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), this.sqlLogicSet(tableInfo), tableInfo.getKeyColumn(), tableInfo.getKeyProperty(), tableInfo.getLogicDeleteSql(true, true));
            }

            SqlSource sqlSource = this.languageDriver.createSqlSource(this.configuration, sql, Object.class);
            return this.addUpdateMappedStatement(mapperClass, modelClass, MAPPER_METHOD, sqlSource);
        } else {
            sqlMethod = SqlMethod.DELETE_BY_ID;
            sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), tableInfo.getKeyColumn(), tableInfo.getKeyProperty());
            SqlSource sqlSource = this.languageDriver.createSqlSource(this.configuration, sql, Object.class);
            return this.addDeleteMappedStatement(mapperClass, MAPPER_METHOD, sqlSource);
        }
    }


}
