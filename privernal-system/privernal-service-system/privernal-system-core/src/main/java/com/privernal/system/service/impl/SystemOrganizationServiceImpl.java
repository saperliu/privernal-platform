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

package com.privernal.system.service.impl;

import com.privernal.common.core.constant.RoleConstants;
import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.ProductModuleEnums;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.system.domain.*;
import com.privernal.system.dto.*;
import com.privernal.system.filter.SystemCommonGroupMenuFilterDTO;
import com.privernal.system.filter.SystemOrganizationFilterDTO;
import com.privernal.system.mapper.*;
import com.privernal.system.service.ISystemDictionaryTypeService;
import com.privernal.system.service.ISystemOrganizationService;
import com.privernal.system.service.ISystemUserService;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 组织的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 组织的Service业务层处理
 */
@Slf4j
@Service
public class SystemOrganizationServiceImpl implements ISystemOrganizationService {
    @Autowired
    private SystemOrganizationMapper systemOrganizationMapper;

    @Autowired
    private SystemDeptRelationMapper systemDeptRelationMapper;

    @Autowired
    private SystemDepartmentMapper systemDepartmentMapper;

    @Autowired
    private SystemUserServiceOrgMapper systemUserServiceOrgMapper;

    @Autowired
    private ISystemUserService systemUserService;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired
    private SystemRoleUserMapper systemRoleUserMapper;

    @Autowired
    private SystemRoleMenuMapper systemRoleMenuMapper;

    @Autowired
    private SystemOrganizationMenuMapper systemOrganizationMenuMapper;

    @Autowired
    private SystemUserDepartmentMapper systemUserDepartmentMapper;

    @Autowired
    private ISystemDictionaryTypeService systemDictionaryTypeService;

    @Autowired
    private SystemOrganizationAppMapper systemOrganizationAppMapper;

    @Autowired
    private SystemAppMenuMapper systemAppMenuMapper;
    @Autowired
    private SystemAppInfoMapper systemAppInfoMapper;

    @Autowired
    private SystemCommonGroupMenuMapper systemCommonGroupMenuMapper;
    @Autowired
    private SystemCommonGroupMapper systemCommonGroupMapper;
    @Autowired
    private SystemUserGroupMapper systemUserGroupMapper;

    @Autowired
    private SystemRoleUserDeptMapper systemRoleUserDeptMapper;

    @Autowired
    private SystemRoleGroupRoleMapper systemRoleGroupRoleMapper;

    /**
     * 查询组织
     *
     * @param id 组织主键
     * @return 组织
     */
    @Transactional(readOnly = true)
    @Override
    public SystemOrganization selectSystemOrganizationById(String id) {
        return systemOrganizationMapper.selectById(id);
    }

    /**
     * 查询组织列表
     *
     * @param systemOrganization 组织
     * @return 组织
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemOrganization> selectSystemOrganizationList(SystemOrganizationFilterDTO systemOrganization) {
        return systemOrganizationMapper.selectSystemOrganizationList(systemOrganization);
    }

    /**
     * 新增组织
     *
     * @param systemOrganizationCreateDTO 组织
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemOrganizationDTO insertSystemOrganization(SystemOrganizationCreateDTO systemOrganizationCreateDTO) {
        // 创建
        SystemOrganization systemOrganization = new SystemOrganization();
        BeanUtils.copyProperties(systemOrganizationCreateDTO, systemOrganization);

        // 检查编码唯一
        List<SystemOrganization> count = systemOrganizationMapper.selectOrganizationByCode(systemOrganization.getOrganizationCode());
        if (!count.isEmpty()) {
            throw new ServiceException("编码重复，请修改");
        }
        systemOrganizationMapper.insert(systemOrganization);

        String userId = systemOrganization.getLeaderId();
        // 创建默认的部门
        SystemDepartment systemDepartment = new SystemDepartment();
        systemDepartment.setOrganizationId(systemOrganization.getId());
        systemDepartment.setStatus(WorkflowDataStatus.FINISH.getCode());
        systemDepartment.setDeptCode(DataConstants.DATA_TYPE_SYSTEM);
        systemDepartment.setLeaderId(systemOrganization.getLeaderId());
        systemDepartment.setDeptName(systemOrganizationCreateDTO.getOrganizationName());
        systemDepartment.setOrderNum(1L);
        systemDepartment.setSystemType(DataConstants.DATA_TYPE_SYSTEM);
        systemDepartment.setId(systemOrganization.getId());
        systemDepartmentMapper.insert(systemDepartment);

        SystemDeptRelation deptRelation = new SystemDeptRelation();
        deptRelation.setDeptId(systemDepartment.getId());
        deptRelation.setParentId(systemOrganization.getId());
        deptRelation.setLevel(1L);
        systemDeptRelationMapper.insert(deptRelation);

        // 创建组织共享的部门，单独一个，如果是顶级，有可能权限过大。授权了部门及以下的话。
//        SystemDepartment systemDepartmentShare = new SystemDepartment();
//        systemDepartmentShare.setOrganizationId(systemOrganization.getId());
//        systemDepartmentShare.setStatus(WorkflowDataStatus.FINISH.getCode());
//        systemDepartmentShare.setDeptCode(DATA_TYPE_SYSTEM);
//        systemDepartmentShare.setLeaderId(systemOrganization.getLeaderId());
//        systemDepartmentShare.setDeptName("共享数据部门");
//        systemDepartmentShare.setOrderNum(2L);
//        systemDepartmentShare.setId(systemOrganization.getId());
//        systemDepartmentShare.setSystemType(DATA_TYPE_SYSTEM);
//        systemDepartmentMapper.insert(systemDepartmentShare);
//
//        SystemDeptRelation deptRelation2 = new SystemDeptRelation();
//        deptRelation2.setDeptId(systemDepartmentShare.getId());
//        deptRelation2.setParentId(systemDepartment.getId());
//        deptRelation2.setLevel(1L);
//        systemDeptRelationMapper.insert(deptRelation2);
//        SystemDeptRelation deptRelation3 = new SystemDeptRelation();
//        deptRelation3.setDeptId(systemDepartmentShare.getId());
//        deptRelation3.setParentId(systemOrganization.getId());
//        deptRelation3.setLevel(2L);
//        systemDeptRelationMapper.insert(deptRelation3);

        if (StringUtils.isNotEmpty(systemOrganizationCreateDTO.getLoginName())) {
            // 没用现有人员，需要把组织管理员同时创建
            SystemUser systemUser = new SystemUser();
            BeanUtils.copyProperties(systemOrganizationCreateDTO, systemUser);
            systemUser.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ALL);
            systemUser.setOrganizationId(systemOrganization.getId());
            systemUser.setDepartmentId(systemDepartment.getId());
            int result = systemUserService.insertSystemUser(systemUser);
            if (result > 1) {
                log.error("create organization insert user result {}", result);
                throw new ServiceException("创建用户失败", Integer.toString(result));
            }

            userId = systemUser.getId();

            SystemUserDepartment userDepartment = new SystemUserDepartment();
            userDepartment.setDepartmentId(systemDepartment.getId());
            userDepartment.setOrganizationId(systemOrganization.getId());
            userDepartment.setUserId(userId);
            userDepartment.setIsMain(1L);
            systemUserDepartmentMapper.insert(userDepartment);

            if (StringUtils.isEmpty(systemOrganization.getLeaderId())) {
                systemDepartment.setLeaderId(userId);
                // 去掉组织权限的条件，更新的部门，非创建组织的人的组织。创建组织的人是系统平台管理员
                boolean ignore = OrganizationContextHolder.isIgnore();
                try {
                    OrganizationContextHolder.setIgnore(true);
                    systemDepartmentMapper.updateById(systemDepartment);
                } finally {
                    OrganizationContextHolder.setIgnore(ignore);
                    OrganizationContextHolder.clear();
                }
            }
            // 建立这个人与组织的关系,已经在上面用户里创建
            systemOrganization.setLeaderId(systemUser.getId());
            systemOrganizationMapper.updateById(systemOrganization);
        } else {
            // 保证只有一个设置生效
            if (StringUtils.isNotEmpty(systemOrganizationCreateDTO.getLeaderId())) {
                // 建立这个人与组织的关系
                SystemUserServiceOrg userServiceOrg = new SystemUserServiceOrg();
                userServiceOrg.setUserId(systemOrganization.getLeaderId());
                userServiceOrg.setOrganizationId(systemOrganization.getId());
                userServiceOrg.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ALL);
                userServiceOrg.setDepartmentId(systemDepartment.getId());
                systemUserServiceOrgMapper.insert(userServiceOrg);
            }
        }

        //初始化域值
        systemDictionaryTypeService.initDictionaryType(systemOrganization.getId(), systemDepartment.getId());
        // 创建默认的角色
        SystemRole systemRole = new SystemRole();
        systemRole.setOrganizationId(systemOrganization.getId());
        systemRole.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
        systemRole.setDepartmentId(systemDepartment.getId());
        systemRole.setOrderNum(1L);
        systemRole.setRoleName("组织管理员");
        systemRole.setRoleKey(RoleConstants.SUPER_ROLE_ADMIN);
        systemRole.setSystemType(DataConstants.DATA_TYPE_SYSTEM);
        systemRoleMapper.insert(systemRole);

        // 创建组织基本的应用。系统管理
        SystemOrganizationAppCreateDTO systemOrganizationAppCreateDTO = new SystemOrganizationAppCreateDTO();
        systemOrganizationAppCreateDTO.setOrganizationId(systemOrganization.getId());
        SystemOrganizationAppDTO systemOrganizationAppDTO = new SystemOrganizationAppDTO();

        SystemAppInfo systemAppInfo = systemAppInfoMapper.selectOne(new LambdaQueryWrapper<SystemAppInfo>().eq(SystemAppInfo::getCode, ProductModuleEnums.SYSTEM.getCode())
                .eq(SystemAppInfo::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT)
                .eq(SystemAppInfo::getStatus, WorkflowDataStatus.FINISH.getCode()));
        systemOrganizationAppDTO.setAppId(systemAppInfo.getId());
        systemOrganizationAppDTO.setStatus(WorkflowDataStatus.FINISH.getCode());
        systemOrganizationAppDTO.setOrganizationId(systemOrganization.getId());
        systemOrganizationAppDTO.setActionTag(DataConstants.ACTION_TAG_ADD);
        systemOrganizationAppCreateDTO.setAppList(List.of(systemOrganizationAppDTO));
        saveSystemOrganizationApp(systemOrganizationAppCreateDTO);

        // 将用户加入角色
        SystemRoleUser systemRoleUser = new SystemRoleUser();
        systemRoleUser.setUserId(userId);
        systemRoleUser.setRoleId(systemRole.getId());
        systemRoleUserMapper.insert(systemRoleUser);

        SystemOrganizationDTO systemOrganizationDTO = new SystemOrganizationDTO();
        BeanUtils.copyProperties(systemOrganization, systemOrganizationDTO);
        return systemOrganizationDTO;
    }

    /**
     * 修改组织
     *
     * @param systemOrganization 组织
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemOrganization(SystemOrganization systemOrganization) {
        return systemOrganizationMapper.updateById(systemOrganization);
    }

    /**
     * 批量删除组织
     *
     * @param ids 需要删除的组织主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemOrganizationByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemOrganizationById(id);
        }
        return ids.length;
    }

    /**
     * 删除组织信息
     *
     * @param id 组织主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemOrganizationById(String id) {

        // 删除组织，其它数据依附在组织上，所以只删除组织，其它数据不处理。
        return systemOrganizationMapper.deleteFillById(id);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int saveSystemOrganizationMenu(SystemOrganizationMenuCreateDTO systemOrganizationMenuCreateDTO) {

        List<String> list = systemOrganizationMenuCreateDTO.getMenuDTOList().stream().map(SystemMenuDTO::getId).toList();

        List<SystemOrganizationMenuDTO> oldSystemMenuList = null;
        SystemRole systemRole = null;
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
            OrganizationContextHolder.setIgnore(true);
            oldSystemMenuList = systemOrganizationMenuMapper.selectMenuAllByOrganizationId(systemOrganizationMenuCreateDTO.getOrganizationId());

            // 查询该组织唯一的系统级的角色
//            systemRole = systemRoleMapper.selectOne(SystemRole::getSystemType, ROLE_TYPE_SYSTEM, SystemRole::getOrganizationId, systemOrganizationMenuCreateDTO.getOrganizationId());
            LambdaPrivernalWrapper<SystemRole> wrapper = new LambdaPrivernalWrapper<SystemRole>();
            wrapper.eq(SystemRole::getSystemType, DataConstants.DATA_TYPE_SYSTEM).eq(SystemRole::getRoleKey, RoleConstants.SUPER_ROLE_ADMIN)
                    .eq(SystemRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT)
                    .eq(SystemRole::getOrganizationId, systemOrganizationMenuCreateDTO.getOrganizationId());
            systemRole = systemRoleMapper.selectOne(wrapper);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

        Assert.notNull(systemRole, "组织管理员角色不存在");
        String roleId = systemRole.getId();

        List<String> addIds;
        List<SystemOrganizationMenuDTO> removeIds = new ArrayList<>();
        if (oldSystemMenuList != null && !oldSystemMenuList.isEmpty()) {
            List<String> oldIds = oldSystemMenuList.stream().map(SystemOrganizationMenuDTO::getMenuId).toList();
            // 去掉已经添加的菜单
            addIds = list.stream().filter(item -> !oldIds.contains(item)).toList();
            //删除去掉的授权
            removeIds = oldSystemMenuList.stream().filter(item -> !list.contains(item.getMenuId())).toList();
        } else {
            addIds = list;
        }

        if (!removeIds.isEmpty()) {
            // 需要删除的菜单
            try {
                // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                OrganizationContextHolder.setIgnore(true);
                removeIds.forEach(item -> systemOrganizationMenuMapper.deleteById(item.getId()));

                List<String> delMenuIds = removeIds.stream().map(SystemOrganizationMenuDTO::getMenuId).toList();
                // 删除掉此组织的角色，授权的菜单关系。
                systemRoleMenuMapper.deleteByOrganizationAndMenu(systemOrganizationMenuCreateDTO.getOrganizationId(), systemOrganizationMenuCreateDTO.getAppId(), delMenuIds);

            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }

        // 需要新增的功能
        List<SystemMenuDTO> listMenu = systemOrganizationMenuCreateDTO.getMenuDTOList().stream().filter(item -> addIds.contains(item.getId())).toList();

        listMenu.forEach(item -> {
            SystemOrganizationMenu systemOrganizationMenu = new SystemOrganizationMenu();
            systemOrganizationMenu.setOrganizationId(systemOrganizationMenuCreateDTO.getOrganizationId());
            systemOrganizationMenu.setMenuId(item.getId());
            systemOrganizationMenu.setStatus(WorkflowDataStatus.FINISH.getCode());
            systemOrganizationMenuMapper.insert(systemOrganizationMenu);

            //给默认的角色，增加这些菜单授权

            SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
            systemRoleMenu.setMenuId(item.getId());
            systemRoleMenu.setRoleId(roleId);
            systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
            systemRoleMenu.setPermission(item.getPermission());
            systemRoleMenu.setAppId(systemOrganizationMenuCreateDTO.getAppId());
            systemRoleMenuMapper.insert(systemRoleMenu);

        });
        return addIds.size();
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int saveSystemOrganizationApp(SystemOrganizationAppCreateDTO systemOrganizationAppCreateDTO) {

        List<SystemOrganizationAppDTO> appList = systemOrganizationAppCreateDTO.getAppList();

        if (appList != null && !appList.isEmpty()) {
            SystemRole systemRole = null;
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                OrganizationContextHolder.setIgnore(true);

                // 查询该组织唯一的系统级的角色
                LambdaPrivernalWrapper<SystemRole> wrapper = new LambdaPrivernalWrapper<SystemRole>();
                wrapper.eq(SystemRole::getSystemType, DataConstants.DATA_TYPE_SYSTEM).eq(SystemRole::getRoleKey, RoleConstants.SUPER_ROLE_ADMIN)
                        .eq(SystemRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT)
                        .eq(SystemRole::getOrganizationId, systemOrganizationAppCreateDTO.getOrganizationId());
                systemRole = systemRoleMapper.selectOne(wrapper);
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }

            SystemRole finalSystemRole = systemRole;
            appList.forEach(item -> {
                if (DataConstants.ACTION_TAG_ADD.equals(item.getActionTag())) {
                    SystemOrganizationApp systemOrganizationApp = new SystemOrganizationApp();
                    systemOrganizationApp.setOrganizationId(systemOrganizationAppCreateDTO.getOrganizationId());
                    systemOrganizationApp.setAppId(item.getAppId());
                    systemOrganizationApp.setStatus(WorkflowDataStatus.FINISH.getCode());

                    systemOrganizationApp.setId(null);
                    systemOrganizationApp.setCreatedBy(null);
                    systemOrganizationApp.setCreatedTime(new Date());
                    systemOrganizationAppMapper.insert(systemOrganizationApp);
                    // 增加组织该应用默认拥有的菜单
                    List<SystemAppMenuDTO> systemAppMenuList = systemAppMenuMapper.selectSystemAppMenuListByAppId(item.getAppId());

                    for (SystemAppMenuDTO systemAppMenu : systemAppMenuList) {
                        SystemOrganizationMenu systemOrganizationMenu = new SystemOrganizationMenu();
                        systemOrganizationMenu.setOrganizationId(systemOrganizationAppCreateDTO.getOrganizationId());
                        systemOrganizationMenu.setMenuId(systemAppMenu.getMenuId());
                        systemOrganizationMenu.setStatus(WorkflowDataStatus.FINISH.getCode());
                        systemOrganizationMenu.setAppId(systemAppMenu.getAppId());
                        systemOrganizationMenuMapper.insert(systemOrganizationMenu);

                        //给默认的角色，增加这些菜单授权
                        SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                        systemRoleMenu.setMenuId(systemAppMenu.getMenuId());
                        systemRoleMenu.setRoleId(finalSystemRole.getId());
                        systemRoleMenu.setAppId(systemAppMenu.getAppId());
                        systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                        systemRoleMenu.setPermission(systemAppMenu.getPermission());
                        systemRoleMenuMapper.insert(systemRoleMenu);
                    }

                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);

                        // 增加应用的公共角色，流程审批使用
                        List<SystemCommonGroupDTO> commonGroups = systemCommonGroupMapper.selectSystemCommonGroupListByAppId(item.getAppId());

                        if (commonGroups != null && !commonGroups.isEmpty()) {
                            commonGroups.forEach(commonGroup -> {
                                //是角色的类型，创建角色
                                if (RoleConstants.GROUP_TYPE_ROLE.equals(commonGroup.getGroupType())) {
                                    SystemRole systemRoleNew = new SystemRole();
                                    systemRoleNew.setOrganizationId(systemOrganizationAppCreateDTO.getOrganizationId());
                                    systemRoleNew.setCreatedBy(null);
                                    systemRoleNew.setDepartmentId(systemOrganizationAppCreateDTO.getOrganizationId());
                                    systemRoleNew.setSystemType(DataConstants.DATA_TYPE_SYSTEM);
                                    systemRoleNew.setRoleKey(commonGroup.getCode());
                                    systemRoleNew.setRoleName(commonGroup.getName());
                                    systemRoleNew.setStatus(WorkflowDataStatus.FINISH.getCode());
                                    systemRoleNew.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                                    systemRoleNew.setRemark(commonGroup.getRemark());
                                    systemRoleMapper.insert(systemRoleNew);

                                    // 公共角色有的权限，要同时带过来
                                    SystemCommonGroupMenuFilterDTO filterDTO = new SystemCommonGroupMenuFilterDTO();
                                    filterDTO.setCommonGroupId(commonGroup.getId());

                                    List<SystemCommonGroupMenuDTO> systemCommonGroupMenuList = systemCommonGroupMenuMapper.selectCommGroupMenuListByCommonGroupId(filterDTO);
                                    if (systemCommonGroupMenuList != null && !systemCommonGroupMenuList.isEmpty()) {

                                        systemCommonGroupMenuList.forEach(commonGroupMenu -> {
                                            // 配置的公共组里的菜单是可显示的，才会加到角色里。
                                            if(commonGroupMenu.getVisibleMenu() != null && commonGroupMenu.getVisibleMenu() == DataConstants.DATA_STATUS_YES.intValue()) {
                                                SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                                                systemRoleMenu.setMenuId(commonGroupMenu.getMenuId());
                                                systemRoleMenu.setRoleId(systemRoleNew.getId());
                                                systemRoleMenu.setAppId(item.getAppId());
                                                systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                                                systemRoleMenu.setPermission(commonGroupMenu.getPermission());
                                                systemRoleMenuMapper.insert(systemRoleMenu);
                                            }
                                        });
                                    }
                                }

                                if (RoleConstants.GROUP_TYPE_USER_GROUP.equals(commonGroup.getGroupType())) {

                                    SystemUserGroup systemUserGroup = new SystemUserGroup();
                                    systemUserGroup.setOrganizationId(systemOrganizationAppCreateDTO.getOrganizationId());
                                    systemUserGroup.setCreatedBy(null);
                                    systemUserGroup.setDepartmentId(systemOrganizationAppCreateDTO.getOrganizationId());
                                    systemUserGroup.setGroupType(DataConstants.DATA_TYPE_SYSTEM);
                                    systemUserGroup.setGroupCode(commonGroup.getCode());
                                    systemUserGroup.setGroupName(commonGroup.getName());
                                    systemUserGroup.setCommonGroupId(commonGroup.getId());
                                    systemUserGroup.setStatus(WorkflowDataStatus.FINISH.getCode());
                                    systemUserGroup.setRemark(commonGroup.getRemark());

                                    systemUserGroupMapper.insert(systemUserGroup);
                                }
                            });
                        }

                    } finally {
                        OrganizationContextHolder.setIgnore(oldIgnore);
                        OrganizationContextHolder.clear();
                    }

                }
                if (DataConstants.ACTION_TAG_DELETE.equals(item.getActionTag())) {
                    // 删除应用，需要删除该组织该应用下的菜单
                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);

                        LambdaPrivernalWrapper<SystemOrganizationApp> wrapper = new LambdaPrivernalWrapper<>();
                        wrapper.eq(SystemOrganizationApp::getOrganizationId, item.getOrganizationId())
                                .eq(SystemOrganizationApp::getAppId, item.getAppId())
                                .eq(SystemOrganizationApp::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);

                        SystemOrganizationApp systemOrganizationApp = systemOrganizationAppMapper.selectOne(wrapper);

                        systemOrganizationAppMapper.deleteFillById(systemOrganizationApp.getId());

                        List<SystemOrganizationMenuDTO> removeIds = systemOrganizationMenuMapper.selectSystemOrganizationMenuListByAppId(systemOrganizationAppCreateDTO.getOrganizationId(), item.getAppId());

                        //删除组织关联的菜单
                        removeIds.forEach(item1 -> {
                            systemOrganizationMenuMapper.deleteFillById(item1.getId());
                        });

                        List<String> delMenuIds = removeIds.stream().map(SystemOrganizationMenuDTO::getMenuId).toList();
                        // 删除掉此组织的角色，授权的菜单关系。
                        if (!delMenuIds.isEmpty()) {
                            systemRoleMenuMapper.deleteByOrganizationAndMenu(systemOrganizationAppCreateDTO.getOrganizationId(), item.getAppId(), delMenuIds);
                        }
                        // 删除此应用的公共角色，及角色有的人员、菜单、人员附带的服务部门

                        // 增加应用的公共角色，流程审批使用
                        List<SystemCommonGroupDTO> commonGroups = systemCommonGroupMapper.selectSystemCommonGroupListByAppId(item.getAppId());

                        if (commonGroups != null && !commonGroups.isEmpty()) {
                            commonGroups.forEach(commonGroup -> {
                                //是角色的类型，创建角色
                                if (RoleConstants.GROUP_TYPE_ROLE.equals(commonGroup.getGroupType())) {

                                    LambdaPrivernalWrapper<SystemRole> lambda = new LambdaPrivernalWrapper<>();
                                    lambda.eq(SystemRole::getRoleKey, commonGroup.getCode())
                                            .eq(SystemRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT)
                                            .eq(SystemRole::getSystemType, DataConstants.DATA_TYPE_SYSTEM);
                                    List<SystemRole> systemRoleList = systemRoleMapper.selectList(lambda);
                                    if (systemRoleList != null && !systemRoleList.isEmpty()) {
                                        systemRoleList.forEach(systemRoleTemp -> {
                                            // 删除角色里的人员
                                            List<SystemRoleUser> roleUsers = systemRoleUserMapper.selectList(SystemRoleUser::getRoleId, systemRoleTemp.getId(), SystemRoleUser::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
                                            for (SystemRoleUser roleUser : roleUsers) {
                                                systemRoleUserMapper.deleteFillById(roleUser.getId());
                                                List<SystemRoleUserDept> roleUserDepts = systemRoleUserDeptMapper.selectSystemRoleUserDeptByRoleUserId(roleUser.getId());
                                                if (roleUserDepts != null && !roleUserDepts.isEmpty()) {
                                                    for (SystemRoleUserDept userDept : roleUserDepts) {
                                                        systemRoleUserDeptMapper.deleteFillById(userDept.getId());
                                                    }
                                                }
                                            }
                                            // 删除角色里的菜单
                                            List<SystemRoleMenu> userAppMenuList = systemRoleMenuMapper.selectList(SystemRoleMenu::getRoleId, systemRoleTemp.getId(), SystemRoleMenu::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
                                            if (userAppMenuList != null && !userAppMenuList.isEmpty()) {
                                                for (SystemRoleMenu groupUser : userAppMenuList) {
                                                    systemRoleMenuMapper.deleteFillById(groupUser.getId());
                                                }
                                            }
                                            // 删除角色组里的角色
                                            List<SystemRoleGroupRole> roleGroupRoleList = systemRoleGroupRoleMapper.selectList(SystemRoleGroupRole::getRoleId, systemRoleTemp.getId(), SystemRoleGroupRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
                                            if (roleGroupRoleList != null && !roleGroupRoleList.isEmpty()) {
                                                for (SystemRoleGroupRole groupUser : roleGroupRoleList) {
                                                    systemRoleGroupRoleMapper.deleteFillById(groupUser.getId());
                                                }
                                            }
                                            systemRoleMapper.deleteFillById(systemRoleTemp.getId());

                                        });
                                    }

                                }

                                if (RoleConstants.GROUP_TYPE_USER_GROUP.equals(commonGroup.getGroupType())) {

                                    List<SystemUserGroup> systemUserGroupList = systemUserGroupMapper.selectList(new LambdaPrivernalWrapper<SystemUserGroup>().eq(SystemUserGroup::getGroupCode, commonGroup.getCode())
                                            .eq(SystemUserGroup::getCommonGroupId, commonGroup.getId())
                                            .eq(SystemUserGroup::getDepartmentId, systemOrganizationAppCreateDTO.getOrganizationId())
                                            .eq(SystemUserGroup::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT)
                                    );

                                    systemUserGroupList.forEach(systemUserGroupTemp -> {
                                        systemUserGroupMapper.deleteFillById(systemUserGroupTemp.getId());
                                    });
                                }
                            });
                        }

                    } finally {
                        OrganizationContextHolder.setIgnore(oldIgnore);
                        OrganizationContextHolder.clear();
                    }

                }
                if (DataConstants.ACTION_TAG_EDIT.equals(item.getActionTag())) {

                    // 需要删除的菜单
                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);
                        SystemOrganizationApp systemAppMenu = systemOrganizationAppMapper.selectById(item.getId());
                        systemAppMenu.setStatus(WorkflowDataStatus.FINISH.getCode());
                        systemAppMenu.setRemark(item.getRemark());
                        systemOrganizationAppMapper.updateById(systemAppMenu);

                    } finally {
                        OrganizationContextHolder.setIgnore(oldIgnore);
                        OrganizationContextHolder.clear();
                    }
                }

            });
            return appList.size();
        }
        return 0;
    }
}
