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

package com.privernal.dynamic.dbs.api;

import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.service.CommonService;
import com.privernal.dynamic.dbs.service.IDynamicDatabaseService;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.dynamic.dto.DynamicFormDTO;
import com.privernal.dynamic.feign.RemoteDynamicService;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;
import com.privernal.system.dto.SystemSocialAppConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RemoteDynamicServiceImpl implements RemoteDynamicService {

    @Autowired
    CommonService commonService;

    @Autowired
    private IDynamicDatabaseService dynamicDatabaseService;

    @Autowired
    private IDynamicFormColumnService dynamicFormColumnService;

    @Autowired
    private IDynamicFormService dynamicFormService;

    @Autowired
    private com.privernal.dynamic.dbs.service.DynamicTableService dynamicTableService;

    @Autowired
    private com.privernal.dynamic.dbs.mapper.DynamicFormMapper dynamicFormMapper;

    /** ObjectProvider 惰性解析：联动钩子在 advanced 包，避免与本服务的 bean 环 */
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.privernal.dynamic.advanced.trigger.EffectiveEventHook> effectiveEventHookProvider;


    @Override
    public CommonResult<List<DynamicFormColumnDTO>> findTableColumnByCode(String tableCode, String request) {
        List<DynamicFormColumnDTO> list = dynamicFormColumnService.selectTableColumnByCode(tableCode);
        return CommonResult.ok(list);
    }

    @Override
    public DynamicDatabaseDTO findDynamicDatabaseByCode(String organizationId) {
        Assert.notNull(organizationId, "organizationId不能为空");
        Boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            // 钉钉的免登的使用，这时没有登录，没有组织信息
            OrganizationContextHolder.setIgnore(true);
            return dynamicDatabaseService.selectDynamicDatabase(organizationId);
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }

    /**
 * EFFECTIVE 联动挂点：syncBusinessStatus 是唯一漏斗（14 处调用
     * 全经这里汇入），在 status→1（已完成）分支做状态跃迁判定后触发联动。
     * 上游会重复回写同状态且无幂等，跃迁判定（before.status != 1）由本钩子承担；
     * 钩子失败不影响回写结果（返回 true），联动自身的可靠性由 dynamic_linkage_log 承担。
     */
    @Override
    public boolean updateBusinessStatus(String tableCode, String dataId, Integer status) {
        if (StringUtils.isEmpty(tableCode) || StringUtils.isEmpty(dataId) || status == null) {
            return false;
        }
        try {
            String database = SecurityUtils.getDatabaseName();
            // 组织前缀改造（upgrade_dynamic_table_prefix）后 flow_form_bind/flow_form_version 里的
            // table_code 是未加前缀的旧编码，回写会找不到物理表——按后缀自愈解析到当前表单编码
            tableCode = resolveDecoratedTableCode(tableCode);
            // 跃迁判定需要回写前的行（before.status != 1 才算 EFFECTIVE，§7.2）
            Map<String, Object> beforeRow = status == 1
                    ? dynamicTableService.findById(database, tableCode, dataId) : null;
            Map<String, Object> data = new HashMap<>();
            data.put("id", dataId);
            data.put("status", status);
            commonService.updateTableData(database, tableCode, data);
            if (beforeRow != null) {
                fireEffectiveHook(database, tableCode, dataId, beforeRow);
            }
            return true;
        } catch (Exception e) {
            log.error("回写业务数据工作流状态失败 tableCode={} dataId={} status={}", tableCode, dataId, status, e);
            return false;
        }
    }

    /**
     * 流程侧传来的 tableCode 可能是前缀改造前的旧编码（如 kill_test），
     * 按 dynamic_form.table_code 精确→`_%s` 后缀匹配自愈到当前物理编码；查不到按原样返回。
     */
    private String resolveDecoratedTableCode(String tableCode) {
        try {
            com.privernal.dynamic.dbs.domain.DynamicForm form = dynamicFormService.selectDynamicFormByTableCode(tableCode);
            if (form != null && StringUtils.isNotEmpty(form.getTableCode())) {
                return form.getTableCode();
            }
            com.privernal.common.mybatis.query.LambdaPrivernalWrapper<com.privernal.dynamic.dbs.domain.DynamicForm> wrapper =
                    new com.privernal.common.mybatis.query.LambdaPrivernalWrapper<>();
            wrapper.apply("table_code like {0}", "%_" + tableCode);
            wrapper.last("limit 1");
            com.privernal.dynamic.dbs.domain.DynamicForm suffixed = dynamicFormMapper.selectOne(wrapper);
            if (suffixed != null && StringUtils.isNotEmpty(suffixed.getTableCode())) {
                log.info("流程回写 tableCode 自愈：{} → {}", tableCode, suffixed.getTableCode());
                return suffixed.getTableCode();
            }
        } catch (Exception e) {
            log.warn("流程回写 tableCode 自愈解析失败，按原编码继续：{}", tableCode, e);
        }
        return tableCode;
    }

    private void fireEffectiveHook(String database, String tableCode, String dataId, Map<String, Object> beforeRow) {
        try {
            com.privernal.dynamic.advanced.trigger.EffectiveEventHook hook = effectiveEventHookProvider.getIfAvailable();
            if (hook == null) {
                return;
            }
            Map<String, Object> afterRow = dynamicTableService.findById(database, tableCode, dataId);
            if (afterRow == null) {
                return;
            }
            // 组织取上下文，缺了按会话兜底（登录链路 OrganizationContextHolder 常为空，
            // 缺组织时钩子会静默跳过——与 publishDataEvent 同一口径）
            String orgId = OrganizationContextHolder.getOrganizationId();
            if (StringUtils.isEmpty(orgId)) {
                orgId = SecurityUtils.getOrganizationId();
            }
            hook.fireOnFinish(database, orgId, tableCode, dataId, beforeRow, afterRow);
        } catch (Exception e) {
            log.error("EFFECTIVE 联动触发失败 tableCode={} dataId={}", tableCode, dataId, e);
        }
    }

    @Override
    public String findTableCodeByFlowCode(String flowCode) {
        if (StringUtils.isEmpty(flowCode)) {
            return null;
        }
        DynamicFormFilterDTO filter = new DynamicFormFilterDTO();
        filter.setWorkflowCode(flowCode);
        filter.setIsWorkflow(1L);
        List<DynamicFormDTO> list = dynamicFormService.selectDynamicFormList(filter);
        if (list == null || list.isEmpty()) {
            return null;
        }
        if (list.size() > 1) {
            log.warn("流程 {} 被 {} 个动态表单绑定，业务状态回写取第一个：{}",
                    flowCode, list.size(), list.get(0).getTableCode());
        }
        return list.get(0).getTableCode();
    }
}
