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
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.system.domain.*;
import com.privernal.system.dto.SystemCommonGroupDTO;
import com.privernal.system.dto.SystemCommonGroupDetailDTO;
import com.privernal.system.dto.SystemCommonGroupMenuDTO;
import com.privernal.system.filter.SystemCommonGroupFilterDTO;
import com.privernal.system.mapper.*;
import com.privernal.system.service.ISystemCommonGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 公共组[用户或角色]的Service业务层处理
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 公共组[用户或角色]的Service业务层处理
 */
@Slf4j
@Service
public class SystemCommonGroupServiceImpl implements ISystemCommonGroupService {
    @Autowired
    private SystemCommonGroupMapper systemCommonGroupMapper;

    @Autowired
    private SystemAppCommonGroupMapper systemAppCommonGroupMapper;

    @Autowired
    private SystemCommonGroupMenuMapper systemCommonGroupMenuMapper;

    @Autowired
    private SystemRoleMenuMapper systemRoleMenuMapper;

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired
    private SystemMenuMapper systemMenuMapper;

    /**
     * 查询公共组[用户或角色]
     *
     * @param id 公共组[用户或角色]主键
     * @return 公共组[用户或角色]
     */
    @Transactional(readOnly = true)
    @Override
    public SystemCommonGroup selectSystemCommonGroupById(String id) {
        return systemCommonGroupMapper.selectById(id);
    }

    /**
     * 查询公共组[用户或角色]
     *
     * @param id 公共组[用户或角色]主键
     * @return 公共组[用户或角色]
     */
    @Transactional(readOnly = true)
    @Override
    public SystemCommonGroupDetailDTO selectSystemCommonGroupDetailDTOById(String id) {
        SystemCommonGroup systemCommonGroup = systemCommonGroupMapper.selectById(id);
        SystemCommonGroupDetailDTO systemCommonGroupDetailDTO = new SystemCommonGroupDetailDTO();
        if (systemCommonGroup != null) {
            BeanUtils.copyProperties(systemCommonGroup, systemCommonGroupDetailDTO);
        }
        return systemCommonGroupDetailDTO;
    }

    /**
     * 查询公共组[用户或角色]列表
     *
     * @param systemCommonGroup 公共组[用户或角色]
     * @return 公共组[用户或角色]
     */

    @Transactional(readOnly = true)
    @Override
    public List<SystemCommonGroupDTO> selectSystemCommonGroupList(SystemCommonGroupFilterDTO systemCommonGroup) {
        return systemCommonGroupMapper.selectSystemCommonGroupList(systemCommonGroup);
    }

    /**
     * 新增公共组[用户或角色]
     *
     * @param systemCommonGroup 公共组[用户或角色]
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemCommonGroup insertSystemCommonGroup(SystemCommonGroup systemCommonGroup) {
        if (!checkCodeUnique(systemCommonGroup.getCode(), systemCommonGroup.getId())) {
            throw new ServiceException("公共组编码已存在");
        }
        systemCommonGroupMapper.insert(systemCommonGroup);
        return systemCommonGroup;
    }

    /**
     * 修改公共组[用户或角色]
     *
     * @param systemCommonGroup 公共组[用户或角色]
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemCommonGroup updateSystemCommonGroup(SystemCommonGroup systemCommonGroup) {
        if (!checkCodeUnique(systemCommonGroup.getCode(), systemCommonGroup.getId())) {
            throw new ServiceException("公共组编码已存在");
        }
        systemCommonGroupMapper.updateById(systemCommonGroup);
        return systemCommonGroup;
    }

    /**
     * 新增或修改公共组信息
     *
     * @param systemCommonGroupDTO 公共组信息
     * @return 结果
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public SystemCommonGroup saveSystemCommonGroupDTO(SystemCommonGroupDTO systemCommonGroupDTO) {
        SystemCommonGroup systemCommonGroup = new SystemCommonGroup();
        BeanUtils.copyProperties(systemCommonGroupDTO, systemCommonGroup);
        if (StringUtils.isNotEmpty(systemCommonGroupDTO.getId())) {
            systemCommonGroupMapper.updateById(systemCommonGroup);
        } else {
            systemCommonGroupMapper.insert(systemCommonGroup);
        }
        List<SystemCommonGroupMenuDTO> list = systemCommonGroupDTO.getMenuList();
        if (list != null && !list.isEmpty() && RoleConstants.GROUP_TYPE_ROLE.equals(systemCommonGroup.getGroupType())) {
            for (SystemCommonGroupMenuDTO sysCommonGroupMenuDTO : list) {

                SystemCommonGroupMenu systemCommonGroupMenu = new SystemCommonGroupMenu();
                BeanUtils.copyProperties(sysCommonGroupMenuDTO, systemCommonGroupMenu);

                if (DataConstants.ACTION_TAG_ADD.equals(sysCommonGroupMenuDTO.getActionTag())) {
                    systemCommonGroupMenu.setId(null);
                    systemCommonGroupMenu.setCreatedBy(null);
                    systemCommonGroupMenu.setCreatedTime(new Date());
                    systemCommonGroupMenu.setCommonGroupId(systemCommonGroup.getId());
                    systemCommonGroupMenuMapper.insert(systemCommonGroupMenu);
                    // 给所有有此应用的组织增加新的功能菜单

                    //查询有此应用的组织，然后给这些组织增加菜单
                    Boolean oldIgnore = OrganizationContextHolder.isIgnore();
                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);

                        // 查询所有此角色,不论哪个组织
                        LambdaPrivernalWrapper<SystemRole> wrapper = new LambdaPrivernalWrapper<SystemRole>();
                        wrapper.eq(SystemRole::getSystemType, DataConstants.DATA_TYPE_SYSTEM)
                                .eq(SystemRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT)
                                .eq(SystemRole::getRoleKey, systemCommonGroup.getCode());
                        List<SystemRole> systemRoleList = systemRoleMapper.selectList(wrapper);
                        // 拥有此角色的所有的组织，给他们加新增的菜单功能
                        for (SystemRole systemRole : systemRoleList) {
                            String organizationId = systemRole.getOrganizationId();
                            if (StringUtils.isNotEmpty(organizationId)) {

                                SystemMenu systemMenu = systemMenuMapper.selectById(systemCommonGroupMenu.getMenuId());
                                //给默认的角色，增加这些菜单授权
                                SystemRoleMenu systemRoleMenu = new SystemRoleMenu();
                                systemRoleMenu.setMenuId(systemCommonGroupMenu.getMenuId());
                                systemRoleMenu.setRoleId(systemRole.getId());
//                                systemRoleMenu.setAppId(systemCommonGroupMenu.getCommonGroupId());
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
                if (DataConstants.ACTION_TAG_DELETE.equals(sysCommonGroupMenuDTO.getActionTag())) {

                    // 删除应用关联的菜单
                    systemCommonGroupMenuMapper.deleteFillById(systemCommonGroupMenu.getId());
                    // 删除所有已经授权的应用的菜单
                    //查询有此应用的组织，然后给这些组织增加菜单
                    Boolean oldIgnore = OrganizationContextHolder.isIgnore();
                    try {
                        // 去掉组织权限，使用参数查询，因为查询的不是当前登录人的组织菜单，是授权目标组织的菜单。系统平台管理管理员的操作
                        OrganizationContextHolder.setIgnore(true);

                        // 删除所有组织的此功能菜单
                        systemRoleMenuMapper.deleteAllRoleMenuByRoleKeyAndMenuId(systemCommonGroup.getCode(), Collections.singletonList(sysCommonGroupMenuDTO.getMenuId()));

                    } finally {
                        OrganizationContextHolder.setIgnore(oldIgnore);
                        OrganizationContextHolder.clear();
                    }

                }
                if (DataConstants.ACTION_TAG_EDIT.equals(sysCommonGroupMenuDTO.getActionTag())) {
                    systemCommonGroupMenuMapper.updateById(systemCommonGroupMenu);
                }
            }
        }
        return systemCommonGroup;
    }

    private boolean checkCodeUnique(String code, String id) {
        LambdaPrivernalWrapper<SystemCommonGroup> wrapper = new LambdaPrivernalWrapper<SystemCommonGroup>();
        wrapper.eq(SystemCommonGroup::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT)
                .eq(SystemCommonGroup::getCode, code);
        Long count = systemCommonGroupMapper.selectCount(wrapper);
        if (count > 0) {
            if (StringUtils.isNotEmpty(id)) {
                SystemCommonGroup systemCommonGroup = systemCommonGroupMapper.selectById(id);
                if (systemCommonGroup.getCode().equals(code)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * 批量删除公共组[用户或角色]
     *
     * @param ids 需要删除的公共组[用户或角色]主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemCommonGroupByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemCommonGroupById(id);
        }
        return ids.length;
    }

    /**
     * 删除公共组[用户或角色]信息
     *
     * @param id 公共组[用户或角色]主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemCommonGroupById(String id) {
        // 删除公共组关联的应用关系
        LambdaPrivernalWrapper<SystemAppCommonGroup> wrapper = new LambdaPrivernalWrapper<SystemAppCommonGroup>();
        wrapper.eq(SystemAppCommonGroup::getCommonGroupId, id);
        wrapper.eq(SystemAppCommonGroup::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        List<SystemAppCommonGroup> userAppMenuList = systemAppCommonGroupMapper.selectList(wrapper);
        if (userAppMenuList != null && !userAppMenuList.isEmpty()) {
            for (SystemAppCommonGroup groupUser : userAppMenuList) {
                systemAppCommonGroupMapper.deleteFillById(groupUser.getId());
            }
        }
        return systemCommonGroupMapper.deleteFillById(id);
    }

}
