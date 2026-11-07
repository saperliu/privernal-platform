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

package com.privernal.system.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.SystemUserFilterDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper接口
 *
 * @author saperliu
 * @date 2022-10-21
 * @Description 用户Mapper接口
 */
@PrivernalDataScope(permissionMain = "system:user")
public interface SystemUserMapper extends PrivernalMapper<SystemUser> {
    /**
     * 查询用户列表
     * <p>
     * mainAlias 必须与 SystemUserMapper.xml 里 selectSystemUserVo 的表别名逐字一致（`as user1`）。
     * 曾写成 "user"，数据权限谓词一注入就是 `user.department_id`，MySQL 直接
     * Unknown column 'user.department_id' —— /system/user/list 与 /all/list 整页 500。
     *
     * @param systemUser 用户
     * @return 用户集合
     */
    @PrivernalDataScope(mainAlias = "user1", permission = "system:user:list")
    public List<SystemUser> selectSystemUserList(SystemUserFilterDTO systemUser);

    /**
     * 校验用户名称是否唯一
     * 全局唯一，所以去掉组织的ID的过滤条件
     *
     * @param loginName 用户名称
     * @return 结果
     */
    @IgnoreDataScope
    public int checkLoginNameUnique(String loginName);

    /**
     * 校验手机号码是否唯一
     * 全局唯一，所以去掉组织的ID的过滤条件
     *
     * @param phoneNumber 手机号码
     * @return 结果
     */
    @IgnoreDataScope
    public SystemUser checkPhoneUnique(String phoneNumber);

    /**
     * 校验email是否唯一
     * 全局唯一，所以去掉组织的ID的过滤条件
     *
     * @param email 用户邮箱
     * @return 结果
     */
    @IgnoreDataScope
    public SystemUser checkEmailUnique(String email);

    /**
     * 修改用户头像
     *
     * @param loginName 用户名
     * @param avatar    头像地址
     * @return 结果
     */
    @IgnoreDataScope
    public int updateUserAvatar(@Param("loginName") String loginName, @Param("avatar") String avatar);

    /**
     * 重置用户密码
     *
     * @param loginName 用户名
     * @param password  密码
     * @return 结果
     */
    @IgnoreDataScope
    public int updateUserPwd(@Param("loginName") String loginName, @Param("password") String password);

    /**
     * 未登录，通过用户名查询用户
     *
     * @param loginName 用户名
     * @return 用户对象信息
     */
    @IgnoreDataScope
    public SystemUserDTO selectUserByLoginName(@Param("loginName") String loginName);

    /**
     * 未登录，通过手机号查询用户
     *
     * @param phone 手机号
     * @return 用户对象信息
     */
    @IgnoreDataScope
    public SystemUserDTO selectUserByPhone(@Param("phone") String phone);

    /**
     * 未登录，通过用户ID查询用户
     * 微信授权登录时，使用
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    @IgnoreDataScope
    public SystemUser selectSystemUserById(@Param("userId") String userId);

    /**
     * 切换组织时，通过组织和用户ID查询用户
     *
     * @param userId 用户ID
     * @param orgId  组织ID
     * @return 用户对象信息
     */
    @IgnoreDataScope
    public SystemUserDTO selectUserDTOByOrgIdAndId(@Param("userId") String userId, @Param("orgId") String orgId);


    /**
     * 通过组织和用户ID集合查询用户
     * 工作流使用
     *
     * @param userList 用户ID
     * @return 用户对象信息
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByOrgIdAndIdList(@Param("userList") List<String> userList);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param deptList 部门ID集合
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByDeptIdList(@Param("deptList") List<String> deptList);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param roleList     角色ID集合
     * @param departmentId 特定部门的人员
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByRoleIdList(@Param("roleList") List<String> roleList, @Param("departmentId") String departmentId);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param groupList    用户组ID集合
     * @param departmentId 特定部门的人员
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByGroupIdList(@Param("groupList") List<String> groupList, @Param("departmentId") String departmentId);


    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param roleCode     角色编码
     * @param departmentId 特定部门的人员
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByRoleCodeList(@Param("roleCode") String roleCode, @Param("departmentId") String departmentId, @Param("organizationId") String organizationId);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param groupCode    用户组编码
     * @param departmentId 特定部门的人员
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByGroupCodeList(@Param("groupCode") String groupCode, @Param("departmentId") String departmentId, @Param("organizationId") String organizationId);


    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param roleCode     角色编码
     * @param departmentId 特定部门的人员
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByRoleCodes(@Param("roleCodeList") List<String> roleCode, @Param("departmentId") String departmentId, @Param("organizationId") String organizationId);

    /**
     * 通过ID查询用户集合
     * 工作流使用
     *
     * @param groupCode    用户组编码
     * @param departmentId 特定部门的人员
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public List<SystemUserDTO> selectUserDTOByGroupCodes(@Param("groupCodeList") List<String> groupCode, @Param("departmentId") String departmentId, @Param("organizationId") String organizationId);

    /**
     * 通过ID查询用户
     * 工作流使用，
     * 因为查询了组织的名称，所以关联表后，会有多条数据。
     * 因为一个人服务多个组织的话。
     *
     * @param userId 用户ID
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public SystemUserDTO selectUserDTOById(@Param("userId") String userId);


    /**
     * 通过第三方ID查询用户
     *
     * @param openId 第三方ID
     * @return
     */
    @PrivernalDataScope(ignoreDepartment = true)
    public SystemUserDTO selectUserDTOByOpenIdOrPhone(@Param("openId") String openId,@Param("phone") String phone);

    /**
     * 通过组织ID查询用户
     * @param organizationId 组织ID
     * @param departmentId 部门ID
     * @return 用户集合
     */
    @IgnoreDataScope
    public List<SystemUserDTO> selectSystemUserByOrganizationId(@Param("organizationId")String organizationId, @Param("departmentId")String departmentId);
}
