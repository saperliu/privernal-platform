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

package com.privernal.system.feign;

import com.privernal.common.core.constant.Constants;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.dto.*;
import com.privernal.system.filter.SystemDepartmentFilterDTO;
import com.privernal.system.filter.WorkflowCodeFilterDTO;
import com.privernal.system.filter.WorkflowUserFilterDTO;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * @author saperliu
 * @date 2022-04-10
 * @Description 用户服务
 */
public interface RemoteUserService {
    /**
     * 通过用户名查询用户信息
     *
     * @param username 用户名
     * @return 结果
     */
    @GetMapping(value = "/inner/user/info/{username}")
    @ResponseBody
    public CommonResult<UserInfo> getUserInfo(@PathVariable("username") String username, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 删除用户
     *
     * @param id 用户Id
     * @return 结果
     */
    @DeleteMapping("/inner/user/delete/{id}")
    @ResponseBody
    public CommonResult<Object> deleteUser(@PathVariable("id") String id, @RequestHeader(Constants.SERVICE_REQUEST) String request);


    /**
     * 切换组织
     *
     * @param organizationId 组织ID
     * @param userId         用户ID
     * @return
     */
    @GetMapping("/inner/user/change/{organizationId}/{userId}")
    @ResponseBody
    public CommonResult<UserInfo> changeOrganization(@PathVariable("organizationId") String organizationId, @PathVariable("userId") String userId, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 查询组织下的部门
     *
     * @param systemDepartmentFilter
     * @param request
     * @return
     */
    @PostMapping("/inner/system/department/list")
    public CommonResult<List<SystemDepartmentDTO>> findDeptListByOrgId(@RequestBody SystemDepartmentFilterDTO systemDepartmentFilter, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 查询小程序的配置
     *
     * @param appCode 小程序的code
     * @return
     */
    @GetMapping(value = "/inner/social/sysSocialAppConfig/{appCode}")
    public CommonResult<SystemSocialAppConfigDTO> getAppConfig(@PathVariable("appCode") String appCode, @RequestHeader(Constants.SERVICE_REQUEST) String request);


    /**
     * 查询小程序的配置
     *
     * @param appCode 小程序的code
     * @param cropId  组织的ID
     * @return
     */
    public CommonResult<SystemSocialAppConfigDTO> getSocialAppConfig(String appCode, String cropId);


    /**
     * 保存菜单
     *
     * @param systemMenuDTO 菜单对象
     * @return
     */
    public SystemMenuDTO insertSystemMenu(SystemMenuDTO systemMenuDTO);


    /**
     * 查询应用信息
     *
     * @param id 应用的ID
     * @return SystemAppInfoDTO
     */
    public SystemAppInfoDTO findAppInfoById(String id);


    /**
     * 查询菜单
     *
     * @param code 菜单的编码
     * @return
     */
    public SystemMenuDTO findSystemMenuByCode(String code);


    /**
     * 按菜单编码级联删除菜单：先删全部子级（按钮），删除时联动清理角色/组织/应用授权与用户收藏
     *
     * @param menuCode 菜单编码
     * @return 删除的菜单数量（菜单不存在返回 0）
     */
    public int deleteSystemMenuByCode(String menuCode);


    /**
     * 删除应用与某个流程表单的绑定（system_app_workflow 行）
     *
     * @param workflowFormId 工作流表单ID（动态表单ID）
     * @return 删除的绑定数量
     */
    public int deleteSystemAppWorkflowByFormId(String workflowFormId);


    /**
     * 授权用户自动登录
     *
     * @param systemSocialUserDTO
     * @param request
     * @return
     */
    @PostMapping("/inner/social/sysSocialUser/find")
    public CommonResult<UserInfo> findSocialUser(@RequestBody SystemSocialUserDTO systemSocialUserDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);


    @PostMapping("/inner/social/sysSocialUser/auth/save")
    public CommonResult<UserInfo> addJustAuthSocialUser(@RequestBody SystemSocialUserDTO sysSocialUserDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);


    /**
     * 获取用户信息集合
     * 工作流批量查询用户信息,根据部门
     */
    @PostMapping("/inner/user/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserListByIds(@RequestBody WorkflowUserFilterDTO filterDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取单个用户信息
     * 工作流批量查询用户信息,根据部门
     */
    @GetMapping("/inner/user/find/id")
    @ResponseBody
    public CommonResult<SystemUserDTO> findUserById(@RequestParam("userId") String userId, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据部门
     */
    @PostMapping("/inner/user/dept/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByDept(@RequestBody WorkflowUserFilterDTO filterDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取组织内全部在职用户（全员通知展开用）
     */
    @GetMapping("/inner/user/org/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByOrg(@RequestParam("organizationId") String organizationId, @RequestHeader(Constants.SERVICE_REQUEST) String request);


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据角色
     */
    @PostMapping("/inner/user/role/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByRole(@RequestBody WorkflowUserFilterDTO filterDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据用户组
     */
    @PostMapping("/inner/user/group/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByGroup(@RequestBody WorkflowUserFilterDTO filterDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);


    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据角色编码
     */
    @PostMapping("/inner/user/roleCode/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByRoleCode(@RequestBody WorkflowCodeFilterDTO filterDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取用户信息
     * 工作流批量查询用户信息,根据用户组编码
     */
    @PostMapping("/inner/user/groupCode/find/list")
    @ResponseBody
    public CommonResult<List<SystemUserDTO>> findUserByGroupCode(@RequestBody WorkflowCodeFilterDTO filterDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取部门信息
     * 工作流批量查询部门信息
     */
    @PostMapping("/inner/department/find/list")
    @ResponseBody
    public CommonResult<List<SystemDepartmentDTO>> findDeptListByIds(@RequestBody WorkflowUserFilterDTO filterDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取部门信息
     * 工作流批量查询部门信息
     */
    @GetMapping(value = "/inner/system/department/{id}")
    public CommonResult<SystemDepartmentDTO> findDeptById(@PathVariable("id") String id, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取组织详细信息
     */
    @GetMapping(value = "/inner/system/organization/{id}")
    public CommonResult<SystemOrganizationDTO> findOrganizationById(@PathVariable("id") String id, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 忘记密码，重置密码
     */
    @PutMapping("/inner/system/user/updatePassword")
    public CommonResult<Object> updatePassword(@RequestBody ResetPasswordDTO resetPasswordDTO, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * F4：保存封存口令摘要（旧口径登录成功后引导迁移）
     */
    @PutMapping("/inner/system/user/password/v2")
    public CommonResult<Object> updatePasswordV2(@RequestParam("loginName") String loginName,
                                                 @RequestParam("sealed") String sealed,
                                                 @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 根据字典类型和字典值查询字典数据
     */
    @GetMapping(value = "/inner/system/dictionaryData/{dictType}/{dictValue}")
    public CommonResult<SystemDictionaryDataDTO> findSystemDictionaryDataByTypeAndValue(@PathVariable("dictType") String dictType,
                                                                                        @PathVariable("dictValue") String dictValue,
                                                                                        @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 根据字典类型和字典值查询字典数据,并根据JSON字段存储格式返回JSON数据
     *
     * @param dictType
     * @param dictValue
     * @param request
     * @param organizationId 组织ID
     * @return
     */
    @GetMapping(value = "/inner/system/dictionaryData/json/{dictType}/{dictValue}/{organizationId}")
    public CommonResult<JSONObject> findSelectSystemDictionaryJSONDataByTypeAndValue(@PathVariable("dictType") String dictType,
                                                                                     @PathVariable("dictValue") String dictValue,
                                                                                     @PathVariable("organizationId") String organizationId,
                                                                                     @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 根据字典类型查询字典列表数据
     *
     * @param dictType
     * @param request
     * @return
     */
    @GetMapping(value = "/inner/system/dictionaryData/{dictType}")
    public CommonResult<List<SystemDictionaryDataDTO>> findSystemDictionaryDataByType(@PathVariable("dictType") String dictType,
                                                                                      @RequestHeader(Constants.SERVICE_REQUEST) String request);

    @GetMapping("/inner/system/userGroup/user/{departmentId}/{groupCode}")
    public CommonResult<List<SystemUserGroupUserDTO>> selectGroupUserByDepartmentIdAndGroupCode(@PathVariable("departmentId") String departmentId,
                                                                                                @PathVariable("groupCode") String groupCode,
                                                                                                @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 获取应用配置的流程表单
     */
    @GetMapping("/inner/system/appWorkflow/{appId}")
    public CommonResult<List<String>> findWorkflowByAppId(@PathVariable("appId") String appId, @RequestHeader(Constants.SERVICE_REQUEST) String request);

    /**
     * 新增用户
     *
     * @param systemUser
     * @return
     */
    public SystemUserDTO insertSystemUser(SystemUserDTO systemUser);


    /**
     * 通过第三方ID查询用户
     * 企业微信登录时使用，查询用户。
     *
     * @param openId 第三方ID
     * @param phone  电话
     * @return
     */
    public SystemUserDTO selectUserDTOByOpenIdOrPhone(String openId, String phone);


    /**
     * 通过第三方cropId查询配置
     *
     * @param cropId 第三方ID
     * @return
     */
    public SystemSocialAppConfigDTO selectSocialAppConfigByCropId(String cropId, String source, String appCode);

    /**
     * 修改用户信息
     * 企业微信同步用户信息使用
     * 包括：头像、昵称、性别、邮箱、电话、uid。
     * 其他信息不允许修改。
     *
     * @param user
     * @return
     */
    public int updateUserProfile(SystemUserDTO user);

    /**
     * 处理登录人的session信息,角色、功能、组织、权限范围
     *
     * @param sysUser        用户基础信息
     * @param organizationId 登录的组织
     * @return 用户信息
     */
    public UserInfo getUserInfo(SystemUserDTO sysUser, String organizationId);

    /**
     * 查询用户拥有的部门，主部门及服务部门
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectSystemDeptByUserIdAndRoleId(String userId, String roleId);


    /**
     * 查询部门的所有上级问题
     *
     * @param departmentId 部门ID
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectSystemParentDeptById(String departmentId);


    /**
     * 保存用户的部门的权限
     *
     * @param systemRoleUserDeptDTO 用户
     * @return 用户权限
     */
    public SystemRoleUserDeptDTO saveRoleUserDept(SystemRoleUserDeptDTO systemRoleUserDeptDTO);

    /**
     * 删除角色用户与部门关系信息
     *
     * @param id 角色用户与部门关系主键
     * @return 结果
     */
    public int deleteSystemRoleUserDeptById(String id);


    /**
     * 查询组织下的所有部门
     *
     * @param organizationId 组织ID
     * @return 部门集合
     */
    public List<SystemDepartmentDTO> selectSystemDeptByOrganizationId(String organizationId);


    /**
     * 查询组织下的所有人员
     *
     * @param organizationId 组织ID
     * @return 人员集合
     */
    public List<SystemUserDTO> selectSystemUserByOrganizationId(String organizationId);

    /**
     * 查询组织下的所有角色
     * 工作流设计器选择审批人使用
     *
     * @param organizationId 组织ID
     * @return 角色集合
     */
    public List<SystemRoleDTO> selectSystemRoleByOrganizationId(String organizationId);

    /**
     * 查询组织下的所有用户组
     * 工作流设计器选择审批人使用
     *
     * @param organizationId 组织ID
     * @return 用户组集合
     */
    public List<SystemUserGroupDTO> selectSystemUserGroupByOrganizationId(String organizationId);


    /**
     * 查询部门下的所有人员
     *
     * @param organizationId 组织ID
     * @param departmentId   部门ID
     * @return 人员集合
     */
    public List<SystemUserDTO> selectSystemUserByOrganizationId(String organizationId, String departmentId);

    /**
     * 查询用户角色信息
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 角色与用户关系信息
     */
    public SystemRoleUserDTO selectRoleUserByUserIdAndRoleId(String userId, String roleId);

    /**
     * 获取角色信息
     *
     * @param roleId 角色ID
     * @return 角色信息
     */
    public SystemRoleDTO selectRoleByRoleId(String roleId);


    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode        第三方应用配置的code
     * @param source         来源
     * @param agentId        应用的ID
     * @param cropId         第三方企业ID
     * @param organizationId 组织ID
     * @return 第三方应用配置(小程序)
     */
    public SystemSocialAppConfigDTO selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(String appCode, String source, String agentId, String cropId, String organizationId);


    /**
     * 查询第三方应用配置(小程序)
     *
     * @param appCode        第三方应用配置的code
     * @param source         来源
     * @return 第三方应用配置(小程序)
     */
    public List<SystemSocialAppConfigDTO> selectSysSocialAppConfigListByCodeAndSource(String appCode, String source);


    /**
     * 获取角色数据权限
     *
     * @param userId         用户Id
     * @param organizationId 组织ID
     * @return 角色权限信息
     */
    public List<SystemRoleUserDTO> selectAuthRoleByUserId(String userId, String organizationId);

}
