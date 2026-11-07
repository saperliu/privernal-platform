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

package com.privernal.dynamic.dbs.service;

import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.privernal.dynamic.dbs.domain.DynamicForm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-30 10:10
 * @Description 操作动态表及数据的接口
 */
public interface DynamicTableService {

    /**
     * 保存或更新表的数据
     *
     * @param tableName 表名
     * @param data      数据
     * @return 更新或是创建的数据ID
     */
    String saveOrUpdate(String database, String tableName, Map<String, Object> data);

    /**
     * 删除数据
     *
     * @param tableName 表名
     * @param id        数据的Id
     */
    public void deleteById(String database, String tableName, String id);

    /**
     * 删除数据
     *
     * @param tableName 表名
     * @param data      数据
     */
    public void deleteByIds(String database, String tableName, List<String> data);

    /**
     * 根据输入的键值数据，查询分页
     *
     * @param tableName 表名
     * @param data      过滤条件 键值对形式
     *                  {
     *                  pageNum  1
     *                  pageSize  10
     *                  name  'luce'
     *                  }
     * @return 分页查询结果
     */
    PageResultInfo findByPage(String database, String tableName, Map<String, Object> data);

    /**
     * 判断动态表物理表是否存在
     * <p>
     * 查询 information_schema，必须忽略组织数据范围（拦截器会给 information_schema 拼
     * organization_id 条件而其无此列）。供数据查询入口在物理表缺失（孤儿表单）时给出友好提示。
     *
     * @param database  数据源名称
     * @param tableName 物理表名
     * @return true 表示存在
     */
    boolean tableExists(String database, String tableName);

    /**
     * 查询单个数据
     *
     * @param id 数据的ID
     * @return 数据的所有内容
     */
    HashMap<String, Object> findById(String database, String tableName, String id);

    /**
     * 根据写的sql直接查询
     *
     * @param sql SQL语句
     * @return 键值对
     */
    List<HashMap<String, Object>> querySql(String database, String tableName, String sql);

    /**
     * 创建动态表
     *
     * @param tableName 表名
     * @param data      表的列
     */
    void createTable(String database, String tableName, Map<String, Object> data);


    /**
     * 创建动态表
     *
     * @param database    数据库
     * @param dynamicForm 动态表
     */
    void createTableByDynamicForm(String database, DynamicForm dynamicForm);

    /**
     * 删除动态表单的物理表：主表 + 子表（表单设计器 sub-table 组件生成的 主表编码_子表字段编码）。
     * 表内业务数据随之删除。DDL 会隐式提交事务，调用方应把它放在删除链路的最后一步。
     *
     * @param database    数据库
     * @param dynamicForm 动态表单（tableCode 须为原始编码，metaData 用于解析子表）
     */
    void dropTableByDynamicForm(String database, DynamicForm dynamicForm);

}
