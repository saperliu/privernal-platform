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

package com.privernal.dynamic.dbs.groovy.env;

import com.privernal.dynamic.dba.vo.PageResultInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface DaoService {

    /**
     * 保存或更新表的数据
     *
     * @param tableName 表名
     * @param data      数据
     * @return 更新或是创建的数据ID
     */
    String saveOrUpdate(String tableName, Map<String, Object> data);

    /**
     * 删除数据
     *
     * @param tableName 表名
     * @param id        数据的Id
     */
    public void deleteById(String tableName, String id);

    /**
     * 删除数据
     *
     * @param tableName 表名
     * @param data      数据
     */
    public void deleteByIds( String tableName, List<String> data);

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
    PageResultInfo findByPage(String tableName, Map<String, Object> data);

    /**
     * 查询单个数据
     *
     * @param id 数据的ID
     * @return 数据的所有内容
     */
    HashMap<String, Object> findById(String tableName, String id);

    /**
     * 根据写的sql直接查询
     *
     * @param sql SQL语句
     * @return 键值对
     */
    List<HashMap<String, Object>> querySql( String tableName, String sql);

}
