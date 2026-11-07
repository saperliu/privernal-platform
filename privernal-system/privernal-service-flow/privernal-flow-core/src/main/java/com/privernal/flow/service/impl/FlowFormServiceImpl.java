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
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.dynamic.feign.RemoteDynamicService;
import com.privernal.flow.constant.FlowFormConstants;
import com.privernal.flow.domain.FlowFormBind;
import com.privernal.flow.domain.FlowFormNodeConfig;
import com.privernal.flow.dto.FlowFormBtnPermDTO;
import com.privernal.flow.dto.FlowFormFieldPermDTO;
import com.privernal.flow.dto.FlowFormPermissionDTO;
import com.privernal.flow.dto.FlowFormVersionDTO;
import com.privernal.flow.mapper.FlowFormBindMapper;
import com.privernal.flow.mapper.FlowFormNodeConfigMapper;
import com.privernal.flow.mapper.FlowQueryMapper;
import com.privernal.flow.service.IFlowFormService;
import com.privernal.flow.service.IFlowFormVersionService;
import com.privernal.flow.service.IFlowUserResolveService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.FlowEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程表单服务实现
 *
 * @author saperliu
 */
@Service
public class FlowFormServiceImpl implements IFlowFormService {

    @Autowired
    private FlowFormBindMapper flowFormBindMapper;

    @Autowired
    private FlowFormNodeConfigMapper flowFormNodeConfigMapper;

    @Autowired
    private FlowQueryMapper flowQueryMapper;

    @Autowired
    private IFlowUserResolveService flowUserResolveService;

    @Autowired
    private IFlowFormVersionService flowFormVersionService;

    @Autowired
    private RemoteDynamicService remoteDynamicService;

    @Override
    public List<FlowFormBind> selectBindList(FlowFormBind query) {
        LambdaQueryWrapper<FlowFormBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotEmpty(query.getFlowCode()), FlowFormBind::getFlowCode, query.getFlowCode());
        wrapper.like(StringUtils.isNotEmpty(query.getFlowName()), FlowFormBind::getFlowName, query.getFlowName());
        wrapper.eq(StringUtils.isNotEmpty(query.getTableCode()), FlowFormBind::getTableCode, query.getTableCode());
        wrapper.like(StringUtils.isNotEmpty(query.getFormName()), FlowFormBind::getFormName, query.getFormName());
        wrapper.orderByDesc(FlowFormBind::getCreatedTime);
        return flowFormBindMapper.selectList(wrapper);
    }

    @Override
    public FlowFormBind findBindByTableCode(String tableCode) {
        LambdaQueryWrapper<FlowFormBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormBind::getTableCode, tableCode);
        wrapper.last("limit 1");
        return flowFormBindMapper.selectOne(wrapper);
    }

    @Override
    public FlowFormBind findBindByFlowCode(String flowCode) {
        LambdaQueryWrapper<FlowFormBind> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormBind::getFlowCode, flowCode);
        wrapper.last("limit 1");
        return flowFormBindMapper.selectOne(wrapper);
    }

    @Override
    public boolean saveBind(FlowFormBind bind) {
        if (findBindByTableCode(bind.getTableCode()) != null) {
            throw new ServiceException("表单已绑定流程：" + bind.getTableCode());
        }
        if (findBindByFlowCode(bind.getFlowCode()) != null) {
            throw new ServiceException("流程已绑定表单：" + bind.getFlowCode());
        }
        fillOrgInfo(bind);
        return flowFormBindMapper.insert(bind) > 0;
    }

    @Override
    public boolean updateBind(FlowFormBind bind) {
        return flowFormBindMapper.updateById(bind) > 0;
    }

    @Override
    public boolean deleteBindByIds(String[] ids) {
        int rows = 0;
        for (String id : ids) {
            rows += flowFormBindMapper.deleteById(id);
        }
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByFlowCode(String flowCode) {
        LambdaQueryWrapper<FlowFormBind> bindWrapper = new LambdaQueryWrapper<>();
        bindWrapper.eq(FlowFormBind::getFlowCode, flowCode);
        flowFormBindMapper.delete(bindWrapper);
        LambdaQueryWrapper<FlowFormNodeConfig> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(FlowFormNodeConfig::getDefinitionCode, flowCode);
        flowFormNodeConfigMapper.delete(configWrapper);
        return true;
    }

    @Override
    public List<FlowFormNodeConfig> selectNodeConfigList(String definitionCode) {
        LambdaQueryWrapper<FlowFormNodeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormNodeConfig::getDefinitionCode, definitionCode);
        return flowFormNodeConfigMapper.selectList(wrapper);
    }

    @Override
    public boolean saveNodeConfig(FlowFormNodeConfig nodeConfig) {
        if (StringUtils.isEmpty(nodeConfig.getDefinitionCode()) || StringUtils.isEmpty(nodeConfig.getNodeCode())) {
            throw new ServiceException("流程编码与节点编码不能为空");
        }
        LambdaQueryWrapper<FlowFormNodeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormNodeConfig::getDefinitionCode, nodeConfig.getDefinitionCode());
        wrapper.eq(FlowFormNodeConfig::getNodeCode, nodeConfig.getNodeCode());
        FlowFormNodeConfig exists = flowFormNodeConfigMapper.selectOne(wrapper);
        if (exists != null) {
            nodeConfig.setId(exists.getId());
            return flowFormNodeConfigMapper.updateById(nodeConfig) > 0;
        }
        fillOrgInfo(nodeConfig);
        return flowFormNodeConfigMapper.insert(nodeConfig) > 0;
    }

    /**
     * 补齐组织/部门字段（表列为 NOT NULL，自动填充器不处理 null 值）
     */
    private void fillOrgInfo(Object entity) {
        if (entity instanceof FlowFormBind bind) {
            if (StringUtils.isEmpty(bind.getOrganizationId())) {
                bind.setOrganizationId(SecurityUtils.getOrganizationId());
            }
            if (StringUtils.isEmpty(bind.getDepartmentId())) {
                bind.setDepartmentId(SecurityUtils.getDepartmentId());
            }
        } else if (entity instanceof FlowFormNodeConfig nodeConfig) {
            if (StringUtils.isEmpty(nodeConfig.getOrganizationId())) {
                nodeConfig.setOrganizationId(SecurityUtils.getOrganizationId());
            }
            if (StringUtils.isEmpty(nodeConfig.getDepartmentId())) {
                nodeConfig.setDepartmentId(SecurityUtils.getDepartmentId());
            }
        }
    }

    @Override
    public FlowFormPermissionDTO getRuntimePermission(String tableCode, String businessId) {
        // 新版本表（flow_form_version，当前生效发布版）优先
        FlowFormVersionDTO version = flowFormVersionService.findCurrentPublishedByTableCode(tableCode);
        if (version != null) {
            return getRuntimePermissionFromVersion(version, tableCode, businessId);
        }
        // 无发布版本时回退旧表（flow_form_bind + flow_form_node_config），过渡期兼容
        return getRuntimePermissionFromLegacy(tableCode, businessId);
    }

    /**
     * 从版本表解析运行时权限：命中当前节点明细，未命中回退 global 节点明细，
     * 并与动态表单字段全集合并（发布后新增字段自动补默认项：显示、不可编辑）
     */
    private FlowFormPermissionDTO getRuntimePermissionFromVersion(FlowFormVersionDTO version, String tableCode,
                                                                  String businessId) {
        FlowFormPermissionDTO dto = new FlowFormPermissionDTO();
        dto.setId(version.getId());
        dto.setTableCode(tableCode);
        dto.setTableName(version.getFormName());
        dto.setDepartmentId(version.getDepartmentId());
        dto.setOrganizationId(version.getOrganizationId());
        dto.setFieldDTOList(new ArrayList<>());
        dto.setBtnDTOList(new ArrayList<>());

        Instance instance = flowQueryMapper.selectLastInstanceByBusinessId(businessId);
        if (instance == null) {
            dto.setHaveUser(false);
            return dto;
        }
        boolean haveUser = isCurrentUserHandler(instance);
        dto.setHaveUser(haveUser);

        String nodeCode = instance.getNodeCode();
        List<FlowFormFieldPermDTO> fieldPerms = selectNodeFieldPerms(version, nodeCode);
        dto.setFieldDTOList(mergeDynamicColumns(fieldPerms, version.getTableCode(), haveUser));
        dto.setBtnDTOList(toBtnItems(selectNodeBtnPerms(version, nodeCode), haveUser));
        return dto;
    }

    /**
     * 节点权限明细未配置时回退 global 节点（对齐旧 wfs 的全局字段/按钮表语义）
     */
    private List<FlowFormFieldPermDTO> selectNodeFieldPerms(FlowFormVersionDTO version, String nodeCode) {
        List<FlowFormFieldPermDTO> nodePerms = filterFieldPerms(version, nodeCode);
        if (nodePerms.isEmpty()) {
            return filterFieldPerms(version, FlowFormConstants.GLOBAL_NODE_CODE);
        }
        return nodePerms;
    }

    private List<FlowFormFieldPermDTO> filterFieldPerms(FlowFormVersionDTO version, String nodeCode) {
        List<FlowFormFieldPermDTO> result = new ArrayList<>();
        if (version.getFieldPermList() == null) {
            return result;
        }
        for (FlowFormFieldPermDTO perm : version.getFieldPermList()) {
            if (nodeCode != null && nodeCode.equals(perm.getNodeCode())) {
                result.add(perm);
            }
        }
        return result;
    }

    private List<FlowFormBtnPermDTO> selectNodeBtnPerms(FlowFormVersionDTO version, String nodeCode) {
        List<FlowFormBtnPermDTO> nodePerms = filterBtnPerms(version, nodeCode);
        if (nodePerms.isEmpty()) {
            return filterBtnPerms(version, FlowFormConstants.GLOBAL_NODE_CODE);
        }
        return nodePerms;
    }

    private List<FlowFormBtnPermDTO> filterBtnPerms(FlowFormVersionDTO version, String nodeCode) {
        List<FlowFormBtnPermDTO> result = new ArrayList<>();
        if (version.getBtnPermList() == null) {
            return result;
        }
        for (FlowFormBtnPermDTO perm : version.getBtnPermList()) {
            if (nodeCode != null && nodeCode.equals(perm.getNodeCode())) {
                result.add(perm);
            }
        }
        return result;
    }

    /**
     * 与动态表单字段全集合并：权限明细未覆盖的字段补默认项，避免动态表单加字段后前端漏显
     */
    private List<FlowFormPermissionDTO.FieldItem> mergeDynamicColumns(List<FlowFormFieldPermDTO> perms,
                                                                      String tableCode, boolean haveUser) {
        List<FlowFormPermissionDTO.FieldItem> items = new ArrayList<>();
        Set<String> configured = new HashSet<>();
        for (FlowFormFieldPermDTO perm : perms) {
            FlowFormPermissionDTO.FieldItem item = new FlowFormPermissionDTO.FieldItem();
            item.setFieldValue(perm.getFieldCode());
            item.setFieldName(perm.getFieldName());
            item.setIsShow(perm.getIsShow() != null && perm.getIsShow() == 1 ? 1 : 0);
            item.setIsEdit(haveUser && perm.getIsEdit() != null && perm.getIsEdit() == 1 ? 1 : 0);
            item.setIsRequired(haveUser && perm.getIsRequired() != null && perm.getIsRequired() == 1 ? 1 : 0);
            item.setDefaultValue(perm.getDefaultValue());
            item.setDict(perm.getDictType());
            items.add(item);
            if (perm.getFieldCode() != null) {
                configured.add(perm.getFieldCode());
            }
        }
        try {
            List<DynamicFormColumnDTO> columns = remoteDynamicService
                    .findTableColumnByCode(tableCode, com.privernal.common.core.constant.Constants.SERVICE_REQUEST).getData();
            if (columns != null) {
                for (DynamicFormColumnDTO column : columns) {
                    if (column.getFiledCode() == null || configured.contains(column.getFiledCode())) {
                        continue;
                    }
                    FlowFormPermissionDTO.FieldItem item = new FlowFormPermissionDTO.FieldItem();
                    item.setFieldValue(column.getFiledCode());
                    item.setFieldName(column.getFiledName());
                    item.setIsShow(1);
                    item.setIsEdit(0);
                    item.setIsRequired(0);
                    items.add(item);
                }
            }
        } catch (Exception e) {
            // 字段全集合并失败不阻塞权限返回，仅使用已配置的权限明细
        }
        return items;
    }

    private List<FlowFormPermissionDTO.BtnItem> toBtnItems(List<FlowFormBtnPermDTO> perms, boolean haveUser) {
        List<FlowFormPermissionDTO.BtnItem> items = new ArrayList<>();
        for (FlowFormBtnPermDTO perm : perms) {
            FlowFormPermissionDTO.BtnItem item = new FlowFormPermissionDTO.BtnItem();
            item.setFieldValue(perm.getBtnCode());
            item.setFieldName(StringUtils.isNotEmpty(perm.getAliasName()) ? perm.getAliasName() : perm.getBtnName());
            item.setIsShow(haveUser && perm.getIsShow() != null && perm.getIsShow() == 1 ? 1 : 0);
            item.setStatus(perm.getIsShow());
            item.setShowDialog(perm.getShowDialog());
            item.setColor(perm.getColor());
            items.add(item);
        }
        return items;
    }

    /**
     * 旧表（flow_form_bind + flow_form_node_config JSON）运行时权限解析，过渡期回退使用
     */
    private FlowFormPermissionDTO getRuntimePermissionFromLegacy(String tableCode, String businessId) {
        FlowFormPermissionDTO dto = new FlowFormPermissionDTO();
        FlowFormBind bind = findBindByTableCode(tableCode);
        if (bind == null) {
            dto.setTableCode(tableCode);
            dto.setHaveUser(false);
            return dto;
        }
        dto.setId(bind.getId());
        dto.setTableCode(bind.getTableCode());
        dto.setTableName(bind.getFormName());
        dto.setDepartmentId(bind.getDepartmentId());
        dto.setOrganizationId(bind.getOrganizationId());
        dto.setFieldDTOList(new ArrayList<>());
        dto.setBtnDTOList(new ArrayList<>());

        Instance instance = flowQueryMapper.selectLastInstanceByBusinessId(businessId);
        if (instance == null) {
            dto.setHaveUser(false);
            return dto;
        }
        // 解析当前节点配置
        List<FlowFormNodeConfig> configs = selectNodeConfigList(bind.getFlowCode());
        FlowFormNodeConfig nodeConfig = null;
        for (FlowFormNodeConfig config : configs) {
            if (config.getNodeCode() != null && config.getNodeCode().equals(instance.getNodeCode())) {
                nodeConfig = config;
                break;
            }
        }
        // 当前用户是否为当前节点审批人（或流程发起人待提交）
        boolean haveUser = isCurrentUserHandler(instance);
        dto.setHaveUser(haveUser);
        if (nodeConfig == null) {
            return dto;
        }
        dto.setFieldDTOList(parseFieldConfig(nodeConfig.getFieldConfig(), haveUser));
        dto.setBtnDTOList(parseBtnConfig(nodeConfig.getBtnConfig(), haveUser));
        return dto;
    }

    /**
     * 当前登录用户是否为实例当前任务的办理人（含转办/委派权限人）
     */
    @Override
    public boolean isCurrentUserHandler(Instance instance) {
        List<Task> tasks = FlowEngine.taskService().getByInsId(instance.getId());
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        List<String> flags = flowUserResolveService.expandCurrentUserFlags();
        for (Task task : tasks) {
            List<User> users = FlowEngine.userService().getByAssociateds(
                    List.of(task.getId()), UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
            if (users != null) {
                for (User user : users) {
                    if (user.getProcessedBy() != null && flags.contains(user.getProcessedBy())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 解析字段权限配置（非审批人时字段一律不可编辑）
     */
    private List<FlowFormPermissionDTO.FieldItem> parseFieldConfig(String fieldConfig, boolean haveUser) {
        List<FlowFormPermissionDTO.FieldItem> items = new ArrayList<>();
        if (StringUtils.isEmpty(fieldConfig)) {
            return items;
        }
        JSONArray array = JSON.parseArray(fieldConfig);
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            FlowFormPermissionDTO.FieldItem item = new FlowFormPermissionDTO.FieldItem();
            item.setFieldValue(obj.getString("fieldCode"));
            item.setFieldName(obj.getString("fieldName"));
            item.setIsShow(obj.getBooleanValue("show") ? 1 : 0);
            item.setIsEdit(haveUser && obj.getBooleanValue("edit") ? 1 : 0);
            item.setIsRequired(haveUser && obj.getBooleanValue("required") ? 1 : 0);
            item.setDefaultValue(obj.getString("defaultValue"));
            item.setDict(obj.getString("dict"));
            items.add(item);
        }
        return items;
    }

    /**
     * 解析按钮权限配置
     */
    private List<FlowFormPermissionDTO.BtnItem> parseBtnConfig(String btnConfig, boolean haveUser) {
        List<FlowFormPermissionDTO.BtnItem> items = new ArrayList<>();
        if (StringUtils.isEmpty(btnConfig)) {
            return items;
        }
        JSONArray array = JSON.parseArray(btnConfig);
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            FlowFormPermissionDTO.BtnItem item = new FlowFormPermissionDTO.BtnItem();
            item.setFieldValue(obj.getString("btnCode"));
            item.setFieldName(obj.getString("name"));
            item.setIsShow(haveUser && obj.getBooleanValue("show") ? 1 : 0);
            item.setStatus(obj.getInteger("status"));
            item.setShowDialog(obj.getInteger("showDialog"));
            item.setColor(obj.getString("color"));
            items.add(item);
        }
        return items;
    }
}
