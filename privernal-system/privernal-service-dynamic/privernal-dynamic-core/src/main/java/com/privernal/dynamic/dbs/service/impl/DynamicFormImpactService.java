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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.mapper.DynamicFormColumnMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.privernal.dynamic.dbs.service.IDynamicFormService;

/**
 * 影响面预评估：编辑/发布表单前给出人工确认关卡的数据源。
 * 输出口径：{影响在办实例数, 是否含 DDL, 是否含删除动作, 将注册哪些权限码, 需人工勾选的角色}。
 * 在办实例数按平台状态语义取业务表 status=2（流程运行中）行数（workflow 表单）；
 * DDL/删除动作按"已登记列 vs 提议 metaData"的列集合差异判定。
 */
@Slf4j
@Service
public class DynamicFormImpactService {

    @Autowired
    private DynamicFormColumnMapper formColumnMapper;

    @Autowired
    private IDynamicFormService dynamicFormService;

    @Autowired
    private com.privernal.dynamic.dbs.service.DynamicTableService dynamicTableService;

    public Map<String, Object> impact(String formId, com.alibaba.fastjson2.JSONObject proposedMetaData) {
        DynamicForm form = dynamicFormService.selectDynamicFormById(formId);
        if (form == null) {
            throw new ServiceException("表单不存在：" + formId);
        }
        String tableCode = form.getTableCode();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("formId", formId);
        out.put("tableCode", form.getTableCode());
        out.put("tableName", form.getTableName());

        // 影响在办实例数：workflow 表单按业务表 status=2（流程运行中）行数；非流程表单恒 0
        long running = 0;
        if (form.getIsWorkflow() != null && form.getIsWorkflow() == 1) {
            try {
                List<HashMap<String, Object>> rows = dynamicTableService.querySql(
                        com.privernal.common.security.service.SecurityUtils.getDatabaseName(),
                        form.getTableCode(),
                        "SELECT COUNT(*) AS cnt FROM " + form.getTableCode()
                                + " WHERE deleted_flag = 'N' AND status = 2");
                if (!rows.isEmpty() && rows.get(0).get("cnt") != null) {
                    running = Long.parseLong(String.valueOf(rows.get(0).get("cnt")));
                }
            } catch (Exception e) {
                log.warn("在办实例统计失败 form={} error={}", formId, e.getMessage());
                out.put("runningInstancesError", "统计失败（业务表可能尚未创建）");
            }
        }
        out.put("runningInstances", running);
        out.put("isWorkflowForm", form.getIsWorkflow() != null && form.getIsWorkflow() == 1);

        // 列差异：已登记列 vs 提议 metaData 的字段集合
        Set<String> registered = new HashSet<>();
        for (DynamicFormColumn col : formColumnMapper.selectList(Wrappers.<DynamicFormColumn>lambdaQuery()
                .eq(DynamicFormColumn::getFormId, formId))) {
            registered.add(col.getFiledCode());
        }
        Set<String> proposed = new HashSet<>();
        if (proposedMetaData != null) {
            collectFields(proposedMetaData, proposed);
        }
        boolean containsDdl = proposedMetaData != null && !proposed.isEmpty() && !registered.isEmpty()
                && !proposed.equals(registered);
        List<String> removed = new ArrayList<>();
        if (proposedMetaData != null && !proposed.isEmpty()) {
            for (String col : registered) {
                if (!proposed.contains(col)) {
                    removed.add(col);
                }
            }
        }
        List<String> added = new ArrayList<>();
        if (proposedMetaData != null && !proposed.isEmpty()) {
            for (String col : proposed) {
                if (!registered.contains(col)) {
                    added.add(col);
                }
            }
        }
        out.put("containsDDL", containsDdl || !added.isEmpty());
        out.put("containsDeleteAction", !removed.isEmpty());
        out.put("removedFields", removed);
        out.put("addedFields", added);

        // 将注册的权限码（建表链自动注册的 6 件套）
        List<String> perms = List.of(
                "dynamic:" + form.getTableCode() + ":list",
                "dynamic:" + form.getTableCode() + ":add",
                "dynamic:" + form.getTableCode() + ":edit",
                "dynamic:" + form.getTableCode() + ":remove",
                "dynamic:" + form.getTableCode() + ":import",
                "dynamic:" + form.getTableCode() + ":export");
        out.put("registeredPermissionCodes", perms);
        out.put("rolesNeedManualGrant", perms);
        out.put("manualGrantNote", "以上权限码只自动授组织管理员；要让业务人员使用，须管理员在角色上勾选");
        return out;
    }

    /** 遍历提议 metaData 收集 field 编码（Map/Collection 接口遍历，与 B1 同款口径） */
    private void collectFields(Object node, Set<String> out) {
        if (node instanceof Map<?, ?> map) {
            Object field = map.get("field");
            if (field != null && Boolean.TRUE.equals(map.get("input"))) {
                out.add(String.valueOf(field));
            }
            for (Object value : map.values()) {
                collectFields(value, out);
            }
        } else if (node instanceof Iterable<?> col) {
            for (Object item : col) {
                collectFields(item, out);
            }
        }
    }
}
