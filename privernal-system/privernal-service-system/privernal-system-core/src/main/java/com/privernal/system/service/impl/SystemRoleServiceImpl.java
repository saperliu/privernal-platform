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
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.system.domain.*;
import com.privernal.system.dto.SystemRoleDTO;
import com.privernal.system.dto.SystemRoleDataScopeDTO;
import com.privernal.system.dto.SystemRoleMenuDTO;
import com.privernal.system.dto.SystemRoleUserDTO;
import com.privernal.system.filter.SystemRoleFilterDTO;
import com.privernal.system.mapper.*;
import com.privernal.system.service.ISystemMenuService;
import com.privernal.system.service.ISystemRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 角色的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色的Service业务层处理
 */
@Slf4j
@Service
public class SystemRoleServiceImpl implements ISystemRoleService {

    @Autowired
    private SystemRoleMapper systemRoleMapper;

    @Autowired
    private ISystemMenuService menuService;

    @Autowired
    private SystemRoleUserMapper systemRoleUserMapper;

    @Autowired
    private SystemRoleUserDeptMapper systemRoleUserDeptMapper;

    @Autowired
    private SystemRoleMenuMapper systemRoleMenuMapper;

    @Autowired
    private SystemRoleGroupRoleMapper systemRoleGroupRoleMapper;

    @Autowired
    private SystemDeptRelationMapper systemDeptRelationMapper;

    /**
     * 查询角色
     *
     * @param id 角色主键
     * @return 角色
     */
    @Transactional(readOnly = true)
    @Override
    public SystemRole selectSystemRoleById(String id) {
        return systemRoleMapper.selectById(id);
    }

    /**
     * 查询角色列表
     *
     * @param systemRole 角色
     * @return 角色
     */
    @PrivernalDataScope(mainAlias = "role", permission = "system:role:list")
    @Transactional(readOnly = true)
    @Override
    public List<SystemRole> selectSystemRoleList(SystemRoleFilterDTO systemRole) {
        return systemRoleMapper.selectSystemRoleList(systemRole);
    }

    @PrivernalDataScope(mainAlias = "role", permission = "system:role:list")
    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleDTO> selectSystemRoleDTOList(SystemRoleFilterDTO systemRole) {
        return systemRoleMapper.selectSystemRoleDTOList(systemRole);
    }

    /**
     * 新增角色
     *
     * @param systemRole 角色
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemRole(SystemRole systemRole) {
        checkAllowed(systemRole.getRoleKey(), systemRole.getSystemType());
        return systemRoleMapper.insert(systemRole);
    }

    /**
     * 修改角色
     *
     * @param systemRole 角色
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemRole(SystemRole systemRole) {
        checkAllowed(systemRole.getRoleKey(), systemRole.getSystemType());
        return systemRoleMapper.updateById(systemRole);
    }

    /**
     * 批量删除角色
     *
     * @param ids 需要删除的角色主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemRoleById(id);
        }
        return ids.length;
    }

    /**
     * 删除角色信息
     *
     * @param id 角色主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemRoleById(String id) {
        SystemRole delObject = systemRoleMapper.selectById(id);
        checkAllowed(delObject.getRoleKey(), delObject.getSystemType());

        // 删除角色里的人员
        List<SystemRoleUser> roleUsers = systemRoleUserMapper.selectList(SystemRoleUser::getRoleId, id, SystemRoleUser::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
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
        List<SystemRoleMenu> userAppMenuList = systemRoleMenuMapper.selectList(SystemRoleMenu::getRoleId, id, SystemRoleMenu::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        if (userAppMenuList != null && !userAppMenuList.isEmpty()) {
            for (SystemRoleMenu groupUser : userAppMenuList) {
                systemRoleMenuMapper.deleteFillById(groupUser.getId());
            }
        }
        // 删除角色组里的角色
        List<SystemRoleGroupRole> roleGroupRoleList = systemRoleGroupRoleMapper.selectList(SystemRoleGroupRole::getRoleId, id, SystemRoleGroupRole::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        if (roleGroupRoleList != null && !roleGroupRoleList.isEmpty()) {
            for (SystemRoleGroupRole groupUser : roleGroupRoleList) {
                systemRoleGroupRoleMapper.deleteFillById(groupUser.getId());
            }
        }
        return systemRoleMapper.deleteFillById(id);
    }

    private void checkAllowed(String roleKey, String roleType) {
        if (StringUtils.isNotNull(roleKey) && (RoleConstants.SUPER_PLATFORM_ROLE_ADMIN.equals(roleKey) || RoleConstants.SUPER_ROLE_ADMIN.equals(roleKey) || DataConstants.DATA_TYPE_SYSTEM.equals(roleType))) {
            throw new ServiceException("不允许操作系统默认角色");
        }
    }

    /**
     * 获取角色数据权限
     *
     * @param userId 用户Id
     * @return 角色权限信息
     */
    @Transactional(readOnly = true)
    @Override
    public Set<String> getAuthRolePermission(String userId, String organizationId) {
        // 先查询所有角色，判断是否有超级角色 todo 此处查询影响后面的权限判断
        List<SystemRole> perms;
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            //  根据用户ID查询所有角色，跨组织，
            // 在登录查询角色时使用，用于系统平台管理员切换到其他组织时，判断是否是系统平台管理员
            OrganizationContextHolder.setIgnore(true);
            perms = systemRoleMapper.selectAuthRolePermissionByUserId(userId);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

        Set<String> permsSet = new HashSet<>();
        for (SystemRole perm : perms) {

            if (StringUtils.isNotNull(perm) && RoleConstants.SUPER_PLATFORM_ROLE_ADMIN.equals(perm.getRoleKey())) {
                // 存在平台超级角色,不需要区分组织
                permsSet.add(RoleConstants.SUPER_PLATFORM_ROLE_ADMIN);
            }

            // 查询所有组织里的角色是否有管理员，在判断用户是否允许修改时使用
            if (RoleConstants.ROLE_ALL_ORGANIZATION.equals(organizationId)) {
                permsSet.add(perm.getRoleKey().trim());
            } else {
                // 当前组织的角色
                if (StringUtils.isNotNull(perm) && organizationId.equals(perm.getOrganizationId())) {
                    if (StringUtils.isNotEmpty(perm.getOrganizationId()) && perm.getOrganizationId().equals(organizationId)) {
                        // 当前组织的角色key
                        permsSet.add(perm.getRoleKey().trim());
                    }
                }
            }
        }
        return permsSet;
    }

    /**
     * 获取菜单数据权限
     *
     * @param userId 用户Id
     * @return 菜单权限信息
     */
    @Transactional(readOnly = true)
    @Override
    public Set<String> getAuthMenuPermission(String userId, String organizationId, Set<String> roles) {
        Set<String> perms = new HashSet<String>();
        // 管理员拥有所有权限
        if (roles != null && (roles.contains(RoleConstants.SUPER_ROLE_ADMIN) || roles.contains(RoleConstants.SUPER_PLATFORM_ROLE_ADMIN))) {
            perms.add(RoleConstants.SUPER_ALL_PERMISSION);
        } else {
            perms.addAll(menuService.getAuthMenuPermissionByUserId(userId, organizationId));
        }
        return perms;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleDataScopeDTO> getAuthRoleDeptScope(String userId, String organizationId, String departmentId, Set<String> roles) {
        // 查询角色赋予的数据权限
        if (roles != null && (roles.contains(RoleConstants.SUPER_ROLE_ADMIN) || roles.contains(RoleConstants.SUPER_PLATFORM_ROLE_ADMIN))) {
            SystemRoleDataScopeDTO systemRoleDataScopeDTO = new SystemRoleDataScopeDTO();
            systemRoleDataScopeDTO.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ALL);
            systemRoleDataScopeDTO.setPermission(RoleConstants.SUPER_ALL_PERMISSION);
            List<SystemRoleDataScopeDTO> dataScopeDTOList = new ArrayList<>();
            dataScopeDTOList.add(systemRoleDataScopeDTO);
            return dataScopeDTOList;
        } else {
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                List<SystemRoleDataScopeDTO> systemRoleDataScopeDTOList = systemRoleMapper.selectRoleDeptScope(userId, organizationId);
                // 查询这些部门的子部门，需要处理服务部门的子部门。
                Map<String, List<SystemDeptRelation>> deptIds = new HashMap<>();
                List<SystemRoleDataScopeDTO> roleDataScopeDTOArrayList = new ArrayList<>();
                // 自定义(40)=本部门 ∪ 服务部门：主部门行每个 permission 只补一次
                Set<String> customMainDeptFilled = new HashSet<>();
                for (SystemRoleDataScopeDTO systemRoleDataScopeDTO : systemRoleDataScopeDTOList) {
                    if (systemRoleDataScopeDTO.getDataScope() == null) {
                        continue;
                    }
                    // 服务部门未配置的行统一落到本人主部门：
                    // 本部门(20)的口径就是「本人主部门」，不依赖服务部门配置，缺部门时绝不能放行
                    if (StringUtils.isEmpty(systemRoleDataScopeDTO.getDepartmentId())) {
                        systemRoleDataScopeDTO.setDepartmentId(departmentId);
                    }

                    // 功能授权的数据范围
                    if (PrivernalDataScopeAspect.DATA_SCOPE_ORG_AND_CHILD == systemRoleDataScopeDTO.getDataScope()) {
                        // 本部门及以下：拆子部门为 ORG 行后，原行也转 ORG，否则漏掉部门自身
                        if (!deptIds.containsKey(systemRoleDataScopeDTO.getDepartmentId())) {
                            List<SystemDeptRelation> subSystemRoleDataScopeDTOList = systemDeptRelationMapper.selectAuthAllChildren(systemRoleDataScopeDTO.getDepartmentId());
                            deptIds.put(systemRoleDataScopeDTO.getDepartmentId(), subSystemRoleDataScopeDTOList);
                            findAllChildren(systemRoleDataScopeDTO, subSystemRoleDataScopeDTOList, roleDataScopeDTOArrayList);
                        } else {
                            findAllChildren(systemRoleDataScopeDTO, deptIds.get(systemRoleDataScopeDTO.getDepartmentId()), roleDataScopeDTOArrayList);
                        }
                        systemRoleDataScopeDTO.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                    } else if (PrivernalDataScopeAspect.DATA_SCOPE_CUSTOM == systemRoleDataScopeDTO.getDataScope()) {
                        // 自定义 = 本部门 ∪ 服务部门：服务部门行由 selectRoleDeptScope 按
                        // system_role_user_dept 拆行（每行一个部门），统一转 ORG 交给切面；
                        // 本部门行按 permission 补一次，保证「本部门的基础上配置服务部门」
                        systemRoleDataScopeDTO.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                        if (customMainDeptFilled.add(systemRoleDataScopeDTO.getPermission())) {
                            if (!systemRoleDataScopeDTO.getDepartmentId().equals(departmentId)) {
                                SystemRoleDataScopeDTO mainDeptRow = new SystemRoleDataScopeDTO();
                                BeanUtils.copyProperties(systemRoleDataScopeDTO, mainDeptRow);
                                mainDeptRow.setDepartmentId(departmentId);
                                roleDataScopeDTOArrayList.add(mainDeptRow);
                            }
                        }
                    }
                }
                if (!roleDataScopeDTOArrayList.isEmpty()) {
                    systemRoleDataScopeDTOList.addAll(roleDataScopeDTOArrayList);
                }
                return systemRoleDataScopeDTOList;
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }
    }

    private void findAllChildren(SystemRoleDataScopeDTO systemRoleDataScopeDTO, List<SystemDeptRelation> subSystemRoleDataScopeDTOList, List<SystemRoleDataScopeDTO> roleDataScopeDTOArrayList) {
        if (subSystemRoleDataScopeDTOList != null && !subSystemRoleDataScopeDTOList.isEmpty()) {
            subSystemRoleDataScopeDTOList.forEach(subSystemRoleDataScopeDTO -> {
                // 封装数据，添加到授权的数据权限集合里
                if (StringUtils.isNotEmpty(subSystemRoleDataScopeDTO.getDeptId())) {
                    SystemRoleDataScopeDTO systemRoleDataScopeDTO1 = new SystemRoleDataScopeDTO();
                    BeanUtils.copyProperties(systemRoleDataScopeDTO, systemRoleDataScopeDTO1);

                    systemRoleDataScopeDTO1.setDepartmentId(subSystemRoleDataScopeDTO.getDeptId());
                    systemRoleDataScopeDTO1.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                    roleDataScopeDTOArrayList.add(systemRoleDataScopeDTO1);
                }
            });
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleUserDTO> selectSystemUserByRoleList(String roleId, String nickName) {
        return systemRoleMapper.selectSystemUserByRoleList(roleId, nickName);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleMenuDTO> selectSystemMenuByRoleList(String roleId) {
        return systemRoleMapper.selectSystemMenuByRoleList(roleId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleMenuDTO> selectSystemMenuByRoleList(String roleId, String appId) {
        return systemRoleMapper.selectSystemMenuByRoleAndAppList(roleId, appId);
    }
}
