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

import com.privernal.common.core.constant.MenuConstants;
import com.privernal.common.core.constant.RoleConstants;
import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.*;
import com.privernal.system.dto.*;
import com.privernal.system.filter.SystemAppInfoFilterDTO;
import com.privernal.system.filter.SystemCommonGroupMenuFilterDTO;
import com.privernal.system.mapper.*;
import com.privernal.system.service.ISystemAppInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 应用信息的Service业务层处理
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用信息的Service业务层处理
 */
@Slf4j
@Service
public class SystemAppInfoServiceImpl implements ISystemAppInfoService {
    @Autowired
    private SystemAppInfoMapper systemAppInfoMapper;

    @org.springframework.context.annotation.Lazy
    @Autowired
    private com.privernal.job.api.ScheduleTaskClient scheduleTaskClient;

    @Autowired
    private SystemAppCommonGroupMapper systemAppCommonGroupMapper;

    @Autowired
    private SystemAppMenuMapper systemAppMenuMapper;

    @Autowired
    private SystemAppPathMapper systemAppPathMapper;

    @Autowired
    private SystemOrganizationAppMapper systemOrganizationAppMapper;

    @Autowired
    private SystemRoleMenuMapper systemRoleMenuMapper;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired
    private SystemOrganizationMenuMapper systemOrganizationMenuMapper;

    @Autowired
    private SystemMenuMapper systemMenuMapper;

    @Autowired
    private SystemCommonGroupMapper systemCommonGroupMapper;

    @Autowired
    private SystemCommonGroupMenuMapper systemCommonGroupMenuMapper;
    @Autowired
    private SystemUserGroupMapper systemUserGroupMapper;

    @Autowired
    private SystemAppWorkflowMapper systemAppWorkflowMapper;

    /**
     * 查询应用信息
     *
     * @param id 应用信息主键
     * @return 应用信息
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppInfo selectSystemAppInfoById(String id) {
        return systemAppInfoMapper.selectById(id);
    }

    /**
     * 查询应用信息
     *
     * @param id 应用信息主键
     * @return 应用信息
     */
    @Transactional(readOnly = true)
    @Override
    public SystemAppInfoDetailDTO selectSystemAppInfoDetailDTOById(String id) {
        SystemAppInfo systemAppInfo = systemAppInfoMapper.selectById(id);
        SystemAppInfoDetailDTO systemAppInfoDetailDTO = new SystemAppInfoDetailDTO();
        if (systemAppInfo != null) {
            BeanUtils.copyProperties(systemAppInfo, systemAppInfoDetailDTO);
        }
        return systemAppInfoDetailDTO;
    }

    /**
     * 查询应用信息列表
     *
     * @param systemAppInfo 应用信息
     * @return 应用信息
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemAppInfoDTO> selectSystemAppInfoList(SystemAppInfoFilterDTO systemAppInfo) {
        return systemAppInfoMapper.selectSystemAppInfoList(systemAppInfo);
    }

    /**
     * 新增应用信息
     *
     * @param systemAppInfo 应用信息
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppInfo insertSystemAppInfo(SystemAppInfo systemAppInfo) {
        systemAppInfoMapper.insert(systemAppInfo);
        return systemAppInfo;
    }

    /**
     * 修改应用信息
     *
     * @param systemAppInfo 应用信息
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemAppInfo updateSystemAppInfo(SystemAppInfo systemAppInfo) {
        systemAppInfoMapper.updateById(systemAppInfo);
 // 应用下架级联暂停定时任务（第7条）：仓库原本没有下架钩子，
        // 这里按状态分支补挂点；暂停只动 READY 行，在途执行不受影响（§7.5）
        if (systemAppInfo.getId() != null && systemAppInfo.getStatus() != null && systemAppInfo.getStatus() == 0L) {
            try {
                com.privernal.common.datascope.context.OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
                scheduleTaskClient.pauseByApp(SecurityUtils.getOrganizationId(), systemAppInfo.getId());
            } catch (Exception e) {
                log.error("应用下架级联暂停定时任务失败 appId={}", systemAppInfo.getId(), e);
            } finally {
                com.privernal.common.datascope.context.OrganizationContextHolder.clear();
            }
        }
        return systemAppInfo;
    }

    @Override
    public SystemAppInfo saveSystemAppInfo(SystemAppInfoDTO systemAppInfoDTO) {
        SystemAppInfo systemAppInfo = new SystemAppInfo();
        BeanUtils.copyProperties(systemAppInfoDTO, systemAppInfo);
        // 应用编码唯一校验：未删除记录中 code 不允许重复（组织隔离条件由拦截器自动追加），编辑时排除自身。
        // 应用编码同时用作菜单编码与菜单路由 path，重复会导致菜单匹配混乱，必须保存前拦截。
        if (StringUtils.isNotEmpty(systemAppInfoDTO.getCode())) {
            LambdaPrivernalWrapper<SystemAppInfo> codeWrapper = new LambdaPrivernalWrapper<>();
            codeWrapper.eq(SystemAppInfo::getCode, systemAppInfoDTO.getCode())
                .eq(SystemAppInfo::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
            if (StringUtils.isNotEmpty(systemAppInfoDTO.getId())) {
                codeWrapper.ne(SystemAppInfo::getId, systemAppInfoDTO.getId());
            }
            Long codeCount = systemAppInfoMapper.selectCount(codeWrapper);
            if (codeCount != null && codeCount > 0) {
                throw new ServiceException(String.format("应用编码「%s」已存在，请更换编码后再保存", systemAppInfoDTO.getCode()));
            }
        }
        if (StringUtils.isNotEmpty(systemAppInfoDTO.getId())) {
            systemAppInfoMapper.updateById(systemAppInfo);
        } else {
            systemAppInfoMapper.insert(systemAppInfo);
        }

        // 动态表单。创建应用的时候，创建一个菜单的目录，与应用同名。后面再增加动态表单，则是此应用下的二级菜单。
        // 如果想增加目录，则去菜单里维护。
        String orgId = SecurityUtils.getOrganizationId();
        if (StringUtils.isNotEmpty(orgId) && StringUtils.isEmpty(systemAppInfoDTO.getId()) ) {

            // 给当前组织授权此应用
            SystemOrganizationApp systemOrganizationApp = new SystemOrganizationApp();
            systemOrganizationApp.setAppId(systemAppInfo.getId());
            systemOrganizationApp.setOrganizationId(orgId);
            systemOrganizationApp.setStatus(WorkflowDataStatus.FINISH.getCode());
            systemOrganizationAppMapper.insert(systemOrganizationApp);

            //增加一个菜单

            SystemMenu systemMenu = new SystemMenu();
            systemMenu.setMenuName(systemAppInfo.getAppName());
            systemMenu.setMenuCode(systemAppInfo.getCode());
            systemMenu.setMenuType(MenuConstants.TYPE_DIR);
            systemMenu.setComponent("basic");
            systemMenu.setPath("/" + systemAppInfo.getCode());
            systemMenu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
            systemMenu.setParentId("0");
            systemMenu.setCreatedBy(SecurityUtils.getUserId());
            systemMenu.setCreatedTime(new Date());
            systemMenu.setOrderNum(20L);
            systemMenu.setVisible(1);
            systemMenu.setPermission("system:"+systemAppInfo.getCode()+":list");
            systemMenuMapper.insert(systemMenu);

            // 给组织增加菜单
            SystemOrganizationMenu systemOrganizationMenu = new SystemOrganizationMenu();
            systemOrganizationMenu.setOrganizationId(orgId);
            systemOrganizationMenu.setMenuId(systemMenu.getId());
            systemOrganizationMenu.setStatus(WorkflowDataStatus.FINISH.getCode());
            systemOrganizationMenu.setAppId(systemAppInfo.getId());
            systemOrganizationMenuMapper.insert(systemOrganizationMenu);

            // 查询该组织唯一的系统级的角色
            LambdaPrivernalWrapper<SystemRole> wrapper = new LambdaPrivernalWrapper<SystemRole>();
            wrapper.eq(SystemRole::getSystemType, DataConstants.DATA_TYPE_SYSTEM).eq(SystemRole::getRoleKey, RoleConstants.SUPER_ROLE_ADMIN).eq(SystemRole::getOrganizationId, orgId);
            wrapper.eq(SystemRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
            SystemRole systemRole = systemRoleMapper.selectOne(wrapper);

            //给默认的角色，增加这些菜单授权 todo 只给组织管理员角色增加了，并未给所有角色增加此新的菜单。需要手动增加。
            // 组织没有系统级管理员角色时跳过授权而不是 NPE：应用与菜单已创建成功，不能让整个保存事务回滚
            if (systemRole == null) {
                log.warn("组织 {} 没有系统级的组织管理员角色，应用目录菜单 {} 已创建但未自动授权", orgId, systemMenu.getMenuCode());
            } else {
                SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                systemRoleMenu.setMenuId(systemMenu.getId());
                systemRoleMenu.setRoleId(systemRole.getId());
                systemRoleMenu.setAppId(systemAppInfo.getId());
                systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                systemRoleMenu.setPermission(systemMenu.getPermission());
                systemRoleMenuMapper.insert(systemRoleMenu);
            }

            // 目录菜单关联应用（system_app_menu），否则应用路由查不到此目录
            SystemAppMenu dirAppMenu = new SystemAppMenu();
            dirAppMenu.setAppId(systemAppInfo.getId());
            dirAppMenu.setMenuId(systemMenu.getId());
            dirAppMenu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
            dirAppMenu.setOrganizationId(orgId);
            dirAppMenu.setMustMenu(0);
            dirAppMenu.setVisibleMenu(1);
            systemAppMenuMapper.insert(dirAppMenu);
        }

        // 处理关联的菜单，动态表单也不需要下面的逻辑。
        List<SystemAppMenuDTO> list = systemAppInfoDTO.getMenuList();
        if (list != null && !list.isEmpty()) {
            for (SystemAppMenuDTO systemAppMenuDTO : list) {

                SystemAppMenu systemAppMenu = new SystemAppMenu();
                BeanUtils.copyProperties(systemAppMenuDTO, systemAppMenu);

                if (DataConstants.ACTION_TAG_ADD.equals(systemAppMenuDTO.getActionTag())) {
                    systemAppMenu.setId(null);
                    systemAppMenu.setCreatedBy(null);
                    systemAppMenu.setCreatedTime(new Date());
                    systemAppMenu.setAppId(systemAppInfo.getId());
                    systemAppMenuMapper.insert(systemAppMenu);
                    // 给所有有此应用的组织增加新的功能菜单

                    //查询有此应用的组织，然后给这些组织增加菜单
                    Boolean oldIgnore = OrganizationContextHolder.isIgnore();
                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);

                        LambdaPrivernalWrapper<SystemOrganizationApp> lambdaQueryWrapper = new LambdaPrivernalWrapper<>();
                        lambdaQueryWrapper.eq(SystemOrganizationApp::getAppId, systemAppInfo.getId());
                        lambdaQueryWrapper.eq(SystemOrganizationApp::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
                        List<SystemOrganizationApp> systemOrganizationApps = systemOrganizationAppMapper.selectList(lambdaQueryWrapper);

                        // 拥有此应用的所有的组织，给他们加新增的菜单功能
                        for (SystemOrganizationApp systemOrganizationApp : systemOrganizationApps) {
                            String organizationId = systemOrganizationApp.getOrganizationId();
                            if (StringUtils.isNotEmpty(organizationId)) {
                                // 给组织增加菜单
                                SystemOrganizationMenu systemOrganizationMenu = new SystemOrganizationMenu();
                                systemOrganizationMenu.setOrganizationId(organizationId);
                                systemOrganizationMenu.setMenuId(systemAppMenu.getMenuId());
                                systemOrganizationMenu.setStatus(WorkflowDataStatus.FINISH.getCode());
                                systemOrganizationMenu.setAppId(systemAppMenu.getAppId());
                                systemOrganizationMenuMapper.insert(systemOrganizationMenu);

                                // 查询该组织唯一的系统级的角色
                                LambdaPrivernalWrapper<SystemRole> wrapper = new LambdaPrivernalWrapper<SystemRole>();
                                wrapper.eq(SystemRole::getSystemType, DataConstants.DATA_TYPE_SYSTEM).eq(SystemRole::getRoleKey, RoleConstants.SUPER_ROLE_ADMIN).eq(SystemRole::getOrganizationId, organizationId);
                                wrapper.eq(SystemRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
                                SystemRole systemRole = systemRoleMapper.selectOne(wrapper);

                                SystemMenu systemMenu = systemMenuMapper.selectById(systemAppMenu.getMenuId());

                                //给默认的角色，增加这些菜单授权 todo 只给组织管理员角色增加了，并未给所有角色增加此新的菜单。需要手动增加。
                                SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                                systemRoleMenu.setMenuId(systemAppMenu.getMenuId());
                                systemRoleMenu.setRoleId(systemRole.getId());
                                systemRoleMenu.setAppId(systemAppMenu.getAppId());
                                systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                                systemRoleMenu.setPermission(systemMenu.getPermission());
                                systemRoleMenuMapper.insert(systemRoleMenu);
                            }

                        }

                    } finally {
                        OrganizationContextHolder.setIgnore(oldIgnore);
                        OrganizationContextHolder.clear();
                    }
                }
                if (DataConstants.ACTION_TAG_DELETE.equals(systemAppMenuDTO.getActionTag())) {

                    // 删除应用关联的菜单
                    systemAppMenuMapper.deleteFillById(systemAppMenu.getId());
                    // 删除所有已经授权的应用的菜单
                    //查询有此应用的组织，然后给这些组织增加菜单
                    Boolean oldIgnore = OrganizationContextHolder.isIgnore();
                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);

                        // 删除所有组织的此功能菜单
                        systemOrganizationMenuMapper.deleteAllOrganizationMenuByAppIdAndMenuId(systemAppInfo.getId(), Collections.singletonList(systemAppMenuDTO.getMenuId()));

                        systemRoleMenuMapper.deleteAllRoleMenuByMenuId(systemAppInfo.getId(), Collections.singletonList(systemAppMenuDTO.getMenuId()));

                    } finally {
                        OrganizationContextHolder.setIgnore(oldIgnore);
                        OrganizationContextHolder.clear();
                    }

                }
                if (DataConstants.ACTION_TAG_EDIT.equals(systemAppMenuDTO.getActionTag())) {
                    systemAppMenuMapper.updateById(systemAppMenu);
                }
            }
        }

        // 动态表单的话，下面也不需要了。因为流程依赖公共组，所以应用要同步公共组。
        List<SystemAppCommonGroupDTO> groupList = systemAppInfoDTO.getGroupList();
        if (groupList != null && !groupList.isEmpty()) {
            for (SystemAppCommonGroupDTO systemAppCommonGroupDTO : groupList) {

                SystemAppCommonGroup systemAppCommonGroup = new SystemAppCommonGroup();
                BeanUtils.copyProperties(systemAppCommonGroupDTO, systemAppCommonGroup);

                if (DataConstants.ACTION_TAG_ADD.equals(systemAppCommonGroupDTO.getActionTag())) {
                    systemAppCommonGroup.setId(null);
                    systemAppCommonGroup.setCreatedBy(null);
                    systemAppCommonGroup.setCreatedTime(new Date());
                    systemAppCommonGroup.setAppId(systemAppInfo.getId());
                    systemAppCommonGroupMapper.insert(systemAppCommonGroup);
                    // 给所有有此应用的组织增加新的功能菜单

                    //查询有此应用的组织，然后给这些组织增加菜单,
                    // todo 考虑要查询有哪些项目，造成依赖混乱。所以此处只增加共享的公共组。各个项目上不会创建此组
                    Boolean oldIgnore = OrganizationContextHolder.isIgnore();
                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);

                        // 给有此应用的组织增加角色或用户组
                        List<SystemOrganizationApp> systemOrganizationApps = systemOrganizationAppMapper.selectList(SystemOrganizationApp::getAppId, systemAppInfo.getId());

                        // 拥有此应用的所有的组织，给他们加新增的菜单功能
                        for (SystemOrganizationApp systemOrganizationApp : systemOrganizationApps) {
                            String organizationId = systemOrganizationApp.getOrganizationId();
                            if (StringUtils.isNotEmpty(organizationId)) {

                                // 查询此组织有哪些项目
                                // 给组织加用户组
                                SystemCommonGroup systemCommonGroup = systemCommonGroupMapper.selectById(systemAppCommonGroupDTO.getCommonGroupId());
                                if (systemCommonGroup != null) {
                                    if (RoleConstants.GROUP_TYPE_ROLE.equals(systemCommonGroup.getGroupType())) {
                                        //角色
                                        SystemRole systemRoleNew = new SystemRole();
                                        systemRoleNew.setOrganizationId(systemOrganizationApp.getOrganizationId());
                                        systemRoleNew.setCreatedBy(null);
                                        systemRoleNew.setDepartmentId(systemOrganizationApp.getOrganizationId());
                                        systemRoleNew.setSystemType(DataConstants.DATA_TYPE_SYSTEM);
                                        systemRoleNew.setRoleKey(systemCommonGroup.getCode());
                                        systemRoleNew.setRoleName(systemCommonGroup.getName());
                                        systemRoleNew.setStatus(WorkflowDataStatus.FINISH.getCode());
                                        systemRoleNew.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                                        systemRoleNew.setRemark(systemCommonGroup.getRemark());
                                        systemRoleMapper.insert(systemRoleNew);

                                        // 公共角色有的权限，要同时带过来
                                        SystemCommonGroupMenuFilterDTO filterDTO = new SystemCommonGroupMenuFilterDTO();
                                        filterDTO.setCommonGroupId(systemCommonGroup.getId());

                                        List<SystemCommonGroupMenuDTO> systemCommonGroupMenuList = systemCommonGroupMenuMapper.selectCommGroupMenuListByCommonGroupId(filterDTO);
                                        if (systemCommonGroupMenuList != null && !systemCommonGroupMenuList.isEmpty()) {

                                            systemCommonGroupMenuList.forEach(commonGroupMenu -> {
                                                // 配置的公共组里的菜单是可显示的，才会加到角色里。
                                                if (commonGroupMenu.getVisibleMenu() != null && commonGroupMenu.getVisibleMenu() == DataConstants.DATA_STATUS_YES.intValue()) {
                                                    SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                                                    systemRoleMenu.setMenuId(commonGroupMenu.getMenuId());
                                                    systemRoleMenu.setRoleId(systemRoleNew.getId());
                                                    systemRoleMenu.setAppId(systemOrganizationApp.getAppId());
                                                    systemRoleMenu.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                                                    systemRoleMenu.setPermission(commonGroupMenu.getPermission());
                                                    systemRoleMenuMapper.insert(systemRoleMenu);
                                                }
                                            });
                                        }
                                    }

                                    if (RoleConstants.GROUP_TYPE_USER_GROUP.equals(systemCommonGroup.getGroupType())) {
                                        //用户组
                                        SystemUserGroup systemUserGroup = new SystemUserGroup();
                                        systemUserGroup.setOrganizationId(systemOrganizationApp.getOrganizationId());
                                        systemUserGroup.setCreatedBy(null);
                                        systemUserGroup.setDepartmentId(systemOrganizationApp.getOrganizationId());
                                        systemUserGroup.setGroupType(DataConstants.DATA_TYPE_SYSTEM);
                                        systemUserGroup.setGroupCode(systemCommonGroup.getCode());
                                        systemUserGroup.setGroupName(systemCommonGroup.getName());
                                        systemUserGroup.setCommonGroupId(systemCommonGroup.getId());
                                        systemUserGroup.setStatus(WorkflowDataStatus.FINISH.getCode());
                                        systemUserGroup.setRemark(systemCommonGroup.getRemark());

                                        systemUserGroupMapper.insert(systemUserGroup);
                                    }
                                }

                            }

                        }

                    } finally {
                        OrganizationContextHolder.setIgnore(oldIgnore);
                        OrganizationContextHolder.clear();
                    }
                }
                if (DataConstants.ACTION_TAG_DELETE.equals(systemAppCommonGroupDTO.getActionTag())) {
                    // 删除应用关联的菜单
                    systemAppCommonGroupMapper.deleteFillById(systemAppCommonGroup.getId());
                }
                if (DataConstants.ACTION_TAG_EDIT.equals(systemAppCommonGroupDTO.getActionTag())) {
                    systemAppCommonGroupMapper.updateById(systemAppCommonGroup);
                }
            }
        }

        // 如果是动态表单，则下面的逻辑不需要了。start
        List<SystemAppWorkflowDTO> workflowList = systemAppInfoDTO.getWorkflowList();
        if (workflowList != null && !workflowList.isEmpty()) {
            for (SystemAppWorkflowDTO systemAppWorkflowDTO : workflowList) {

                SystemAppWorkflow systemAppWorkflow = new SystemAppWorkflow();
                BeanUtils.copyProperties(systemAppWorkflowDTO, systemAppWorkflow);

                if (DataConstants.ACTION_TAG_ADD.equals(systemAppWorkflowDTO.getActionTag())) {
                    systemAppWorkflow.setId(null);
                    systemAppWorkflow.setCreatedBy(null);
                    systemAppWorkflow.setCreatedTime(new Date());
                    systemAppWorkflow.setAppId(systemAppInfo.getId());
                    systemAppWorkflowMapper.insert(systemAppWorkflow);
                    // 给所有有此应用的组织增加新的流程表单
                    // todo 不能反向依赖流程引擎，所以不直接创建流程表单
                }
                if (DataConstants.ACTION_TAG_DELETE.equals(systemAppWorkflowDTO.getActionTag())) {
                    // 删除应用关联的流程
                    systemAppWorkflowMapper.deleteFillById(systemAppWorkflowDTO.getId());
                }
                if (DataConstants.ACTION_TAG_EDIT.equals(systemAppWorkflowDTO.getActionTag())) {
                    systemAppWorkflowMapper.updateById(systemAppWorkflow);
                }
            }
        }
        // end

        return systemAppInfo;
    }

    /**
     * 批量删除应用信息
     *
     * @param ids 需要删除的应用信息主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppInfoByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemAppInfoById(id);
        }
        return ids.length;
    }

    /**
     * 删除应用信息信息
     *
     * @param id 应用信息主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemAppInfoById(String id) {
        // 删除应用关联的公共组
        LambdaPrivernalWrapper<SystemAppCommonGroup> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(SystemAppCommonGroup::getAppId, id);
        wrapper.eq(SystemAppCommonGroup::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        List<SystemAppCommonGroup> userAppMenuList = systemAppCommonGroupMapper.selectList(wrapper);
        if (userAppMenuList != null && !userAppMenuList.isEmpty()) {
            for (SystemAppCommonGroup groupUser : userAppMenuList) {
                systemAppCommonGroupMapper.deleteFillById(groupUser.getId());
            }
        }

        // 删除应用关联的菜单
        LambdaPrivernalWrapper<SystemAppMenu> wrapper1 = new LambdaPrivernalWrapper<>();
        wrapper1.eq(SystemAppMenu::getAppId, id);
        wrapper1.eq(SystemAppMenu::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        List<SystemAppMenu> systemAppMenuList = systemAppMenuMapper.selectList(wrapper1);
        if (systemAppMenuList != null && !systemAppMenuList.isEmpty()) {
            for (SystemAppMenu groupUser : systemAppMenuList) {
                systemAppMenuMapper.deleteFillById(groupUser.getId());
            }
        }

        // 删除应用关联的PATH
        LambdaPrivernalWrapper<SystemAppPath> wrapper2 = new LambdaPrivernalWrapper<>();
        wrapper2.eq(SystemAppPath::getAppId, id);
        wrapper2.eq(SystemAppPath::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        List<SystemAppPath> systemAppPathList = systemAppPathMapper.selectList(wrapper2);
        if (systemAppPathList != null && !systemAppPathList.isEmpty()) {
            for (SystemAppPath groupUser : systemAppPathList) {
                systemAppPathMapper.deleteFillById(groupUser.getId());
            }
        }

        return systemAppInfoMapper.deleteFillById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemAppInfoDTO> selectSystemAppInfoListByUserId(String organizationId, String userId) {
        if (SecurityUtils.isSuperPlatformRoleUser() || SecurityUtils.isSuperRoleUser()) {
            // 如果是组织管理员或者平台管理员，则直接查询此组织拥有的所有菜单，去掉部门和角色权限的限制
            // 在初次创建完组织后，要给创建的默认角色授权功能，这时不特殊处理，是查询不到组织有的功能的。
            return systemAppInfoMapper.selectSystemAppInfoListByOrganizationId(organizationId);
        } else {
            return systemAppInfoMapper.selectSystemAppInfoListByOrganizationIdAndUserId(organizationId, userId);
        }
    }

    /**
     * 统计组织内非系统应用（type != 'SYSTEM'，即个人/业务创建的应用）数量
     */
    @Transactional(readOnly = true)
    @Override
    public Long selectCustomAppNumByOrganizationId(String organizationId) {
        return systemAppInfoMapper.selectCustomAppNumByOrganizationId(organizationId);
    }
}
