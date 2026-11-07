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

import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.dynamic.feign.RemoteDynamicService;
import com.privernal.flow.constant.FlowFormConstants;
import com.privernal.flow.domain.FlowFormBind;
import com.privernal.flow.domain.FlowFormBtnPerm;
import com.privernal.flow.domain.FlowFormFieldPerm;
import com.privernal.flow.domain.FlowFormNodeConfig;
import com.privernal.flow.domain.FlowFormVersion;
import com.privernal.flow.dto.FlowFormBtnPermDTO;
import com.privernal.flow.dto.FlowFormFieldPermDTO;
import com.privernal.flow.dto.FlowFormVersionDTO;
import com.privernal.flow.enums.FlowFormBtnEnum;
import com.privernal.flow.enums.FlowFormPublishStatusEnum;
import com.privernal.flow.mapper.FlowFormBindMapper;
import com.privernal.flow.mapper.FlowFormBtnPermMapper;
import com.privernal.flow.mapper.FlowFormFieldPermMapper;
import com.privernal.flow.mapper.FlowFormNodeConfigMapper;
import com.privernal.flow.mapper.FlowFormVersionMapper;
import com.privernal.flow.service.IFlowFormVersionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程表单配置版本服务实现
 *
 * @author saperliu
 */
@Service
public class FlowFormVersionServiceImpl implements IFlowFormVersionService {

    /**
     * 初始化草稿时默认显示的按钮（任务级操作全开，表单/实例级的保存单独放开）
     */
    private static final Set<String> DEFAULT_SHOW_BTNS = Set.of(
            FlowFormBtnEnum.SAVE.getCode(), FlowFormBtnEnum.VIEW_FORM.getCode(),
            FlowFormBtnEnum.APPROVE.getCode(),
            FlowFormBtnEnum.REJECT.getCode(), FlowFormBtnEnum.TRANSFER.getCode(),
            FlowFormBtnEnum.DELEGATE.getCode(), FlowFormBtnEnum.ADD_SIGN.getCode(),
            FlowFormBtnEnum.REDUCE_SIGN.getCode(), FlowFormBtnEnum.JUMP.getCode(),
            FlowFormBtnEnum.REVOKE.getCode());

    /**
     * 初始化草稿时默认需要填写审批意见（确认弹窗）的按钮
     */
    private static final Set<String> DEFAULT_DIALOG_BTNS = Set.of(
            FlowFormBtnEnum.APPROVE.getCode(), FlowFormBtnEnum.REJECT.getCode());

    @Autowired
    private FlowFormVersionMapper flowFormVersionMapper;

    @Autowired
    private FlowFormFieldPermMapper flowFormFieldPermMapper;

    @Autowired
    private FlowFormBtnPermMapper flowFormBtnPermMapper;

    @Autowired
    private FlowFormBindMapper flowFormBindMapper;

    @Autowired
    private FlowFormNodeConfigMapper flowFormNodeConfigMapper;

    @Autowired
    private RemoteDynamicService remoteDynamicService;

    @Override
    public List<FlowFormVersionDTO> selectVersionList(String flowCode, String tableCode, Integer publishStatus) {
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotEmpty(flowCode), FlowFormVersion::getFlowCode, flowCode);
        wrapper.eq(StringUtils.isNotEmpty(tableCode), FlowFormVersion::getTableCode, tableCode);
        wrapper.eq(publishStatus != null, FlowFormVersion::getPublishStatus, publishStatus);
        wrapper.orderByDesc(FlowFormVersion::getCreatedTime);
        List<FlowFormVersionDTO> result = new ArrayList<>();
        for (FlowFormVersion version : flowFormVersionMapper.selectList(wrapper)) {
            FlowFormVersionDTO dto = new FlowFormVersionDTO();
            BeanUtils.copyProperties(version, dto);
            result.add(dto);
        }
        return result;
    }

    @Override
    public FlowFormVersionDTO selectVersionDetail(String id) {
        FlowFormVersion version = requireVersion(id);
        return toDetailDTO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean initDraft(String flowCode, String tableCode) {
        if (StringUtils.isEmpty(flowCode) || StringUtils.isEmpty(tableCode)) {
            throw new ServiceException("流程编码与动态表单编码不能为空");
        }
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormVersion::getFlowCode, flowCode);
        Long count = flowFormVersionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new ServiceException("该流程已存在表单配置版本，请从历史版本复制草稿");
        }
        FlowFormVersion version = new FlowFormVersion();
        version.setFlowCode(flowCode);
        version.setTableCode(tableCode);
        version.setVersion(1);
        version.setPublishStatus(FlowFormPublishStatusEnum.DRAFT.getCode());
        version.setCurrentFlag(FlowFormConstants.CURRENT_FLAG_NO);
        fillOrgInfo(version);
        flowFormVersionMapper.insert(version);
        // 字段全集取自动态表单模块，全局节点默认全部显示、可编辑
        List<DynamicFormColumnDTO> columns = findDynamicColumns(tableCode);
        int sort = 0;
        for (DynamicFormColumnDTO column : columns) {
            FlowFormFieldPerm perm = new FlowFormFieldPerm();
            perm.setVersionId(version.getId());
            perm.setFlowCode(flowCode);
            perm.setVersion(version.getVersion());
            perm.setNodeCode(FlowFormConstants.GLOBAL_NODE_CODE);
            perm.setFieldCode(column.getFiledCode());
            perm.setFieldName(column.getFiledName());
            perm.setIsShow(1);
            perm.setIsEdit(1);
            perm.setIsRequired(0);
            perm.setSort(++sort);
            fillOrgInfo(perm);
            flowFormFieldPermMapper.insert(perm);
        }
        // 按钮全集取内置按钮枚举，默认放开保存与全部任务级操作，仅终止默认隐藏
        int btnSort = 0;
        for (Map.Entry<String, FlowFormBtnEnum> entry : FlowFormBtnEnum.allButtons().entrySet()) {
            FlowFormBtnEnum btn = entry.getValue();
            FlowFormBtnPerm perm = new FlowFormBtnPerm();
            perm.setVersionId(version.getId());
            perm.setFlowCode(flowCode);
            perm.setVersion(version.getVersion());
            perm.setNodeCode(FlowFormConstants.GLOBAL_NODE_CODE);
            perm.setBtnCode(btn.getCode());
            perm.setBtnName(btn.getName());
            perm.setIsShow(DEFAULT_SHOW_BTNS.contains(btn.getCode()) ? 1 : 0);
            // 通过/驳回默认填写审批意见，其余按钮直接执行；别名与颜色留空，由运营者按需配置
            perm.setShowDialog(DEFAULT_DIALOG_BTNS.contains(btn.getCode()) ? 1 : 0);
            perm.setSort(++btnSort);
            fillOrgInfo(perm);
            flowFormBtnPermMapper.insert(perm);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDraft(FlowFormVersionDTO dto) {
        if (dto == null || StringUtils.isEmpty(dto.getId())) {
            throw new ServiceException("版本id不能为空");
        }
        FlowFormVersion version = requireVersion(dto.getId());
        if (FlowFormPublishStatusEnum.ofCode(version.getPublishStatus()) != FlowFormPublishStatusEnum.DRAFT) {
            throw new ServiceException("已发布版本不可修改，请从历史版本复制新草稿");
        }
        version.setFlowName(dto.getFlowName());
        version.setFormName(dto.getFormName());
        version.setRemark(dto.getRemark());
        flowFormVersionMapper.updateById(version);
        // 明细全量替换
        LambdaQueryWrapper<FlowFormFieldPerm> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(FlowFormFieldPerm::getVersionId, version.getId());
        flowFormFieldPermMapper.delete(fieldWrapper);
        LambdaQueryWrapper<FlowFormBtnPerm> btnWrapper = new LambdaQueryWrapper<>();
        btnWrapper.eq(FlowFormBtnPerm::getVersionId, version.getId());
        flowFormBtnPermMapper.delete(btnWrapper);
        if (dto.getFieldPermList() != null) {
            for (FlowFormFieldPermDTO field : dto.getFieldPermList()) {
                FlowFormFieldPerm perm = new FlowFormFieldPerm();
                BeanUtils.copyProperties(field, perm);
                perm.setId(null);
                perm.setVersionId(version.getId());
                perm.setFlowCode(version.getFlowCode());
                perm.setVersion(version.getVersion());
                fillOrgInfo(perm);
                flowFormFieldPermMapper.insert(perm);
            }
        }
        if (dto.getBtnPermList() != null) {
            for (FlowFormBtnPermDTO btn : dto.getBtnPermList()) {
                FlowFormBtnPerm perm = new FlowFormBtnPerm();
                BeanUtils.copyProperties(btn, perm);
                perm.setId(null);
                perm.setVersionId(version.getId());
                perm.setFlowCode(version.getFlowCode());
                perm.setVersion(version.getVersion());
                fillOrgInfo(perm);
                flowFormBtnPermMapper.insert(perm);
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean copyAsDraft(String sourceVersionId) {
        FlowFormVersion source = requireVersion(sourceVersionId);
        FlowFormVersion draft = new FlowFormVersion();
        BeanUtils.copyProperties(source, draft);
        draft.setId(null);
        draft.setRevision(null);
        draft.setCreatedTime(null);
        draft.setUpdatedTime(null);
        draft.setPublishStatus(FlowFormPublishStatusEnum.DRAFT.getCode());
        draft.setCurrentFlag(FlowFormConstants.CURRENT_FLAG_NO);
        draft.setPublishTime(null);
        draft.setPublishBy(null);
        draft.setVersion(nextVersion(source.getFlowCode()));
        fillOrgInfo(draft);
        flowFormVersionMapper.insert(draft);
        LambdaQueryWrapper<FlowFormFieldPerm> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(FlowFormFieldPerm::getVersionId, source.getId());
        for (FlowFormFieldPerm perm : flowFormFieldPermMapper.selectList(fieldWrapper)) {
            FlowFormFieldPerm copy = new FlowFormFieldPerm();
            BeanUtils.copyProperties(perm, copy);
            copy.setId(null);
            copy.setRevision(null);
            copy.setCreatedTime(null);
            copy.setUpdatedTime(null);
            copy.setVersionId(draft.getId());
            copy.setVersion(draft.getVersion());
            fillOrgInfo(copy);
            flowFormFieldPermMapper.insert(copy);
        }
        LambdaQueryWrapper<FlowFormBtnPerm> btnWrapper = new LambdaQueryWrapper<>();
        btnWrapper.eq(FlowFormBtnPerm::getVersionId, source.getId());
        for (FlowFormBtnPerm perm : flowFormBtnPermMapper.selectList(btnWrapper)) {
            FlowFormBtnPerm copy = new FlowFormBtnPerm();
            BeanUtils.copyProperties(perm, copy);
            copy.setId(null);
            copy.setRevision(null);
            copy.setCreatedTime(null);
            copy.setUpdatedTime(null);
            copy.setVersionId(draft.getId());
            copy.setVersion(draft.getVersion());
            fillOrgInfo(copy);
            flowFormBtnPermMapper.insert(copy);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publish(String id) {
        FlowFormVersion version = requireVersion(id);
        if (FlowFormPublishStatusEnum.ofCode(version.getPublishStatus()) != FlowFormPublishStatusEnum.DRAFT) {
            throw new ServiceException("该版本已发布");
        }
        LambdaQueryWrapper<FlowFormFieldPerm> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(FlowFormFieldPerm::getVersionId, version.getId());
        if (flowFormFieldPermMapper.selectCount(fieldWrapper) == 0) {
            throw new ServiceException("版本未配置任何字段权限，不能发布");
        }
        version.setPublishStatus(FlowFormPublishStatusEnum.PUBLISHED.getCode());
        version.setCurrentFlag(FlowFormConstants.CURRENT_FLAG_YES);
        version.setPublishTime(new Date());
        version.setPublishBy(SecurityUtils.getUserId());
        flowFormVersionMapper.updateById(version);
        clearCurrentFlag(version.getFlowCode(), version.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollback(String id) {
        FlowFormVersion version = requireVersion(id);
        if (FlowFormPublishStatusEnum.ofCode(version.getPublishStatus()) != FlowFormPublishStatusEnum.PUBLISHED) {
            throw new ServiceException("仅已发布版本支持回滚");
        }
        version.setCurrentFlag(FlowFormConstants.CURRENT_FLAG_YES);
        flowFormVersionMapper.updateById(version);
        clearCurrentFlag(version.getFlowCode(), version.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVersion(String id) {
        FlowFormVersion version = requireVersion(id);
        if (FlowFormPublishStatusEnum.ofCode(version.getPublishStatus()) == FlowFormPublishStatusEnum.PUBLISHED
                && FlowFormConstants.CURRENT_FLAG_YES.equals(version.getCurrentFlag())) {
            throw new ServiceException("当前生效版本不可删除，请先发布或回滚到其它版本");
        }
        deleteDetails(version.getId());
        flowFormVersionMapper.deleteById(version.getId());
        return true;
    }

    @Override
    public FlowFormVersionDTO findCurrentPublishedByTableCode(String tableCode) {
        if (StringUtils.isEmpty(tableCode)) {
            return null;
        }
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormVersion::getTableCode, tableCode);
        wrapper.eq(FlowFormVersion::getCurrentFlag, FlowFormConstants.CURRENT_FLAG_YES);
        wrapper.last("limit 1");
        FlowFormVersion version = flowFormVersionMapper.selectOne(wrapper);
        return version == null ? null : toDetailDTO(version);
    }

    @Override
    public FlowFormVersionDTO findCurrentPublishedByFlowCode(String flowCode) {
        if (StringUtils.isEmpty(flowCode)) {
            return null;
        }
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormVersion::getFlowCode, flowCode);
        wrapper.eq(FlowFormVersion::getCurrentFlag, FlowFormConstants.CURRENT_FLAG_YES);
        wrapper.last("limit 1");
        FlowFormVersion version = flowFormVersionMapper.selectOne(wrapper);
        return version == null ? null : toDetailDTO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishLatestDraftByFlowCode(String flowCode) {
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormVersion::getFlowCode, flowCode);
        wrapper.eq(FlowFormVersion::getPublishStatus, FlowFormPublishStatusEnum.DRAFT.getCode());
        wrapper.orderByDesc(FlowFormVersion::getVersion);
        List<FlowFormVersion> drafts = flowFormVersionMapper.selectList(wrapper);
        if (drafts == null || drafts.isEmpty()) {
            return false;
        }
        FlowFormVersion draft = drafts.get(0);
        LambdaQueryWrapper<FlowFormFieldPerm> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(FlowFormFieldPerm::getVersionId, draft.getId());
        if (flowFormFieldPermMapper.selectCount(fieldWrapper) == 0) {
            // 草稿无任何字段配置时不自动发布，保持上一个发布版本生效
            return false;
        }
        draft.setPublishStatus(FlowFormPublishStatusEnum.PUBLISHED.getCode());
        draft.setCurrentFlag(FlowFormConstants.CURRENT_FLAG_YES);
        draft.setPublishTime(new Date());
        draft.setPublishBy(SecurityUtils.getUserId());
        flowFormVersionMapper.updateById(draft);
        clearCurrentFlag(flowCode, draft.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByFlowCode(String flowCode) {
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormVersion::getFlowCode, flowCode);
        for (FlowFormVersion version : flowFormVersionMapper.selectList(wrapper)) {
            deleteDetails(version.getId());
            flowFormVersionMapper.deleteById(version.getId());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateFromLegacy() {
        List<FlowFormBind> binds = flowFormBindMapper.selectList(null);
        int migrated = 0;
        for (FlowFormBind bind : binds) {
            LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FlowFormVersion::getFlowCode, bind.getFlowCode());
            if (flowFormVersionMapper.selectCount(wrapper) > 0) {
                continue;
            }
            FlowFormVersion version = new FlowFormVersion();
            version.setFlowCode(bind.getFlowCode());
            version.setFlowName(bind.getFlowName());
            version.setTableCode(bind.getTableCode());
            version.setFormName(bind.getFormName());
            version.setVersion(1);
            version.setPublishStatus(FlowFormPublishStatusEnum.PUBLISHED.getCode());
            version.setCurrentFlag(FlowFormConstants.CURRENT_FLAG_YES);
            version.setPublishTime(new Date());
            version.setPublishBy(SecurityUtils.getUserId());
            version.setOrganizationId(bind.getOrganizationId());
            version.setDepartmentId(bind.getDepartmentId());
            flowFormVersionMapper.insert(version);
            LambdaQueryWrapper<FlowFormNodeConfig> configWrapper = new LambdaQueryWrapper<>();
            configWrapper.eq(FlowFormNodeConfig::getDefinitionCode, bind.getFlowCode());
            for (FlowFormNodeConfig config : flowFormNodeConfigMapper.selectList(configWrapper)) {
                insertLegacyFieldPerms(version, config);
                insertLegacyBtnPerms(version, config);
            }
            migrated++;
        }
        return migrated;
    }

    private void insertLegacyFieldPerms(FlowFormVersion version, FlowFormNodeConfig config) {
        if (StringUtils.isEmpty(config.getFieldConfig())) {
            return;
        }
        JSONArray array = JSON.parseArray(config.getFieldConfig());
        int sort = 0;
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            FlowFormFieldPerm perm = new FlowFormFieldPerm();
            perm.setVersionId(version.getId());
            perm.setFlowCode(version.getFlowCode());
            perm.setVersion(version.getVersion());
            perm.setNodeCode(config.getNodeCode());
            perm.setFieldCode(obj.getString("fieldCode"));
            perm.setFieldName(obj.getString("fieldName"));
            perm.setIsShow(obj.getBooleanValue("show") ? 1 : 0);
            perm.setIsEdit(obj.getBooleanValue("edit") ? 1 : 0);
            perm.setIsRequired(obj.getBooleanValue("required") ? 1 : 0);
            perm.setDefaultValue(obj.getString("defaultValue"));
            perm.setDictType(obj.getString("dict"));
            perm.setSort(++sort);
            perm.setOrganizationId(config.getOrganizationId());
            perm.setDepartmentId(config.getDepartmentId());
            flowFormFieldPermMapper.insert(perm);
        }
    }

    private void insertLegacyBtnPerms(FlowFormVersion version, FlowFormNodeConfig config) {
        if (StringUtils.isEmpty(config.getBtnConfig())) {
            return;
        }
        JSONArray array = JSON.parseArray(config.getBtnConfig());
        int sort = 0;
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            FlowFormBtnPerm perm = new FlowFormBtnPerm();
            perm.setVersionId(version.getId());
            perm.setFlowCode(version.getFlowCode());
            perm.setVersion(version.getVersion());
            perm.setNodeCode(config.getNodeCode());
            perm.setBtnCode(obj.getString("btnCode"));
            perm.setBtnName(obj.getString("name"));
            perm.setIsShow(obj.getBooleanValue("show") ? 1 : 0);
            perm.setShowDialog(obj.getInteger("showDialog"));
            perm.setColor(obj.getString("color"));
            perm.setSort(++sort);
            perm.setOrganizationId(config.getOrganizationId());
            perm.setDepartmentId(config.getDepartmentId());
            flowFormBtnPermMapper.insert(perm);
        }
    }

    /**
     * 从动态表单模块获取字段全集（单体部署为本地 Bean 调用）
     */
    private List<DynamicFormColumnDTO> findDynamicColumns(String tableCode) {
        List<DynamicFormColumnDTO> columns;
        try {
            columns = remoteDynamicService
                    .findTableColumnByCode(tableCode, Constants.SERVICE_REQUEST).getData();
        } catch (Exception e) {
            throw new ServiceException("查询动态表单字段失败：" + tableCode + "，" + e.getMessage());
        }
        if (columns == null || columns.isEmpty()) {
            throw new ServiceException("动态表单不存在或未配置字段：" + tableCode);
        }
        return columns;
    }

    private FlowFormVersion requireVersion(String id) {
        FlowFormVersion version = flowFormVersionMapper.selectById(id);
        if (version == null) {
            throw new ServiceException("表单配置版本不存在：" + id);
        }
        return version;
    }

    private Integer nextVersion(String flowCode) {
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormVersion::getFlowCode, flowCode);
        wrapper.orderByDesc(FlowFormVersion::getVersion);
        List<FlowFormVersion> versions = flowFormVersionMapper.selectList(wrapper);
        return versions.stream().map(FlowFormVersion::getVersion)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    /**
     * 同流程下仅保留指定版本为当前生效发布版
     */
    private void clearCurrentFlag(String flowCode, String keepId) {
        LambdaQueryWrapper<FlowFormVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowFormVersion::getFlowCode, flowCode);
        wrapper.eq(FlowFormVersion::getCurrentFlag, FlowFormConstants.CURRENT_FLAG_YES);
        wrapper.ne(FlowFormVersion::getId, keepId);
        for (FlowFormVersion other : flowFormVersionMapper.selectList(wrapper)) {
            other.setCurrentFlag(FlowFormConstants.CURRENT_FLAG_NO);
            flowFormVersionMapper.updateById(other);
        }
    }

    private void deleteDetails(String versionId) {
        LambdaQueryWrapper<FlowFormFieldPerm> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(FlowFormFieldPerm::getVersionId, versionId);
        flowFormFieldPermMapper.delete(fieldWrapper);
        LambdaQueryWrapper<FlowFormBtnPerm> btnWrapper = new LambdaQueryWrapper<>();
        btnWrapper.eq(FlowFormBtnPerm::getVersionId, versionId);
        flowFormBtnPermMapper.delete(btnWrapper);
    }

    private FlowFormVersionDTO toDetailDTO(FlowFormVersion version) {
        FlowFormVersionDTO dto = new FlowFormVersionDTO();
        BeanUtils.copyProperties(version, dto);
        LambdaQueryWrapper<FlowFormFieldPerm> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.eq(FlowFormFieldPerm::getVersionId, version.getId());
        List<FlowFormFieldPermDTO> fields = new ArrayList<>();
        for (FlowFormFieldPerm perm : flowFormFieldPermMapper.selectList(fieldWrapper)) {
            FlowFormFieldPermDTO field = new FlowFormFieldPermDTO();
            BeanUtils.copyProperties(perm, field);
            fields.add(field);
        }
        dto.setFieldPermList(fields);
        LambdaQueryWrapper<FlowFormBtnPerm> btnWrapper = new LambdaQueryWrapper<>();
        btnWrapper.eq(FlowFormBtnPerm::getVersionId, version.getId());
        List<FlowFormBtnPermDTO> btns = new ArrayList<>();
        for (FlowFormBtnPerm perm : flowFormBtnPermMapper.selectList(btnWrapper)) {
            FlowFormBtnPermDTO btn = new FlowFormBtnPermDTO();
            BeanUtils.copyProperties(perm, btn);
            btns.add(btn);
        }
        dto.setBtnPermList(btns);
        return dto;
    }

    private void fillOrgInfo(Object entity) {
        if (entity instanceof FlowFormVersion version) {
            if (StringUtils.isEmpty(version.getOrganizationId())) {
                version.setOrganizationId(SecurityUtils.getOrganizationId());
            }
            if (StringUtils.isEmpty(version.getDepartmentId())) {
                version.setDepartmentId(SecurityUtils.getDepartmentId());
            }
        } else if (entity instanceof FlowFormFieldPerm perm) {
            if (StringUtils.isEmpty(perm.getOrganizationId())) {
                perm.setOrganizationId(SecurityUtils.getOrganizationId());
            }
            if (StringUtils.isEmpty(perm.getDepartmentId())) {
                perm.setDepartmentId(SecurityUtils.getDepartmentId());
            }
        } else if (entity instanceof FlowFormBtnPerm perm) {
            if (StringUtils.isEmpty(perm.getOrganizationId())) {
                perm.setOrganizationId(SecurityUtils.getOrganizationId());
            }
            if (StringUtils.isEmpty(perm.getDepartmentId())) {
                perm.setDepartmentId(SecurityUtils.getDepartmentId());
            }
        }
    }
}
