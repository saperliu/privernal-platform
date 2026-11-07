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

package com.privernal.flow.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.flow.domain.FlowFormBind;
import com.privernal.flow.domain.FlowFormNodeConfig;
import com.privernal.flow.filter.FlowDefinitionFilterDTO;
import com.privernal.flow.mapper.FlowFormBindMapper;
import com.privernal.flow.mapper.FlowFormNodeConfigMapper;
import com.privernal.flow.mapper.FlowQueryMapper;
import com.privernal.flow.service.IFlowDefinitionService;
import com.privernal.flow.service.IFlowFormService;
import com.privernal.flow.service.IFlowFormVersionService;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 流程定义服务实现
 *
 * @author saperliu
 */
@Service
public class FlowDefinitionServiceImpl implements IFlowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionServiceImpl.class);

    /** 平台模板数据的组织标识（organization_id 为空即平台级模板） */
    private static final String TEMPLATE_ORGANIZATION_ID = "";

    @Autowired
    private FlowQueryMapper flowQueryMapper;

    @Autowired
    private com.privernal.flow.handler.FlowTimeoutTaskSyncer timeoutTaskSyncer;

    @Autowired
    private IFlowFormService flowFormService;

    @Autowired
    private IFlowFormVersionService flowFormVersionService;

    @Autowired
    private FlowFormBindMapper flowFormBindMapper;

    @Autowired
    private FlowFormNodeConfigMapper flowFormNodeConfigMapper;

    @Override
    @DS("master")
    public Page<Definition> pageDefinitions(FlowDefinitionFilterDTO filter) {
        int pageNum = filter.getPageNum() == null ? 1 : filter.getPageNum();
        int pageSize = filter.getPageSize() == null ? 10 : filter.getPageSize();
        Page<FlowDefinition> page = new Page<>(pageNum, pageSize);
        return (Page<Definition>) (Page) flowQueryMapper.selectDefinitionPage(page,
                filter.getFlowCode(), filter.getFlowName(), filter.getCategory(), filter.getIsPublish());
    }

    @Override
    @DS("master")
    public Definition getDefinition(Long id) {
        return FlowEngine.defService().getAllDataDefinition(id);
    }

    @Override
    @DS("master")
    public Definition saveDefJson(DefJson defJson) {
        try {
            FlowEngine.defService().saveDef(defJson, false);
            Definition definition = FlowEngine.defService().getPublishByFlowCode(defJson.getFlowCode());
            if (definition == null) {
                List<Definition> definitions = FlowEngine.defService().getByFlowCode(defJson.getFlowCode());
                if (definitions != null && !definitions.isEmpty()) {
                    definition = definitions.get(definitions.size() - 1);
                }
            }
            // D2-A：设计器保存（同 id 覆盖场景）会改变节点 ext.wfTimeoutHours——对该定义的
            // 在办实例触发一次存量重签（幂等改期；未配置超时的待办重签后不变）
            if (defJson.getId() != null) {
                rescheduleTimeoutTasks(defJson.getId());
            }
            return definition;
        } catch (Exception e) {
            throw new ServiceException("保存流程定义失败：" + e.getMessage());
        }
    }

    @Override
    @DS("master")
    public boolean publish(Long id) {
        boolean result = FlowEngine.defService().publish(id);
        if (result) {
            // 流程定义发布成功后，联动发布该流程最新的表单配置草稿（对齐旧 wfs 的发布行为）
            try {
                Definition definition = FlowEngine.defService().getById(id);
                if (definition != null) {
                    flowFormVersionService.publishLatestDraftByFlowCode(definition.getFlowCode());
                }
            } catch (Exception e) {
                log.warn("流程表单配置联动发布失败，可手动发布 versionId={} error={}", id, e.getMessage());
            }
            // D2-A：发布即生效——对该定义的在办实例重签超时任务（新版本定义或覆盖更新后统一入口）
            rescheduleTimeoutTasks(id);
        }
        return result;
    }

    /**
 * 定义保存/发布后对该定义的全部在办实例重签超时任务（尾注，D2-A）。
     * syncByInstance 幂等：未配置超时的待办不注册；在途 TRIGGERED/RUNNING 行返回延迟生效。
     * 失败只告警不阻断发布——每日兜底扫描最终会接住（§10.5）。
     */
    private void rescheduleTimeoutTasks(Long definitionId) {
        try {
            List<Instance> instances = FlowEngine.insService().getByDefId(definitionId);
            if (instances == null || instances.isEmpty()) {
                return;
            }
            for (Instance ins : instances) {
                if (ins != null && ins.getId() != null) {
                    timeoutTaskSyncer.syncByInstanceAsync(ins.getId());
                }
            }
            log.info("[flow-timeout] 定义保存/发布触发存量重签 definitionId={} instances={}", definitionId, instances.size());
        } catch (Exception e) {
            log.warn("定义保存/发布后重签超时任务失败 definitionId={}", definitionId, e);
        }
    }

    @Override
    @DS("master")
    public boolean unPublish(Long id) {
        return FlowEngine.defService().unPublish(id);
    }

    @Override
    @DS("master")
    public boolean active(Long id) {
        return FlowEngine.defService().active(id);
    }

    @Override
    @DS("master")
    public boolean unActive(Long id) {
        return FlowEngine.defService().unActive(id);
    }

    @Override
    @DS("master")
    public boolean copyDef(Long id) {
        return FlowEngine.defService().copyDef(id);
    }

    @Override
    @DS("master")
    public boolean removeDef(String[] ids) {
        for (String id : ids) {
            Definition definition = FlowEngine.defService().getById(id);
            FlowEngine.defService().removeDef(List.of(Long.valueOf(id)));
            if (definition != null) {
                flowFormService.deleteByFlowCode(definition.getFlowCode());
                flowFormVersionService.deleteByFlowCode(definition.getFlowCode());
            }
        }
        return true;
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public int removeByFlowCode(String flowCode) {
        if (StringUtils.isEmpty(flowCode)) {
            return 0;
        }
        List<Definition> definitions = FlowEngine.defService().getByFlowCode(flowCode);
        if (definitions == null || definitions.isEmpty()) {
            return 0;
        }
        List<Long> defIds = definitions.stream().map(Definition::getId).toList();
        // 先清运行数据：remove 会连带删办理人（flow_user）、待办任务（flow_task）与历史任务（flow_his_task）
        List<Instance> instances = FlowEngine.insService().listByDefIds(defIds);
        if (instances != null && !instances.isEmpty()) {
            FlowEngine.insService().remove(instances.stream().map(Instance::getId).toList());
        }
        // 再删定义本体（warm-flow removeDef 连带删节点与跳转），最后清表单绑定/节点配置/表单版本
        FlowEngine.defService().removeDef(defIds);
        flowFormService.deleteByFlowCode(flowCode);
        flowFormVersionService.deleteByFlowCode(flowCode);
        return defIds.size();
    }

    @Override
    @DS("master")
    public String exportJson(Long id) {
        return FlowEngine.defService().exportJson(id);
    }

    @Override
    @DS("master")
    @Transactional(rollbackFor = Exception.class)
    public int initForOrganization(String organizationId, String departmentId) {
        if (StringUtils.isEmpty(organizationId)) {
            throw new ServiceException("组织ID不能为空");
        }
        List<FlowFormBind> templates = selectTemplateBinds();
        if (templates.isEmpty()) {
            log.info("initForOrganization 无平台模板表单绑定，跳过 organizationId={}", organizationId);
            return 0;
        }
        String dept = StringUtils.isEmpty(departmentId) ? organizationId : departmentId;
        int created = 0;
        for (FlowFormBind template : templates) {
            boolean ignore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                OrganizationContextHolder.setOrganizationId(organizationId);
                // 目标组织已绑定同一动态表单则跳过，避免重复初始化
                Long exists = flowFormBindMapper.selectCount(new LambdaQueryWrapper<FlowFormBind>()
                        .eq(FlowFormBind::getTableCode, template.getTableCode())
                        .eq(FlowFormBind::getOrganizationId, organizationId));
                if (exists != null && exists > 0) {
                    continue;
                }
                FlowFormBind bind = new FlowFormBind();
                BeanUtils.copyProperties(template, bind, "id", "createdBy", "createdTime", "updatedBy", "updatedTime");
                bind.setOrganizationId(organizationId);
                bind.setDepartmentId(dept);
                bind.setCreatedTime(new Date());
                flowFormBindMapper.insert(bind);
                copyNodeConfigs(template.getFlowCode(), organizationId, dept);
                created++;
                log.info("initForOrganization 复制模板绑定 flowCode={}, tableCode={} -> organizationId={}",
                        template.getFlowCode(), template.getTableCode(), organizationId);
            } finally {
                OrganizationContextHolder.setIgnore(ignore);
                OrganizationContextHolder.clear();
            }
        }
        return created;
    }

    /**
     * 查询平台级模板表单绑定（organization_id 为空）
     */
    private List<FlowFormBind> selectTemplateBinds() {
        boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            return flowFormBindMapper.selectList(new LambdaQueryWrapper<FlowFormBind>()
                    .eq(FlowFormBind::getOrganizationId, TEMPLATE_ORGANIZATION_ID));
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 复制模板流程的节点字段/按钮权限配置到目标组织
     */
    private void copyNodeConfigs(String flowCode, String organizationId, String departmentId) {
        boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<FlowFormNodeConfig> configs = flowFormNodeConfigMapper.selectList(
                    new LambdaQueryWrapper<FlowFormNodeConfig>()
                            .eq(FlowFormNodeConfig::getDefinitionCode, flowCode)
                            .eq(FlowFormNodeConfig::getOrganizationId, TEMPLATE_ORGANIZATION_ID));
            for (FlowFormNodeConfig config : configs) {
                FlowFormNodeConfig copy = new FlowFormNodeConfig();
                BeanUtils.copyProperties(config, copy, "id", "createdBy", "createdTime", "updatedBy", "updatedTime");
                copy.setOrganizationId(organizationId);
                copy.setDepartmentId(departmentId);
                copy.setCreatedTime(new Date());
                flowFormNodeConfigMapper.insert(copy);
            }
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }
}
