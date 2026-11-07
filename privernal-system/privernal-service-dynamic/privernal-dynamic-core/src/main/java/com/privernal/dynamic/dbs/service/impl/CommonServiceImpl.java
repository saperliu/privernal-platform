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

package com.privernal.dynamic.dbs.service.impl;

import com.privernal.dynamic.dba.constants.Constants;
import com.privernal.dynamic.dbs.domain.DynamicDatabase;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.groovy.GroovyScriptService;
import com.privernal.dynamic.dbs.mapper.DynamicDatabaseMapper;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import com.privernal.dynamic.dbs.service.CommonService;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.dynamic.dbs.support.FailClosedConfig;
import com.privernal.common.datascope.aspect.DataScopeHelper;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.privernal.dynamic.constants.DynamicConstants.BACK_SCRIPT_TRANSACTIONAL;
import static com.privernal.dynamic.constants.DynamicConstants.FIELD_START_FLOW;
import static com.privernal.dynamic.constants.DynamicConstants.FIELD_TABLE_CODE;


/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-27 11:01
 * @Description 公共服务，表单的创建及查询
 */
@Service
public class CommonServiceImpl implements CommonService {

    private static final Logger logger = LoggerFactory.getLogger(CommonServiceImpl.class);

    /**
     * findTableList 允许的排序字段白名单，防止排序字段拼接注入
     */
    private static final Set<String> SORT_COLUMNS = Set.of(
            "id", "table_code", "table_name", "table_comment", "create_date", "create_user");

    @Autowired
    DynamicDatabaseMapper dynamicDatabaseMapper;

    @Autowired
    DynamicFormMapper dynamicFormMapper;


    @Autowired
    GroovyScriptService groovyScriptService;

    @Autowired
    FailClosedConfig failClosedConfig;


    @Autowired
    DynamicTableService dynamicTableService;

    @DS("#database")
    @Override
    public boolean saveForm(String database, DynamicForm dynamicForm) {
        if (StringUtils.hasLength(dynamicForm.getId())) {
            dynamicFormMapper.updateById(dynamicForm);
        } else {
            dynamicFormMapper.insert(dynamicForm);
        }
        return true;
    }


    @Override
    public boolean saveDatabase(DynamicDatabase dynamicDatabase) {
        int i = dynamicDatabaseMapper.insert(dynamicDatabase);
        if (i == 1) {
            return true;
        }
        return false;
    }

    @Override
    public DynamicDatabase findDatabaseByOrg(String orgId, String siteId) {
        QueryWrapper<DynamicDatabase> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("org_id", orgId).eq("site_id", siteId);

        return dynamicDatabaseMapper.selectOne(queryWrapper);
    }

    @DS("#database")
    @Override
    public IPage<DynamicForm> findTableList(String database, DynamicFormFilterDTO dynamicFormFilter) {

        QueryWrapper<DynamicForm> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasLength(dynamicFormFilter.getTableCode())) {
            queryWrapper.eq("table_code", dynamicFormFilter.getTableCode());
        }
        if (StringUtils.hasLength(dynamicFormFilter.getCreateUser())) {
            queryWrapper.eq("create_user", dynamicFormFilter.getCreateUser());
        }

        String[] keywords = dynamicFormFilter.getKeywords();
        if (keywords != null && keywords.length > 0) {
            // 用 and 包裹，避免 or 条件破坏与其它 eq 条件的组合逻辑
            queryWrapper.and(w -> {
                for (int i = 0; i < keywords.length; i++) {
                    if (i > 0) {
                        w.or();
                    }
                    w.like("table_name", keywords[i]).or().like("table_comment", keywords[i]);
                }
            });
        }
        applySort(queryWrapper, dynamicFormFilter.getSort());

        long pageNum = dynamicFormFilter.getPageNum() != null && dynamicFormFilter.getPageNum() > 0
                ? dynamicFormFilter.getPageNum() : Constants.DEFAULT_PAGE_NUM;
        long pageSize = dynamicFormFilter.getPageSize() != null && dynamicFormFilter.getPageSize() > 0
                ? dynamicFormFilter.getPageSize() : Constants.DEFAULT_PAGE_SIZE;
        // 新-6：pageSize 上限，防止一次请求全表拉取
        pageSize = Math.min(pageSize, 500);
        Page<DynamicForm> page = new Page<>(pageNum, pageSize);

        queryWrapper.select("id", "table_code", "table_name", "table_comment", "create_date", "create_user", "form_json");
        return dynamicFormMapper.selectPage(page, queryWrapper);

    }

    /**
     * 应用排序条件，格式为 +列名 或 -列名，列名必须在白名单内
     */
    private void applySort(QueryWrapper<DynamicForm> queryWrapper, String sort) {
        if (!StringUtils.hasLength(sort) || sort.length() < 2) {
            return;
        }
        String column = sort.substring(1);
        if (!SORT_COLUMNS.contains(column)) {
            logger.warn("findTableList 忽略非法排序字段：{}", column);
            return;
        }
        if (sort.startsWith("+")) {
            queryWrapper.orderByAsc(column);
        } else {
            queryWrapper.orderByDesc(column);
        }
    }

    @DS("#database")
    @Override
    public DynamicForm findTableByCode(String database, String tableCode) {
        QueryWrapper<DynamicForm> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("table_code", tableCode);
        return dynamicFormMapper.selectOne(queryWrapper);
    }

    @DS("#database")
    @Override
    public DynamicForm findTableById(String database, String id) {
        return dynamicFormMapper.selectById(id);
    }



    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public Object executeScript(String database, String tableName, String script, Map<String, Object> data) throws Exception {

        runBackScript(script, data);
        removeProtocolFields(data);
        String id = dynamicTableService.saveOrUpdate(database, tableName, data);
        data.put("id", id);
        return data;
    }

    @Override
    public Object saveTableData(String database, String tableName, String backScript, Map<String, Object> data, boolean transactional) throws Exception {
        if (transactional) {
            return executeScript(database, tableName, backScript, data);
        }
        runBackScript(backScript, data);
        removeProtocolFields(data);
        String id = dynamicTableService.saveOrUpdate(database, tableName, data);
        data.put("id", id);
        return data;
    }

    /**
     * 表单后台脚本统一入口（沙箱接线）：
     * 防护类开关 {@code dynamic.script.sandbox.enforced} fail-closed——缺行/读失败 = 强制沙箱，
     * 灰度豁免靠显式写入种子行（全局或组织级）。豁免不是永久态，交付说明已写明存量脚本回归通过后要收回。
     */
    private void runBackScript(String script, Map<String, Object> data) throws Exception {
        if (script == null || script.isEmpty()) {
            return;
        }
        // 表单后台脚本属于平台配置的自动化逻辑（与联动引擎同域）：执行期忽略组织与数据权限改写。
        // 否则脚本内跨表统计会被数据范围切面改写成 WHERE 1=4（参数错位时直接 SQL 异常），
        // 导致普通用户提交数据即 500。执行完恢复，落库写入仍按提交人会话。
        DataScopeHelper.mergeIgnoreStrategy(true, true);
        try {
            if (failClosedConfig.sandboxEnforced(com.privernal.common.datascope.context.OrganizationContextHolder.getOrganizationId())) {
                groovyScriptService.executeSandboxed(script, data, 0);
            } else {
                groovyScriptService.executeScript(script, data);
            }
        } finally {
            DataScopeHelper.clearIgnoreStrategy();
        }
    }

    /**
     * 剔除提交数据中的协议字段（非动态表业务列）
     */
    private void removeProtocolFields(Map<String, Object> data) {
        data.remove(FIELD_TABLE_CODE);
        data.remove(FIELD_START_FLOW);
        data.remove(BACK_SCRIPT_TRANSACTIONAL);
    }

    @Override
    public String updateTableData(String database, String tableName, Map<String, Object> data) {
        if (data == null || StringUtils.isEmpty((String) data.get("id"))) {
            return null;
        }
        return dynamicTableService.saveOrUpdate(database, tableName, data);
    }
}