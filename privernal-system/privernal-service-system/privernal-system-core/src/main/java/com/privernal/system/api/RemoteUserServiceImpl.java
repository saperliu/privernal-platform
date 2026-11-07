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

package com.privernal.system.api;

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.security.service.TokenService;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.domain.*;
import com.privernal.system.dto.*;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.system.filter.*;
import com.privernal.system.mapper.SystemAppMenuMapper;
import com.privernal.system.mapper.SystemMenuMapper;
import com.privernal.system.service.*;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

import static com.privernal.common.core.constant.UserConstants.EXIST_MOBILE;

@Slf4j
@Service
public class RemoteUserServiceImpl implements RemoteUserService {
    @Autowired
    private ISystemUserService systemUserService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISystemDepartmentService systemDepartmentService;

    @Autowired
    private ISystemSocialAppConfigService systemSocialAppConfigService;

    @Autowired
    private ISystemSocialUserService systemSocialUserService;

    @Autowired
    private ISystemOrganizationService systemOrganizationService;

    @Autowired
    private ISystemDictionaryDataService systemDictionaryDataService;

    @Autowired
    private ISystemUserGroupService systemUserGroupService;

    @Autowired
    private ISystemAppWorkflowService systemAppWorkflowService;

    @Autowired
    private ISystemMenuService iSystemMenuService;

    @Autowired
    private ISystemAppInfoService iSystemAppInfoService;

    @Autowired
    private ISystemRoleUserDeptService iSystemRoleUserDeptService;

    @Autowired
    private ISystemRoleUserService iSystemRoleUserService;

    @Autowired
    private ISystemRoleService iSystemRoleService;

    @Autowired
    private SystemAppMenuMapper systemAppMenuMapper;

    @Autowired
    private SystemMenuMapper systemMenuMapper;

    @Autowired
    private com.privernal.common.security.service.PasswordDigestSealer passwordDigestSealer;

    @Override
    public CommonResult<UserInfo> getUserInfo(String username, String request) {
        //登录使用，此时未登录，去掉组织的限制
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            SystemUserDTO sysUser = systemUserService.selectUserByPhone(username);
            if (StringUtils.isNull(sysUser)) {
                // 用户名没有查询到，查询是否是手机号登录
                sysUser = systemUserService.selectUserByLoginName(username);
                if (StringUtils.isNull(sysUser)) {
                    return CommonResult.fail(EXIST_MOBILE, "用户名或密码错误");
                }
            }
            if (DataConstants.DATA_DELETE_FLAG_YES.equals(sysUser.getDeletedFlag())) {
                return CommonResult.fail("用户已被删除");
            }
            if (WorkflowDataStatus.CANCEL.getCode().equals(sysUser.getStatus())) {
                return CommonResult.fail("用户已被禁用");
            }
            UserInfo userInfo = new UserInfo();
            org.springframework.beans.BeanUtils.copyProperties(sysUser, userInfo);
            userInfo.setUserId(sysUser.getId());
            userInfo.setNickName(sysUser.getNickName());
            userInfo.setLoginName(sysUser.getLoginName());
            userInfo.setLoginTime(System.currentTimeMillis());
            userInfo.setOrganizationId(sysUser.getOrganizationId());
            userInfo.setOrganizationName(sysUser.getOrganizationName());
            userInfo.setPassword(sysUser.getPassword());
            // 查询用户拥有的组织, 设置切换后这个组织的默认部门,查询时已经查出
            userInfo.setDepartmentId(sysUser.getDepartmentId());
            return CommonResult.ok(userInfo);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<Object> deleteUser(String id, String request) {
        if (StringUtils.isNotEmpty(id)) {
            int i = systemUserService.deleteSystemUserById(id);
            return CommonResult.ok(i);
        }
        return CommonResult.fail("修改个人信息异常，请联系管理员");
    }

    @Override
    public CommonResult<UserInfo> changeOrganization(String organizationId, String userId, String request) {
        Assert.hasLength(organizationId, "组织ID不能为空");
        SystemUserDTO sysUser = systemUserService.selectUserDTOByOrgIdAndId(userId, organizationId);
        Assert.notNull(sysUser, "切换失败");
        sysUser.setPassword(null);
        UserInfo userInfo = systemUserService.getUserInfo(sysUser, organizationId);
        // 设置信息缓存
        tokenService.setLoginUser(userInfo);
        return CommonResult.ok(userInfo);
    }

    @Override
    public CommonResult<List<SystemDepartmentDTO>> findDeptListByOrgId(SystemDepartmentFilterDTO systemDepartmentFilter, String request) {
        List<SystemDepartment> list = systemDepartmentService.selectDepartmentList(systemDepartmentFilter);
        List<SystemDepartmentDTO> list1 = new ArrayList<SystemDepartmentDTO>();
        if (list != null) {
            list.forEach(item -> {
                SystemDepartmentDTO systemDepartmentDTO = new SystemDepartmentDTO();
                BeanUtils.copyProperties(item, systemDepartmentDTO);
                list1.add(systemDepartmentDTO);
            });
        }
        return CommonResult.ok(list1);
    }

    @Override
    public CommonResult<SystemSocialAppConfigDTO> getAppConfig(String appCode, String request) {
        SystemSocialAppConfig systemSocialAppConfigDTO = systemSocialAppConfigService.selectSysSocialAppConfigByCode(appCode);
        if (systemSocialAppConfigDTO == null) {
            return CommonResult.fail("未找到此应用");
        }
        SystemSocialAppConfigDTO systemSocialAppConfigDTO1 = new SystemSocialAppConfigDTO();
        BeanUtils.copyProperties(systemSocialAppConfigDTO, systemSocialAppConfigDTO1);
        return CommonResult.ok(systemSocialAppConfigDTO1);
    }

    @Override
    public CommonResult<SystemSocialAppConfigDTO> getSocialAppConfig(String appCode, String cropId) {
        log.info("---------getAppConfig-------{}. {} ", appCode, cropId);
        Boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            // 钉钉的免登的使用，这时没有登录，没有组织信息
            OrganizationContextHolder.setIgnore(true);
            SystemSocialAppConfig systemSocialAppConfigDTO = null;
            if (StringUtils.hasText(cropId)) {
                systemSocialAppConfigDTO = systemSocialAppConfigService.selectSysSocialAppConfigByCodeAndCropId(appCode, cropId);
            } else {
                systemSocialAppConfigDTO = systemSocialAppConfigService.selectSysSocialAppConfigByCode(appCode);
            }
            log.info("---------getAppConfig-------systemSocialAppConfigDTO :{}", systemSocialAppConfigDTO);
            if (systemSocialAppConfigDTO == null) {
                return CommonResult.fail("未找到此应用");
            }
            SystemSocialAppConfigDTO systemSocialAppConfigDTO1 = new SystemSocialAppConfigDTO();
            BeanUtils.copyProperties(systemSocialAppConfigDTO, systemSocialAppConfigDTO1);
            return CommonResult.ok(systemSocialAppConfigDTO1);
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<UserInfo> findSocialUser(SystemSocialUserDTO systemSocialUserDTO, String request) {

        SystemUser sysUser = systemSocialUserService.findSocialUser(systemSocialUserDTO);
        if (sysUser == null) {
            return CommonResult.fail("未找到此用户");
        } else {
            return findSocialUser2(sysUser, systemSocialUserDTO);
        }
    }

    /**
     * 登录成功后，返回用户信息，放在token里
     *
     * @param sysUser          用户
     * @param sysSocialUserDTO 社交用户参数
     * @return 用户信息
     */
    private CommonResult<UserInfo> findSocialUser2(SystemUser sysUser, SystemSocialUserDTO sysSocialUserDTO) {

        SystemUserDTO userDTO = new SystemUserDTO();
        org.springframework.beans.BeanUtils.copyProperties(sysUser, userDTO);
        UserInfo userInfo = systemUserService.getUserInfo(userDTO, sysUser.getOrganizationId());

        SystemOrganization systemOrganization = systemOrganizationService.selectSystemOrganizationById(sysUser.getOrganizationId());
        userInfo.setOrganizationName(systemOrganization.getOrganizationName());
        userInfo.setOpenId(sysSocialUserDTO.getOpenId());

        return CommonResult.ok(userInfo);
    }

    @Override
    public CommonResult<UserInfo> addJustAuthSocialUser(SystemSocialUserDTO sysSocialUserDTO, String request) {
        Boolean isIgnore = OrganizationContextHolder.isIgnore();
        try {
            // 第三方的免登接口，还没有注册或登录，所以没有组织信息
            OrganizationContextHolder.setIgnore(true);
            SystemUser sysUser = systemSocialUserService.saveSysSocialUser(sysSocialUserDTO);
            return findSocialUser2(sysUser, sysSocialUserDTO);
        } finally {
            OrganizationContextHolder.setIgnore(isIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<List<SystemUserDTO>> findUserListByIds(WorkflowUserFilterDTO filterDTO, String request) {
        if (StringUtils.isEmpty(filterDTO.getUserList())) {
            return CommonResult.fail(new ArrayList<>(), "用户ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByIdList(filterDTO.getUserList());
            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<SystemUserDTO> findUserById(String userId, String request) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            SystemUser systemUser = systemUserService.selectSystemUserById(userId);
            SystemUserDTO systemUserDTO = new SystemUserDTO();
            BeanUtils.copyProperties(systemUser, systemUserDTO);
            return CommonResult.ok(systemUserDTO);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<List<SystemUserDTO>> findUserByDept(WorkflowUserFilterDTO filterDTO, String request) {
        if (StringUtils.isEmpty(filterDTO.getDeptList())) {
            return CommonResult.fail(new ArrayList<>(), "部门ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByDeptIdList(filterDTO.getDeptList());

            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }

    }

    /**
     * 获取组织内全部在职用户（全员通知展开用）
     */
    @Override
    public CommonResult<List<SystemUserDTO>> findUserByOrg(String organizationId, String request) {
        if (StringUtils.isEmpty(organizationId)) {
            return CommonResult.fail(new ArrayList<>(), "组织ID不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            return CommonResult.ok(systemUserService.selectSystemUserByOrganizationId(organizationId, null));
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<List<SystemUserDTO>> findUserByRole(WorkflowUserFilterDTO filterDTO, String request) {
        if (StringUtils.isEmpty(filterDTO.getRoleList())) {
            return CommonResult.fail(new ArrayList<>(), "角色ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByRoleIdList(filterDTO.getRoleList(), filterDTO.getDepartmentId());
            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<List<SystemUserDTO>> findUserByGroup(WorkflowUserFilterDTO filterDTO, String request) {
        if (StringUtils.isEmpty(filterDTO.getUserGroupList())) {
            return CommonResult.fail(new ArrayList<>(), "用户组ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByGroupIdList(filterDTO.getUserGroupList(), filterDTO.getDepartmentId());
            return CommonResult.ok(systemUserList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<List<SystemUserDTO>> findUserByRoleCode(WorkflowCodeFilterDTO filterDTO, String request) {
        if (StringUtils.isNotEmpty(filterDTO.getCode())) {
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);

                List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByRoleCodeList(filterDTO.getCode(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId());
                return CommonResult.ok(systemUserList);
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }

        } else {
            if (StringUtils.isEmpty(filterDTO.getCodeList())) {
                return CommonResult.fail(new ArrayList<>(), "角色编码集合不能为空");
            }
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                return CommonResult.ok(systemUserService.selectUserDTOByRoleCodes(filterDTO.getCodeList(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId()));
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }
    }

    @Override
    public CommonResult<List<SystemUserDTO>> findUserByGroupCode(WorkflowCodeFilterDTO filterDTO, String request) {
        if (StringUtils.isNotEmpty(filterDTO.getCode())) {

            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                List<SystemUserDTO> systemUserList = systemUserService.selectUserDTOByGroupCodeList(filterDTO.getCode(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId());
                return CommonResult.ok(systemUserList);
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }

        } else {
            if (StringUtils.isEmpty(filterDTO.getCodeList())) {
                return CommonResult.fail(new ArrayList<>(), "用户组编码集合不能为空");
            }
            Boolean oldIgnore = OrganizationContextHolder.isIgnore();
            try {
                OrganizationContextHolder.setIgnore(true);
                return CommonResult.ok(systemUserService.selectUserDTOByGroupCodes(filterDTO.getCodeList(), filterDTO.getDepartmentId(), filterDTO.getOrganizationId()));
            } finally {
                OrganizationContextHolder.setIgnore(oldIgnore);
                OrganizationContextHolder.clear();
            }
        }
    }

    @Override
    public CommonResult<List<SystemDepartmentDTO>> findDeptListByIds(WorkflowUserFilterDTO filterDTO, String request) {
        if (StringUtils.isEmpty(filterDTO.getDeptList())) {
            return CommonResult.fail(new ArrayList<>(), "部门ID集合不能为空");
        }
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<SystemDepartmentDTO> systemDepartmentList = systemDepartmentService.selectSystemDepartmentList(filterDTO.getDeptList());
            return CommonResult.ok(systemDepartmentList);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<SystemDepartmentDTO> findDeptById(String id, String request) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            SystemDepartment systemDepartmentDTO = systemDepartmentService.selectSystemDepartmentById(id);
            SystemDepartmentDTO dto = null;
            if (systemDepartmentDTO != null) {
                dto = new SystemDepartmentDTO();
                BeanUtils.copyProperties(systemDepartmentDTO, dto);
            }
            return CommonResult.ok(dto);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<SystemOrganizationDTO> findOrganizationById(String id, String request) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            SystemOrganization systemOrganization = systemOrganizationService.selectSystemOrganizationById(id);

            SystemOrganizationDTO dto = null;
            if (systemOrganization != null) {
                dto = new SystemOrganizationDTO();
                BeanUtils.copyProperties(systemOrganization, dto);

            }
            return CommonResult.ok(dto);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<Object> updatePassword(ResetPasswordDTO resetPasswordDTO, String request) {
        SystemUserDTO user = systemUserService.selectUserByPhone(resetPasswordDTO.getPhone());
        if (systemUserService.resetUserPwd(user.getLoginName(), SecurityUtils.encryptPassword(resetPasswordDTO.getPassword())) > 0) {
            // F4 双写：挑战登录用的封存线摘要同步更新
            systemUserService.savePasswordV2(user.getLoginName(), passwordDigestSealer.seal(resetPasswordDTO.getPassword()));
            return CommonResult.ok();
        }
        return CommonResult.fail("修改密码异常，请联系管理员");
    }

    @Override
    public CommonResult<Object> updatePasswordV2(String loginName, String sealed, String request) {
        // F4：登录引导迁移写入封存摘要；密封值由 auth 侧主密钥生成，本服务只落库
        return CommonResult.ok(systemUserService.savePasswordV2(loginName, sealed));
    }

    @Override
    public CommonResult<SystemDictionaryDataDTO> findSystemDictionaryDataByTypeAndValue(String dictType, String dictValue, String request) {
        if (StringUtils.isEmpty(dictType) || StringUtils.isEmpty(dictValue)) {
            return CommonResult.fail("字典类型或字典值不能为空");
        }
        SystemDictionaryData systemDictionaryData = systemDictionaryDataService.findSystemDictionaryDataByTypeAndValue(dictType, dictValue);
        if (systemDictionaryData == null) {
            return CommonResult.fail("字典数据不存在");
        }
        SystemDictionaryDataDTO systemDictionaryDataDTO = new SystemDictionaryDataDTO();
        BeanUtils.copyProperties(systemDictionaryData, systemDictionaryDataDTO);
        return CommonResult.ok(systemDictionaryDataDTO);
    }

    @Override
    public CommonResult<JSONObject> findSelectSystemDictionaryJSONDataByTypeAndValue(String dictType, String dictValue, String organizationId, String request) {
        boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            if (StringUtils.isNotEmpty(organizationId)) {
                OrganizationContextHolder.setOrganizationId(organizationId);
            }
            return CommonResult.ok(systemDictionaryDataService.findSelectSystemDictionaryJSONDataByTypeAndValue(dictType, dictValue));
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }

    @Override
    public CommonResult<List<SystemDictionaryDataDTO>> findSystemDictionaryDataByType(String dictType, String request) {
        SystemDictionaryDataFilterDTO systemDictionaryDataFilterDTO = new SystemDictionaryDataFilterDTO();
        systemDictionaryDataFilterDTO.setDictType(dictType);
        List<SystemDictionaryData> list = systemDictionaryDataService.selectSystemDictionaryDataList(systemDictionaryDataFilterDTO);

        if (list == null) {
            return CommonResult.ok(new ArrayList<>());
        }
        List<SystemDictionaryDataDTO> dtoList = new ArrayList<>();
        list.forEach(item -> {
            SystemDictionaryDataDTO dto = new SystemDictionaryDataDTO();
            BeanUtils.copyProperties(item, dto);
            dtoList.add(dto);
        });
        return CommonResult.ok(dtoList);
    }

    @Override
    public CommonResult<List<SystemUserGroupUserDTO>> selectGroupUserByDepartmentIdAndGroupCode(String departmentId, String groupCode, String request) {
        List<SystemUserGroupUserDTO> list = systemUserGroupService.selectGroupUserByDepartmentIdAndGroupCode(departmentId, groupCode);
        return CommonResult.ok(list);
    }

    @Override
    public CommonResult<List<String>> findWorkflowByAppId(String appId, String request) {
        SystemAppWorkflowFilterDTO systemAppWorkflow = new SystemAppWorkflowFilterDTO();
        systemAppWorkflow.setAppId(appId);
        List<SystemAppWorkflowDTO> list = systemAppWorkflowService.selectSystemAppWorkflowList(systemAppWorkflow);
        // 获取list里的所有workflowId
        List<String> workflowIds = list.stream().map(SystemAppWorkflowDTO::getWorkflowFormId).toList();
        return CommonResult.ok(workflowIds);
    }

    @Override
    public SystemMenuDTO insertSystemMenu(SystemMenuDTO systemMenuDTO) {
        SystemMenu systemMenu = new SystemMenu();
        BeanUtils.copyProperties(systemMenuDTO, systemMenu);
        iSystemMenuService.insertSystemMenu(systemMenu);
        systemMenuDTO.setId(systemMenu.getId());
        linkAppMenu(systemMenu);
        return systemMenuDTO;
    }

    /**
     * 菜单带 appId 时同步关联应用菜单（system_app_menu），保证应用路由可查出；幂等
     */
    private void linkAppMenu(SystemMenu systemMenu) {
        if (systemMenu == null || StringUtils.isEmpty(systemMenu.getAppId())
                || StringUtils.isEmpty(systemMenu.getId())) {
            return;
        }
        try {
            LambdaPrivernalWrapper<SystemAppMenu> wrapper = new LambdaPrivernalWrapper<SystemAppMenu>();
            wrapper.eq(SystemAppMenu::getAppId, systemMenu.getAppId());
            wrapper.eq(SystemAppMenu::getMenuId, systemMenu.getId());
            wrapper.eq(SystemAppMenu::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
            if (systemAppMenuMapper.selectCount(wrapper) > 0) {
                return;
            }
            SystemAppMenu appMenu = new SystemAppMenu();
            appMenu.setAppId(systemMenu.getAppId());
            appMenu.setMenuId(systemMenu.getId());
            appMenu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
            appMenu.setOrganizationId(StringUtils.isNotEmpty(systemMenu.getOrganizationId())
                    ? systemMenu.getOrganizationId() : SecurityUtils.getOrganizationId());
            appMenu.setMustMenu(0);
            appMenu.setVisibleMenu(1);
            systemAppMenuMapper.insert(appMenu);
        } catch (Exception e) {
            log.error("菜单 {} 关联应用 {} 失败", systemMenu.getId(), systemMenu.getAppId(), e);
        }
    }

    @Override
    public SystemAppInfoDTO findAppInfoById(String id) {
        SystemAppInfo systemAppInfo = iSystemAppInfoService.selectSystemAppInfoById(id);
        if (systemAppInfo != null) {
            SystemAppInfoDTO systemAppInfoDTO = new SystemAppInfoDTO();
            BeanUtils.copyProperties(systemAppInfo, systemAppInfoDTO);
            return systemAppInfoDTO;
        }
        return null;
    }

    @Override
    public SystemMenuDTO findSystemMenuByCode(String code) {
        SystemMenu systemMenu = iSystemMenuService.selectSystemMenuByCode(code);
        if (systemMenu != null) {
            SystemMenuDTO systemMenuDTO = new SystemMenuDTO();
            BeanUtils.copyProperties(systemMenu, systemMenuDTO);
            return systemMenuDTO;
        }
        return null;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteSystemMenuByCode(String menuCode) {
        if (StringUtils.isEmpty(menuCode)) {
            return 0;
        }
        SystemMenu systemMenu = iSystemMenuService.selectSystemMenuByCode(menuCode);
        if (systemMenu == null || StringUtils.isEmpty(systemMenu.getId())) {
            return 0;
        }
        // 子级（表单生成的 6 个按钮）先删，再删菜单；删除接口内部联动清理
        // 角色/组织/应用授权与用户收藏（deleteSystemMenuById）
        LambdaPrivernalWrapper<SystemMenu> childWrapper = new LambdaPrivernalWrapper<>();
        childWrapper.eq(SystemMenu::getParentId, systemMenu.getId());
        childWrapper.eq(SystemMenu::getDeletedFlag, DataConstants.DATA_DELETE_FLAG_NOT);
        List<SystemMenu> children = systemMenuMapper.selectList(childWrapper);
        for (SystemMenu child : children) {
            iSystemMenuService.deleteSystemMenuById(child.getId());
        }
        return iSystemMenuService.deleteSystemMenuById(systemMenu.getId());
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteSystemAppWorkflowByFormId(String workflowFormId) {
        if (StringUtils.isEmpty(workflowFormId)) {
            return 0;
        }
        SystemAppWorkflowFilterDTO filter = new SystemAppWorkflowFilterDTO();
        filter.setWorkflowFormId(workflowFormId);
        List<SystemAppWorkflowDTO> bindings = systemAppWorkflowService.selectSystemAppWorkflowList(filter);
        int rows = 0;
        for (SystemAppWorkflowDTO binding : bindings) {
            rows += systemAppWorkflowService.deleteSystemAppWorkflowById(binding.getId());
        }
        return rows;
    }

    @Override
    public SystemUserDTO insertSystemUser(SystemUserDTO systemUser) {
        SystemUser systemUser1 = new SystemUser();
        BeanUtils.copyProperties(systemUser, systemUser1);
        systemUserService.insertSystemUser(systemUser1);
        systemUser.setId(systemUser1.getId());
        return systemUser;
    }

    @Override
    public SystemUserDTO selectUserDTOByOpenIdOrPhone(String openId, String phone) {
        return systemUserService.selectUserDTOByOpenIdOrPhone(openId, phone);
    }

    @Override
    public SystemSocialAppConfigDTO selectSocialAppConfigByCropId(String cropId, String source, String appCode) {
        return systemSocialAppConfigService.selectSysSocialAppConfigByCropId(cropId, source, appCode);
    }

    @Override
    public int updateUserProfile(SystemUserDTO user) {
        SystemUser systemUser = new SystemUser();
        BeanUtils.copyProperties(user, systemUser);
        return systemUserService.updateUserProfile(systemUser);
    }
    @Transactional(readOnly = true)
    @Override
    public UserInfo getUserInfo(SystemUserDTO sysUser, String organizationId) {
        return systemUserService.getUserInfo(sysUser, organizationId);
    }
    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartmentDTO> selectSystemDeptByUserIdAndRoleId(String userId, String roleId) {
        return iSystemRoleUserDeptService.selectSystemDeptByUserIdAndRoleId(userId, roleId);
    }
    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartmentDTO> selectSystemParentDeptById(String departmentId) {
        return systemDepartmentService.selectSystemParentDeptById(departmentId);
    }

    @Override
    public SystemRoleUserDeptDTO saveRoleUserDept(SystemRoleUserDeptDTO systemRoleUserDeptDTO) {

        SystemRoleUserDept systemRoleUserDept = new SystemRoleUserDept();
        BeanUtils.copyProperties(systemRoleUserDeptDTO, systemRoleUserDept);

        if (StringUtils.isNotEmpty(systemRoleUserDeptDTO.getId())) {
            iSystemRoleUserDeptService.updateSystemRoleUserDept(systemRoleUserDept);
        } else {
            iSystemRoleUserDeptService.insertSystemRoleUserDept(systemRoleUserDept);
        }

        systemRoleUserDeptDTO.setId(systemRoleUserDept.getId());
        return systemRoleUserDeptDTO;
    }

    @Override
    public int deleteSystemRoleUserDeptById(String id) {
        return iSystemRoleUserDeptService.deleteSystemRoleUserDeptById(id);
    }
    @Transactional(readOnly = true)
    @Override
    public List<SystemDepartmentDTO> selectSystemDeptByOrganizationId(String organizationId) {
        SystemDepartmentFilterDTO systemDepartmentFilter = new SystemDepartmentFilterDTO();
        systemDepartmentFilter.setOrganizationId(organizationId);
        return systemDepartmentService.selectDepartmentByOrganizationId(systemDepartmentFilter);
    }
    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectSystemUserByOrganizationId(String organizationId) {
        return systemUserService.selectSystemUserByOrganizationId(organizationId, null);
    }
    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleDTO> selectSystemRoleByOrganizationId(String organizationId) {
        SystemRoleFilterDTO roleFilter = new SystemRoleFilterDTO();
        roleFilter.setOrganizationId(organizationId);
        List<SystemRole> roles = iSystemRoleService.selectSystemRoleList(roleFilter);
        List<SystemRoleDTO> roleDTOs = new ArrayList<>();
        for (SystemRole role : roles) {
            SystemRoleDTO roleDTO = new SystemRoleDTO();
            BeanUtils.copyProperties(role, roleDTO);
            roleDTOs.add(roleDTO);
        }
        return roleDTOs;
    }
    @Transactional(readOnly = true)
    @Override
    public List<SystemUserGroupDTO> selectSystemUserGroupByOrganizationId(String organizationId) {
        SystemUserGroupFilterDTO groupFilter = new SystemUserGroupFilterDTO();
        groupFilter.setOrganizationId(organizationId);
        List<SystemUserGroup> groups = systemUserGroupService.selectSystemUserGroupList(groupFilter);
        List<SystemUserGroupDTO> groupDTOs = new ArrayList<>();
        for (SystemUserGroup group : groups) {
            SystemUserGroupDTO groupDTO = new SystemUserGroupDTO();
            BeanUtils.copyProperties(group, groupDTO);
            groupDTOs.add(groupDTO);
        }
        return groupDTOs;
    }
    @Transactional(readOnly = true)
    @Override
    public List<SystemUserDTO> selectSystemUserByOrganizationId(String organizationId, String departmentId) {
        return systemUserService.selectSystemUserByOrganizationId(organizationId, departmentId);
    }
    @Transactional(readOnly = true)
    @Override
    public SystemRoleUserDTO selectRoleUserByUserIdAndRoleId(String userId, String roleId) {
        return iSystemRoleUserService.selectRoleUserByUserIdAndRoleId(userId, roleId);
    }

    @Transactional(readOnly = true)
    @Override
    public SystemRoleDTO selectRoleByRoleId(String roleId) {
        SystemRole systemRole = iSystemRoleService.selectSystemRoleById(roleId);
        SystemRoleDTO systemRoleDTO = new SystemRoleDTO();
        BeanUtils.copyProperties(systemRole, systemRoleDTO);
        return systemRoleDTO;
    }
    @Transactional(readOnly = true)
    @Override
    public SystemSocialAppConfigDTO selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(String appCode, String source, String agentId, String cropId, String organizationId) {
        return systemSocialAppConfigService.selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(appCode, source, agentId, cropId, organizationId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemSocialAppConfigDTO> selectSysSocialAppConfigListByCodeAndSource(String appCode, String source) {
        return systemSocialAppConfigService.selectSysSocialAppConfigListByCodeAndSource(appCode, source);
    }

    @Transactional(readOnly = true)
    @Override
    public List<SystemRoleUserDTO> selectAuthRoleByUserId(String userId, String organizationId) {
        return iSystemRoleUserService.selectAuthRoleByUserId(userId, organizationId);
    }
}
