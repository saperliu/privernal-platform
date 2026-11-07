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

package com.privernal.system.service;

import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.SystemUserServiceOrgDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.filter.SystemUserFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 用户的Service接口
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的Service接口
 */
public interface ISystemUserService {
    /**
     * 查询用户
     *
     * @param id 用户主键
     * @return 用户
     */
    public SystemUser selectSystemUserById(String id);

    /**
     * 查询用户列表
     *
     * @param systemUser 用户
     * @return 用户集合
     */
    public List<SystemUser> selectSystemUserList(SystemUserFilterDTO systemUser);

    /**
     * 新增用户
     *
     * @param systemUser 用户
     * @return 结果
     */
    public int insertSystemUser(SystemUser systemUser);

    /**
     * 修改用户
     *
     * @param systemUser 用户
     * @return 结果
     */
    public int updateSystemUser(SystemUser systemUser);

    /**
     * 批量删除用户
     *
     * @param ids 需要删除的用户主键集合
     * @return 结果
     */
    public int deleteSystemUserByIds(String[] ids);

    /**
     * 删除用户信息
     *
     * @param id 用户主键
     * @return 结果
     */
    public int deleteSystemUserById(String id);

    /**
     * 校验用户名称是否唯一
     *
     * @param loginName 用户名称
     * @return 结果
     */
    public Boolean checkLoginNameUnique(String loginName);

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    public Boolean checkPhoneUnique(SystemUser user);

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    public Boolean checkEmailUnique(SystemUser user);

    /**
     * 校验用户是否允许操作用户
     *
     * @param userId 用户Id
     */
    public void checkUserAllowed(String userId);

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    public int updateUserProfile(SystemUser user);

    /**
     * 修改用户头像
     *
     * @param loginName 用户名
     * @param avatar    头像地址
     * @return 结果
     */
    public boolean updateUserAvatar(String loginName, String avatar);

    /**
     * 重置用户密码
     *
     * @param loginName 用户名
     * @param password  密码
     * @return 结果
     */
    public int resetUserPwd(String loginName, String password);

    /**
     * F4：保存封存口令摘要（挑战登录用；登录引导迁移与改密双写共用）
     *
     * @param loginName 用户名
     * @param sealed    AES-GCM 封存的线摘要
     * @return 结果
     */
    public int savePasswordV2(String loginName, String sealed);

    /**
     * 通过用户名查询用户
     *
     * @param loginName 用户名
     * @return 用户对象信息
     */
    public SystemUserDTO selectUserByLoginName(String loginName);

    /**
     * 通过手机号查询用户
     *
     * @param phone 手机号
     * @return 用户对象信息
     */
    public SystemUserDTO selectUserByPhone(String phone);

    /**
     * 通过组织和用户ID查询用户
     *
     * @param userId 用户ID
     * @param orgId  组织ID
     * @return 用户对象信息
     */
    public SystemUserDTO selectUserDTOByOrgIdAndId(String userId, String orgId);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param userList 用户ID集合
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByIdList(List<String> userList);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param deptList 部门ID集合
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByDeptIdList(List<String> deptList);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param roleList     角色ID集合
     * @param departmentId 特定部门的人员
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByRoleIdList(List<String> roleList, String departmentId);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param groupList    用户组ID集合
     * @param departmentId 特定部门的人员
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByGroupIdList(List<String> groupList, String departmentId);

    /**
     * 通过code查询用户集合
     * 工作流使用
     *
     * @param code         角色code
     * @param departmentId 特定部门的人员
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByRoleCodeList(String code, String departmentId, String organizationId);

    /**
     * 通过code查询用户集合
     * 工作流使用
     *
     * @param code         用户组code
     * @param departmentId 特定部门的人员
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByGroupCodeList(String code, String departmentId, String organizationId);

    /**
     * 通过code查询用户集合
     * 工作流使用
     *
     * @param code         角色code集合
     * @param departmentId 特定部门的人员
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByRoleCodes(List<String> code, String departmentId, String organizationId);

    /**
     * 通过code查询用户集合
     * 工作流使用
     *
     * @param code         用户组code集合
     * @param departmentId 特定部门的人员
     * @return
     */
    public List<SystemUserDTO> selectUserDTOByGroupCodes(List<String> code, String departmentId, String organizationId);

    /**
     * 通过ID查询用户
     * 工作流使用，
     * 因为查询了组织的名称，所以关联表后，会有多条数据。
     * 因为一个人服务多个组织的话。
     *
     * @param userId 用户ID
     * @return
     */
    public SystemUserDTO selectUserDTOById(String userId);

    /**
     * 查询用户拥有的组织列表
     *
     * @param userId 用户ID
     * @param roles  所有角色
     * @return 组织集合
     */
    public List<SystemUserServiceOrgDTO> getAuthSystemOrganizationList(String userId, Set<String> roles, String organizationId);

    /**
     * 处理登录人的session信息,角色、功能、组织、权限范围
     *
     * @param sysUser        用户基础信息
     * @param organizationId 登录的组织
     * @return 用户信息
     */
    public UserInfo getUserInfo(SystemUserDTO sysUser, String organizationId);

    /**
     * 查询组织下用户数量
     * @param organizationId
     * @return
     */
    public Long selectCountUserByOrganizationId(String organizationId);

    /**
     * 通过第三方ID查询用户
     *
     * @param openId 第三方ID
     * @param phone 用户的电话
     * @return
     */
    public SystemUserDTO selectUserDTOByOpenIdOrPhone(String openId, String phone);

    /**
     * 查询部门下的所有人员
     *
     * @param organizationId 组织ID
     * @param departmentId   部门ID
     * @return 人员集合
     */
    public List<SystemUserDTO> selectSystemUserByOrganizationId(String organizationId, String departmentId);
}
