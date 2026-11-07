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

import com.privernal.dynamic.dbs.domain.DynamicDatabase;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-27 11:01
 * @Description 基础表的操作
 */
public interface CommonService {


    /**
     * 保存新增的数据库配置
     *
     * @param dynamicDatabase 配置的内容
     * @return 是否成功
     */
    public boolean saveDatabase(DynamicDatabase dynamicDatabase);

    /**
     * 查询组织和站点的数据库
     *
     * @param orgId  组织ID
     * @param siteId 站点ID
     * @return 查询到的数据库
     */
    public DynamicDatabase findDatabaseByOrg(String orgId, String siteId);

    /**
     * 保存新建立的表单
     *
     * @param database    数据库
     * @param dynamicForm 表单数据
     * @return 是否成功
     */
    public boolean saveForm(String database, DynamicForm dynamicForm);

    /**
     * 查询创建的表单
     *
     * @param database          数据库
     * @param dynamicFormFilter 查询条件
     * @return 分页表单
     */

    public IPage<DynamicForm> findTableList(String database, DynamicFormFilterDTO dynamicFormFilter);

    /**
     * 查询创建的表单通过编码
     *
     * @param database  数据库
     * @param tableCode 查询条件
     * @return 分页表单
     */

    public DynamicForm findTableByCode(String database, String tableCode);

    /**
     * 查询创建的表单通过ID
     *
     * @param database 数据库
     * @param id       查询条件
     * @return 分页表单
     */

    public DynamicForm findTableById(String database, String id);

    /**
     * 执行脚本
     *
     * @param database 数据库
     * @param tableName 表名
     * @param data      表的列
     */
    Object executeScript(String database, String tableName, String script, Map<String, Object> data) throws Exception;

    /**
     * 动态表数据提交入口：先执行表单配置的后台脚本，再落库
     * <p>
     * transactional 为 true 时脚本执行与数据落库在同一事务内（任一失败整体回滚）；
     * 为 false 时脚本先行执行（自身原子），随后独立落库。
     * </p>
     *
     * @param database      数据源名称
     * @param tableName     动态表名
     * @param backScript    表单配置的后台脚本（可为空）
     * @param data          提交的数据（含协议字段 tableCode，落库前剔除）
     * @param transactional 是否与落库同事务
     * @return 落库后的数据（含主键 id）
     */
    Object saveTableData(String database, String tableName, String backScript, Map<String, Object> data, boolean transactional) throws Exception;

    /**
     * 更新动态表数据（按 id 局部更新传入的列）
     * 供工作流模块回写业务数据状态使用
     *
     * @param database  数据源名称
     * @param tableName 动态表名
     * @param data      更新的列（必须含 id）
     * @return 数据id
     */
    String updateTableData(String database, String tableName, Map<String, Object> data);
}
