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

import com.privernal.common.core.constant.UserConstants;
import com.privernal.common.core.constant.RoleConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.ip.IpUtils;
import com.privernal.common.datascope.aspect.PrivernalDataScopeAspect;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.*;
import com.privernal.system.dto.SystemRoleDataScopeDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.SystemUserServiceOrgDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.filter.SystemOrganizationFilterDTO;
import com.privernal.system.filter.SystemUserFilterDTO;
import com.privernal.system.filter.SystemUserServiceOrgFilterDTO;
import com.privernal.system.mapper.*;
import com.privernal.system.service.ISystemRoleService;
import com.privernal.system.service.ISystemUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.privernal.common.core.constant.DataConstants.DATA_DELETE_FLAG_NOT;
import static com.privernal.common.core.constant.UserConstants.USER_TYPE_SYSTEM;
import static com.privernal.common.security.service.SecurityUtils.getDefaultPassword;
import static com.privernal.common.security.service.SecurityUtils.getMd5Password;

/**
 * 用户的Service业务层处理
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的Service业务层处理
 */
@Slf4j
@Service
public class SystemUserServiceImpl implements ISystemUserService {
    @Autowired
    private SystemUserMapper systemUserMapper;

    @Autowired
    private SystemUserServiceOrgMapper systemUserServiceOrgMapper;

    @Autowired
    private ISystemRoleService iSystemRoleService;

    @Autowired
    private SystemDepartmentMapper systemDepartmentMapper;

    @Autowired
    private SystemOrganizationMapper systemOrganizationMapper;

    @Autowired
    private SystemDeptRelationMapper systemDeptRelationMapper;

    @Autowired
    private SystemRoleUserDeptMapper systemRoleUserDeptMapper;

    @Autowired
    private SystemRoleUserMapper systemRoleUserMapper;

    @Autowired
    private SystemUserSocialUserMapper systemUserSocialUserMapper;

    @Autowired
    private SystemSocialUserMapper systemSocialUserMapper;

    @Autowired
    private SystemUserGroupUserMapper systemUserGroupUserMapper;

    @Autowired
    private SystemUserDepartmentMapper systemUserDepartmentMapper;

    @Autowired
    private SystemUserAppMenuMapper systemUserAppMenuMapper;

    /**
     * 查询用户
     *
     * @param id 用户主键
     * @return 用户
     */
    @Transactional(readOnly = true)
    @Override
    public SystemUser selectSystemUserById(String id) {
        return systemUserMapper.selectById(id);
    }

    /**
     * 查询用户列表
     *
     * @param systemUser 用户
     * @return 用户
     */
    @Transactional(readOnly = true)
    @Override
    public List<SystemUser> selectSystemUserList(SystemUserFilterDTO systemUser) {
        if (StringUtils.isNotEmpty(systemUser.getDepartmentId())) {
            List<String> allIds = new ArrayList<>();
            allIds.add(systemUser.getDepartmentId());
            //查询此部门的子部门
            List<SystemDeptRelation> systemDepartments = systemDeptRelationMapper.selectAuthAllChildren(systemUser.getDepartmentId());

            List<String> departmentIds = systemDepartments.stream().map(SystemDeptRelation::getDeptId).toList();
            allIds.addAll(departmentIds);
            systemUser.setDeptIds(allIds.toArray(new String[0]));
        }

        return systemUserMapper.selectSystemUserList(systemUser);
    }

    /**
     * 新增用户
     *
     * @param systemUser 用户
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int insertSystemUser(SystemUser systemUser) {
        if (!checkPhoneUnique(systemUser)) {
            return UserConstants.NOT_UNIQUE_MOBILE;
        }
        if (!checkEmailUnique(systemUser)) {
            return UserConstants.NOT_UNIQUE_EMAIL;
        }
        if (!checkLoginNameUnique(systemUser.getLoginName())) {
            return UserConstants.NOT_UNIQUE_LOGIN_NAME;
        }

        // dataScope 是 Long，与 long 常量比较会自动拆箱：不带该字段（走 API 直接建用户）时这里曾经 500 NPE。
        // 界面路径一定带值，所以这里按「入参不完整」明确报错，而不是给一个猜出来的默认范围。
        if (systemUser.getDataScope() == null) {
            throw new ServiceException("数据范围（dataScope）不能为空");
        }

        if (systemUser.getDataScope() == PrivernalDataScopeAspect.DATA_SCOPE_ALL) {
            if (!SecurityUtils.isSuperPlatformRoleUser() && !SecurityUtils.isSuperRoleUser()) {
                return UserConstants.DATA_SCOPE_MAX;
            }
        }

        // 先进行一轮加密，因为登录时使用的此加密算法
        // 没有设置密码，则默认是手机号后6位
        String phone = systemUser.getPhone();
        if (StringUtils.isEmpty(phone)){
            // 同步企业微信用户时，没有手机号。
            systemUser.setPassword(SecurityUtils.encryptPassword(getMd5Password(systemUser.getLoginName())));
        }else{
            systemUser.setPassword(SecurityUtils.encryptPassword(getMd5Password(getDefaultPassword(phone))));
        }

        systemUserMapper.insert(systemUser);

        SystemUserDepartment userDepartment = new SystemUserDepartment();
        userDepartment.setDepartmentId(systemUser.getDepartmentId());
        userDepartment.setOrganizationId(systemUser.getOrganizationId());
        userDepartment.setUserId(systemUser.getId());
        userDepartment.setIsMain(1L);
        if(StringUtils.isEmpty(SecurityUtils.getUserId())){
            userDepartment.setCreatedBy(USER_TYPE_SYSTEM);
        }
        systemUserDepartmentMapper.insert(userDepartment);

        // 增加服务的组织和部门
        SystemUserServiceOrg systemUserServiceOrg = new SystemUserServiceOrg();
        systemUserServiceOrg.setUserId(systemUser.getId());
        systemUserServiceOrg.setOrganizationId(systemUser.getOrganizationId());
        systemUserServiceOrg.setDepartmentId(systemUser.getDepartmentId());
        systemUserServiceOrg.setDataScope(systemUser.getDataScope());
        if(StringUtils.isEmpty(SecurityUtils.getUserId())){
            systemUserServiceOrg.setCreatedBy(USER_TYPE_SYSTEM);
        }
        return systemUserServiceOrgMapper.insert(systemUserServiceOrg);

    }

    /**
     * 修改用户
     *
     * @param systemUser 用户
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateSystemUser(SystemUser systemUser) {
        checkUserAllowed(systemUser.getId());
        if (!checkPhoneUnique(systemUser)) {
            return UserConstants.NOT_UNIQUE_MOBILE;
        }
        if (!checkEmailUnique(systemUser)) {
            return UserConstants.NOT_UNIQUE_EMAIL;
        }
        String userId = systemUser.getId();
        SystemUserDTO info = systemUserMapper.selectUserByLoginName(systemUser.getLoginName());
        if (StringUtils.isNotNull(info) && !info.getId().equals(userId)) {
            return UserConstants.NOT_UNIQUE_LOGIN_NAME;
        }

        if (StringUtils.isNotNull(info) && !info.getDepartmentId().equals(systemUser.getDepartmentId())) {
            // 删除原主部门数据
            LambdaPrivernalWrapper<SystemUserDepartment> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(SystemUserDepartment::getUserId, userId);
            wrapper.eq(SystemUserDepartment::getDepartmentId, info.getDepartmentId());
            wrapper.eq(SystemUserDepartment::getDeletedFlag, DATA_DELETE_FLAG_NOT);
            SystemUserDepartment systemUserDepartment = systemUserDepartmentMapper.selectOne(wrapper);
            if (systemUserDepartment != null) {
                systemUserDepartmentMapper.deleteFillById(systemUserDepartment.getId());
            }

            // 添加新部门数据
            SystemUserDepartment userDepartment = new SystemUserDepartment();
            userDepartment.setDepartmentId(systemUser.getDepartmentId());
            userDepartment.setOrganizationId(systemUser.getOrganizationId());
            userDepartment.setUserId(systemUser.getId());
            userDepartment.setIsMain(1L);
            systemUserDepartmentMapper.insert(userDepartment);
        }

        systemUserMapper.updateById(systemUser);

        SystemUserServiceOrgFilterDTO filterDTO = new SystemUserServiceOrgFilterDTO();
        filterDTO.setUserId(systemUser.getId());
        filterDTO.setOrganizationId(systemUser.getOrganizationId());
        List<SystemUserServiceOrg> serviceOrgList = systemUserServiceOrgMapper.selectSystemUserServiceOrgList(filterDTO);
        if (serviceOrgList != null && serviceOrgList.size() == 1) {
            // 增加服务的组织和部门
            SystemUserServiceOrg systemUserServiceOrg = serviceOrgList.get(0);
            systemUserServiceOrg.setUserId(systemUser.getId());
            systemUserServiceOrg.setOrganizationId(systemUser.getOrganizationId());
            systemUserServiceOrg.setDepartmentId(systemUser.getDepartmentId());
            systemUserServiceOrg.setDataScope(systemUser.getDataScope());
            return systemUserServiceOrgMapper.updateById(systemUserServiceOrg);
        } else {
            if (!SecurityUtils.isSuperPlatformRoleUser()) {
                throw new ServiceException("用户数据异常");
            }
            return 0;
        }

    }

    /**
     * 批量删除用户
     *
     * @param ids 需要删除的用户主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserByIds(String[] ids) {
        for (String id : ids) {
            deleteSystemUserById(id);
        }
        return ids.length;
    }

    /**
     * 删除用户信息
     *
     * @param id 用户主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteSystemUserById(String id) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        log.info("deleteSystemUserById： oldIgnore {}.   currentThread:{}", oldIgnore, Thread.currentThread().getId());
        try {
            OrganizationContextHolder.setIgnore(true);

            log.info("deleteSystemUserById. isIgnore：{}  currentThread:{} ", OrganizationContextHolder.isIgnore(), Thread.currentThread().getId());
            checkUserAllowed(id);
            // 可以删除自己，在注销的时候。
//            Assert.isTrue(!SecurityUtils.getUserId().equals(id), "用户不可以删除自己");
            // 删除角色用户
            OrganizationContextHolder.setIgnore(true);

            List<SystemRoleUser> roleUsers = systemRoleUserMapper.selectSystemRoleUserByUserId(id);
            for (SystemRoleUser roleUser : roleUsers) {
                systemRoleUserMapper.deleteFillById(roleUser.getId());
                List<SystemRoleUserDept> roleUserDepts = systemRoleUserDeptMapper.selectSystemRoleUserDeptByRoleUserId(roleUser.getId());
                if (roleUserDepts != null && !roleUserDepts.isEmpty()) {
                    for (SystemRoleUserDept userDept : roleUserDepts) {
                        systemRoleUserDeptMapper.deleteFillById(userDept.getId());
                    }
                }
            }
            // 删除授权给此用户的组织
            List<SystemUserServiceOrgDTO> all = systemOrganizationMapper.selectUserSystemOrganizationList(id);
            if (all != null && !all.isEmpty()) {
                for (SystemUserServiceOrgDTO systemUserServiceOrgDTO : all) {
                    systemUserServiceOrgMapper.deleteFillById(systemUserServiceOrgDTO.getId());
                }
            }
            // 删除第三方授权登录帐号
            SystemUserSocialUser userSocialUser = systemUserSocialUserMapper.selectSysUserSocialUserByUserId(id);
            if (userSocialUser != null) {
                systemSocialUserMapper.deleteFillById(userSocialUser.getSocialUserId());
            }
            // 删除用户组里的此用户关系

            List<SystemUserGroupUser> systemUserGroupUserList = systemUserGroupUserMapper.selectSystemUserGroupByUserId(id);
            if (systemUserGroupUserList != null && !systemUserGroupUserList.isEmpty()) {
                for (SystemUserGroupUser groupUser : systemUserGroupUserList) {
                    systemUserGroupUserMapper.deleteFillById(groupUser.getId());
                }
            }

            // 删除用户所有的兼职部门关系
            List<SystemUserDepartment> userDepartmentList = systemUserDepartmentMapper.selectList(SystemUserDepartment::getUserId, id, SystemUserDepartment::getDeletedFlag, DATA_DELETE_FLAG_NOT);
            if (userDepartmentList != null && !userDepartmentList.isEmpty()) {
                for (SystemUserDepartment groupUser : userDepartmentList) {
                    systemUserDepartmentMapper.deleteFillById(groupUser.getId());
                }
            }

            // 删除用户收藏的常用功能
            List<SystemUserAppMenu> userAppMenuList = systemUserAppMenuMapper.selectList(SystemUserAppMenu::getUserId, id, SystemUserAppMenu::getDeletedFlag, DATA_DELETE_FLAG_NOT);
            if (userAppMenuList != null && !userAppMenuList.isEmpty()) {
                for (SystemUserAppMenu groupUser : userAppMenuList) {
                    systemUserAppMenuMapper.deleteFillById(groupUser.getId());
                }
            }

            return systemUserMapper.deleteFillById(id);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Boolean checkLoginNameUnique(String loginName) {
        int count = systemUserMapper.checkLoginNameUnique(loginName);
        if (count > 0) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    @Override
    public Boolean checkPhoneUnique(SystemUser user) {
        String userId = user.getId();
        SystemUser info = systemUserMapper.checkPhoneUnique(user.getPhone());
        if (StringUtils.isNotNull(info) && !info.getId().equals(userId)) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    @Override
    public Boolean checkEmailUnique(SystemUser user) {
        String userId = user.getId();
        SystemUser info = systemUserMapper.checkEmailUnique(user.getEmail());
        if (StringUtils.isNotNull(info) && !info.getId().equals(userId) && StringUtils.isNotEmpty(user.getEmail())) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    @Override
    public void checkUserAllowed(String userId) {
        Set<String> roleKeys = iSystemRoleService.getAuthRolePermission(userId, RoleConstants.ROLE_ALL_ORGANIZATION);
        if (roleKeys != null) {
            if (roleKeys.contains(RoleConstants.SUPER_ROLE_ADMIN) || roleKeys.contains(RoleConstants.SUPER_PLATFORM_ROLE_ADMIN)) {
                // 有角色是组织管理员或是平台管理员，则此用户不允许修改
                if (SecurityUtils.isSuperPlatformRoleUser() || (StringUtils.isNotEmpty(userId) && SecurityUtils.getUserId().equals(userId))) {
                    // 自己修改自己信息则允许 // 平台管理员则允许
                } else {
                    throw new ServiceException("不允许操作系统管理员");
                }
            }
        }
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int updateUserProfile(SystemUser user) {
        SystemUser systemUser = systemUserMapper.selectById(user.getId());
        if (StringUtils.isNotEmpty(user.getPhone())){
            systemUser.setPhone(user.getPhone());
        }
        if (StringUtils.isNotEmpty(user.getEmail())){
            systemUser.setEmail(user.getEmail());
        }
        if (StringUtils.isNotEmpty(user.getGender())){
            systemUser.setGender(user.getGender());
        }
        if(StringUtils.isNotEmpty(user.getNickName())){
            systemUser.setNickName(user.getNickName());
        }
        if(StringUtils.isNotEmpty(user.getAvatar())){
            systemUser.setAvatar(user.getAvatar());
        }
        if(StringUtils.isNotEmpty(user.getIdentityNum())){
            systemUser.setIdentityNum(user.getIdentityNum());
        }
        systemUserMapper.updateById(systemUser);
        return 1;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public boolean updateUserAvatar(String loginName, String avatar) {
        return systemUserMapper.updateUserAvatar(loginName, avatar) > 0;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int resetUserPwd(String loginName, String password) {
        return systemUserMapper.updateUserPwd(loginName, password);
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int savePasswordV2(String loginName, String sealed) {
        if (StringUtils.isAnyBlank(loginName, sealed)) {
            return 0;
        }
        // F4：本写在登录进行中调用（无 token 会话），数据范围切面会因取不到会话而炸——
        // 按 OpenApiAiBuildController 同款 set-then-restore 口径忽略数据范围（S19：必须配对恢复）
        Boolean prevOrg = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreOrganization();
        Boolean prevDp = com.privernal.common.datascope.aspect.DataScopeHelper.getIgnoreDataPermission();
        com.privernal.common.datascope.aspect.DataScopeHelper.mergeIgnoreStrategy(true, true);
        try {
            SystemUser user = new SystemUser();
            user.setPasswordV2(sealed);
            return systemUserMapper.update(user,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SystemUser>()
                            .eq(SystemUser::getLoginName, loginName));
        } finally {
            com.privernal.common.datascope.aspect.DataScopeHelper.mergeIgnoreStrategy(prevOrg, prevDp);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public SystemUserDTO selectUserByLoginName(String loginName) {
        return systemUserMapper.selectUserByLoginName(loginName);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemUserDTO selectUserByPhone(String phone) {
        return systemUserMapper.selectUserByPhone(phone);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemUserDTO selectUserDTOByOrgIdAndId(String userId, String orgId) {
        return systemUserMapper.selectUserDTOByOrgIdAndId(userId, orgId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByIdList(List<String> userList) {
        if (userList == null || userList.isEmpty()) {
            return new ArrayList<>();
        }
        return systemUserMapper.selectUserDTOByOrgIdAndIdList(userList);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByDeptIdList(List<String> deptList) {
        return systemUserMapper.selectUserDTOByDeptIdList(deptList);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByRoleIdList(List<String> roleList, String departmentId) {
        return systemUserMapper.selectUserDTOByRoleIdList(roleList, departmentId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByGroupIdList(List<String> groupList, String departmentId) {
        return systemUserMapper.selectUserDTOByGroupIdList(groupList, departmentId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByRoleCodeList(String code, String departmentId, String organizationId) {
        return systemUserMapper.selectUserDTOByRoleCodeList(code, departmentId, organizationId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByGroupCodeList(String code, String departmentId, String organizationId) {
        return systemUserMapper.selectUserDTOByGroupCodeList(code, departmentId, organizationId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByRoleCodes(List<String> code, String departmentId, String organizationId) {
        return systemUserMapper.selectUserDTOByRoleCodes(code, departmentId, organizationId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectUserDTOByGroupCodes(List<String> code, String departmentId, String organizationId) {
        return systemUserMapper.selectUserDTOByGroupCodes(code, departmentId, organizationId);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemUserDTO selectUserDTOById(String userId) {
        return systemUserMapper.selectUserDTOById(userId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserServiceOrgDTO> getAuthSystemOrganizationList(String userId, Set<String> roles, String organizationId) {
        if (roles != null && roles.contains(RoleConstants.SUPER_PLATFORM_ROLE_ADMIN)) {
            // 平台管理员，可以登录所有组织，并且是所有数据权限，todo 超级管理员未查询该组织的部门。在切换时需要查询一下。
            List<SystemUserServiceOrgDTO> all = systemOrganizationMapper.selectAllOrganizationList();
            return all.stream().peek(item -> {
                item.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ALL);
            }).collect(Collectors.toList());
        } else {

            List<SystemUserServiceOrgDTO> all = systemOrganizationMapper.selectUserSystemOrganizationList(userId);
            if (roles != null && roles.contains(RoleConstants.SUPER_ROLE_ADMIN)) {
                return all.stream().peek(item -> {
                    // 同一个组织，如果是此组织的超级管理员，则设置数据权限为所有
                    if (item.getOrganizationId().equals(organizationId)) {
                        item.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ALL);
                    }
                }).collect(Collectors.toList());
            }
            // 处理子部门
            List<SystemUserServiceOrgDTO> haveChildren = new ArrayList<>();
            for (SystemUserServiceOrgDTO orgDTO : all) {
                // 处理登录的组织的数据权限
                if (orgDTO.getOrganizationId().equals(organizationId) && orgDTO.getDataScope() == PrivernalDataScopeAspect.DATA_SCOPE_ORG_AND_CHILD) {
                    // 查询子部门
                    List<SystemDeptRelation> children = systemDeptRelationMapper.selectAuthAllChildren(orgDTO.getDepartmentId());
                    for (SystemDeptRelation deptRelation : children) {
                        SystemUserServiceOrgDTO userServiceOrgDTO = new SystemUserServiceOrgDTO();
                        userServiceOrgDTO.setOrganizationId(orgDTO.getOrganizationId());
                        userServiceOrgDTO.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ORG);
                        userServiceOrgDTO.setDepartmentId(deptRelation.getDeptId());
                        haveChildren.add(userServiceOrgDTO);
                    }
                }
            }
            haveChildren.addAll(all);
            return haveChildren;
        }
    }

    @Transactional(readOnly = true)
    @Override
    public UserInfo getUserInfo(SystemUserDTO sysUser, String organizationId) {
        UserInfo userInfo = new UserInfo();
        BeanUtils.copyProperties(sysUser, userInfo);
        userInfo.setUserId(sysUser.getId());
        userInfo.setNickName(sysUser.getNickName());
        userInfo.setIpAddr(IpUtils.getIpAddr(ServletUtils.getRequest()));
        userInfo.setToken(SecurityUtils.getToken());
        userInfo.setLoginName(sysUser.getLoginName());
        userInfo.setLoginTime(System.currentTimeMillis());
        userInfo.setOrganizationId(organizationId);
        userInfo.setOrganizationName(sysUser.getOrganizationName());

        userInfo.setPassword(sysUser.getPassword());

        if (StringUtils.isNotEmpty(userInfo.getPhone())) {
            // 判断是否是初始密码
            String userPhone = userInfo.getPhone();
            String defaultPassword = SecurityUtils.getDefaultPassword(userPhone);
            String md5Password = SecurityUtils.getMd5Password(defaultPassword);
            if (SecurityUtils.matchesPassword(md5Password, sysUser.getPassword())) {
                userInfo.setInitPassword(true);
            }
        }
        // 查询用户拥有的组织, 设置切换后这个组织的默认部门,查询时已经查出
        userInfo.setDepartmentId(sysUser.getDepartmentId());
        if (StringUtils.isNotEmpty(sysUser.getDepartmentId())) {
            // 因为没有登录，所以查询接口不能用组织id的条件
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                SystemDepartment systemDepartment = systemDepartmentMapper.selectById(sysUser.getDepartmentId());
                userInfo.setDepartmentName(systemDepartment.getDeptName());
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }

        // 角色KEY集合
        Set<String> roles = iSystemRoleService.getAuthRolePermission(sysUser.getId(), organizationId);
        userInfo.setRoleKeys(roles);

        // 后端数据权限查询
        List<SystemRoleDataScopeDTO> dataScopeDTOList = iSystemRoleService.getAuthRoleDeptScope(sysUser.getId(), organizationId, sysUser.getDepartmentId(), roles);
        userInfo.setFunctionDeptList(dataScopeDTOList);

        // 权限集合
        Set<String> permissions = iSystemRoleService.getAuthMenuPermission(sysUser.getId(), organizationId, roles);
        userInfo.setPermissions(permissions);

        // 所在组织,在切换组织时使用，数据权限也会用到，已经处理了子部门的权限
        List<SystemUserServiceOrgDTO> organizationList = getAuthSystemOrganizationList(sysUser.getId(), roles, organizationId);

        //处理此人是组织管理员，就是组织上设置的leader
        SystemOrganizationFilterDTO systemOrganizationDTO = new SystemOrganizationFilterDTO();
        systemOrganizationDTO.setLeaderId(sysUser.getId());
        // 组织是未禁用的
        systemOrganizationDTO.setStatus(WorkflowDataStatus.FINISH.getCode());
        List<SystemOrganization> orgList = systemOrganizationMapper.selectSystemOrganizationList(systemOrganizationDTO);

        if (orgList != null && !orgList.isEmpty()) {
            for (SystemOrganization organization : orgList) {
                for (SystemUserServiceOrgDTO userServiceOrgDTO : organizationList) {
                    if (userServiceOrgDTO.getOrganizationId().equals(organization.getId())) {
                        userServiceOrgDTO.setDataScope(PrivernalDataScopeAspect.DATA_SCOPE_ALL);
                        break;
                    }
                }
            }
        }
        userInfo.setUserServiceOrgDTOList(organizationList);
        return userInfo;
    }

    @Transactional(readOnly = true)
    @Override
    public Long selectCountUserByOrganizationId(String organizationId) {
        LambdaPrivernalWrapper<SystemUser> lambda = new LambdaPrivernalWrapper<>();
        return systemUserMapper.selectCount(lambda);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemUserDTO selectUserDTOByOpenIdOrPhone(String openId, String phone) {
        return systemUserMapper.selectUserDTOByOpenIdOrPhone(openId, phone);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectSystemUserByOrganizationId(String organizationId, String departmentId) {
        return systemUserMapper.selectSystemUserByOrganizationId(organizationId, departmentId);
    }
}
