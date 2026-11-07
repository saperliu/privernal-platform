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

package com.privernal.system.controller;

import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.constant.UserConstants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.dingtalk.service.DingTalkService;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.common.web.domain.PageListDTO;
import com.privernal.common.wx.service.WorkWxService;
import com.privernal.system.constant.SystemSocialAppConstant;
import com.privernal.system.domain.SystemOrganization;
import com.privernal.system.domain.SystemSocialAppConfig;
import com.privernal.system.dto.*;
import com.privernal.system.filter.SystemOrganizationFilterDTO;
import com.privernal.system.filter.SystemOrganizationMenuFilterDTO;
import com.privernal.system.open.DingService;
import com.privernal.system.service.ISystemDictionaryTypeService;
import com.privernal.system.service.ISystemOrganizationMenuService;
import com.privernal.system.service.ISystemOrganizationService;
import com.privernal.system.service.ISystemSocialAppConfigService;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 组织的接口
 */
@Tag(name = "组织相关接口")
@RestController
public class SystemOrganizationController extends BaseController {

    @Autowired
    private ISystemOrganizationService systemOrganizationService;

    @Autowired
    private DingService dingService;

    @Autowired
    private DingTalkService dingTalkService;

    @Autowired
    private WorkWxService workWxService;

    @Autowired
    private ISystemOrganizationMenuService systemOrganizationMenuService;

    @Autowired
    private ISystemSocialAppConfigService systemSocialAppConfigService;

    @Autowired
    private ISystemDictionaryTypeService systemDictionaryTypeService;

    /**
     * 查询组织列表
     */
    @Operation(summary = "查询组织列表", description = "查询组织列表")
    @PreAuthorize(hasPermi = "system:organization:list")
    @PostMapping("/back/system/organization/list")
    public CommonResult<PageListDTO<Object>> list(@RequestBody SystemOrganizationFilterDTO systemOrganization) {
        startPage(systemOrganization);
        List<SystemOrganization> list = systemOrganizationService.selectSystemOrganizationList(systemOrganization);
        List<SystemOrganizationDTO> dtoList = new ArrayList<>();
        for (SystemOrganization obj : list) {
            SystemOrganizationDTO dto = new SystemOrganizationDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        CommonResult<PageListDTO<Object>> tableResult = getDataTable(list);
        tableResult.getData().setList(dtoList);
        return tableResult;
    }

    /**
     * 获取组织详细信息
     */
    @Operation(summary = "获取组织详细信息", description = "获取组织详细信息")
    @PreAuthorize(hasPermi = "system:organization:query")
    @GetMapping(value = "/back/system/organization/{id}")
    public CommonResult<Object> getInfo(@PathVariable("id") String id) {
        return CommonResult.ok(systemOrganizationService.selectSystemOrganizationById(id));
    }

    /**
     * 新增组织
     */
    @Operation(summary = "新增组织", description = "新增组织")
    @PreAuthorize(hasPermi = "system:organization:add")
    @Log(title = "组织", operationType = OperationType.INSERT)
    @PostMapping(value = "/back/system/organization/save")
    public CommonResult<Object> add(@RequestBody SystemOrganizationCreateDTO systemOrganizationDTO) {
        try {
            SystemOrganizationDTO result = systemOrganizationService.insertSystemOrganization(systemOrganizationDTO);
            return CommonResult.ok(result);
        } catch (ServiceException e) {
            logger.error("create organization  {} {}", e, e.getCode());
            if (UserConstants.NOT_UNIQUE_LOGIN_NAME.toString().equals(e.getCode())) {
                return CommonResult.fail("新增用户'" + systemOrganizationDTO.getLoginName() + "'失败，登录账号已存在");
            } else if (StringUtils.isNotEmpty(systemOrganizationDTO.getPhone())
                    && UserConstants.NOT_UNIQUE_MOBILE.toString().equals(e.getCode())) {
                return CommonResult.fail("新增用户'" + systemOrganizationDTO.getLoginName() + "'失败，手机号码已存在");
            } else if (StringUtils.isNotEmpty(systemOrganizationDTO.getEmail())
                    && UserConstants.NOT_UNIQUE_EMAIL.toString().equals(e.getCode())) {
                return CommonResult.fail("新增用户'" + systemOrganizationDTO.getLoginName() + "'失败，邮箱账号已存在");
            } else if (UserConstants.DATA_SCOPE_MAX.toString().equals(e.getCode())) {
                return CommonResult.fail("新增用户'" + systemOrganizationDTO.getLoginName() + "'失败，授权权限过大");
            }
        }
        return CommonResult.fail("新增组织'" + systemOrganizationDTO.getOrganizationName() + "'失败");
    }

    /**
     * 组织授权
     */
    @Operation(summary = "组织授权", description = "组织授权")
    @PreAuthorize(hasPermi = "system:organization:edit")
    @Log(title = "组织", operationType = OperationType.UPDATE)
    @PostMapping(value = "/back/system/organization/menu/save")
    public CommonResult<Object> authFunction(@RequestBody SystemOrganizationMenuCreateDTO systemOrganizationMenuCreateDTO) {
        Assert.isTrue(SecurityUtils.isSuperPlatformRoleUser(), "没有权限,请联系管理员");
        try {
            systemOrganizationService.saveSystemOrganizationMenu(systemOrganizationMenuCreateDTO);
            return CommonResult.ok("授权组织功能成功");
        } catch (ServiceException e) {
            logger.error("create organization ", e);
        }
        return CommonResult.fail("授权组织功能失败");
    }

    /**
     * 组织授权
     */
    @Operation(summary = "组织授权", description = "组织授权")
    @PreAuthorize(hasPermi = "system:organization:edit")
    @Log(title = "组织", operationType = OperationType.UPDATE)
    @PostMapping(value = "/back/system/organization/app/save")
    public CommonResult<Object> authApp(@RequestBody SystemOrganizationAppCreateDTO systemOrganizationAppCreateDTO) {
        Assert.isTrue(SecurityUtils.isSuperPlatformRoleUser(), "没有权限,请联系管理员");
        try {
            systemOrganizationService.saveSystemOrganizationApp(systemOrganizationAppCreateDTO);
            return CommonResult.ok("授权组织功能成功");
        } catch (ServiceException e) {
            logger.error("create organization ", e);
        }
        return CommonResult.fail("授权组织功能失败");
    }

    /**
     * 查询组织拥有的菜单列表
     */
    @Operation(summary = "查询组织拥有的菜单列表", description = "查询组织拥有的菜单列表")
    @PreAuthorize(hasPermi = "system:organization:list")
    @PostMapping("/back/system/organization/menu/list")
    public CommonResult<Object> menuList(@RequestBody SystemOrganizationMenuFilterDTO systemOrganizationMenu) {
        List<SystemOrganizationMenuDTO> list = systemOrganizationMenuService.selectSystemOrganizationMenuList(systemOrganizationMenu);
        List<SystemOrganizationMenuDTO> dtoList = new ArrayList<>();
        for (SystemOrganizationMenuDTO obj : list) {
            SystemOrganizationMenuDTO dto = new SystemOrganizationMenuDTO();
            BeanUtils.copyProperties(obj, dto, DataConstants.IGNORED_ON_COPY);
            dtoList.add(dto);
        }
        return CommonResult.ok(dtoList);
    }

    /**
     * 修改组织
     */
    @Operation(summary = "修改组织", description = "修改组织")
    @PreAuthorize(hasPermi = "system:organization:edit")
    @Log(title = "组织", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/organization/edit")
    public CommonResult<Object> edit(@RequestBody SystemOrganizationDTO systemOrganizationDTO) {
        SystemOrganization systemOrganization = new SystemOrganization();
        BeanUtils.copyProperties(systemOrganizationDTO, systemOrganization);
        return toAjax(systemOrganizationService.updateSystemOrganization(systemOrganization));
    }

    /**
     * 删除组织
     */
    @Operation(summary = "删除组织", description = "删除组织")
    @PreAuthorize(hasPermi = "system:organization:remove")
    @Log(title = "组织", operationType = OperationType.DELETE)
    @DeleteMapping("/back/system/organization/{ids}")
    public CommonResult<Object> remove(@PathVariable("ids") String[] ids) {
        return toAjax(systemOrganizationService.deleteSystemOrganizationByIds(ids));
    }

    /**
     * 同步组织
     * 钉钉的同步
     */
    @Operation(summary = "钉钉同步组织", description = "钉钉同步组织")
    @PreAuthorize(hasPermi = "system:organization:add")
    @Log(title = "钉钉组织", operationType = OperationType.INSERT)
    @PostMapping("/back/system/organization/syn/{id}")
    public CommonResult<Object> synOrganization(@PathVariable("id") String id) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            //todo 未将同步组织的key 形成配置
            SystemSocialAppConfig systemSocialAppConfig = systemSocialAppConfigService.selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(SystemSocialAppConstant.SYSTEM_DING_APP_CODE, SystemSocialAppConstant.SYSTEM_WORK_WEIXIN, id);
            if (systemSocialAppConfig != null) {
                Assert.isTrue(SecurityUtils.isSuperPlatformRoleUser() || (SecurityUtils.isSuperRoleUser() && id.equals(SecurityUtils.getOrganizationId())), "无权限操作!");
                GetAccessTokenResponseBody token = dingTalkService.getAccessToken(systemSocialAppConfig.getAppId(), systemSocialAppConfig.getSecret());
                dingService.synRootDept(token.getAccessToken(), id, systemSocialAppConfig.getSynEmployee());
            } else {
                return error("钉钉组织未配置钉钉应用!");
            }
        } catch (Exception e) {
            logger.error("钉钉同步组织数据失败 ", e);
            return error("钉钉同步组织数据失败!");
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
        return toAjax(true);
    }

    /**
     * 同步组织
     * 企业微信同步
     */
    @Operation(summary = "企业微信同步组织", description = "企业微信同步组织")
    @PreAuthorize(hasPermi = "system:organization:add")
    @Log(title = "企业微信组织", operationType = OperationType.INSERT)
    @PostMapping("/back/system/organization/syn/wx/{id}")
    public CommonResult<Object> synWorkWxOrganization(@PathVariable("id") String id) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            //todo 未将同步组织的key 形成配置
            SystemSocialAppConfig systemSocialAppConfig = systemSocialAppConfigService.selectSysSocialAppConfigByCodeAndSourceAndOrganizationId(SystemSocialAppConstant.SYSTEM_DING_APP_CODE, SystemSocialAppConstant.SYSTEM_WORK_WEIXIN, id);
            if (systemSocialAppConfig != null) {
                String token = workWxService.getAccessToken(systemSocialAppConfig.getAppId(), systemSocialAppConfig.getSecret());

                Assert.isTrue(StringUtils.isNotEmpty(token), "企业微信获取token失败!");
                Assert.isTrue(SecurityUtils.isSuperPlatformRoleUser() || (SecurityUtils.isSuperRoleUser() && id.equals(SecurityUtils.getOrganizationId())), "无权限操作!");
                dingService.synWorkRootDept(token, id, systemSocialAppConfig.getSource(), systemSocialAppConfig.getSynEmployee());
            } else {
                return error("企业微信组织未配置钉钉应用!");
            }
        } catch (Exception e) {
            logger.error("企业微信同步组织数据失败 ", e);
            return error("企业微信同步组织数据失败!");
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
        return toAjax(true);
    }

    /**
     * 同步字典
     */
    @Operation(summary = "修改组织", description = "修改组织")
    @PreAuthorize(hasPermi = "system:organization:edit")
    @Log(title = "组织", operationType = OperationType.UPDATE)
    @PutMapping(value = "/back/system/organization/init/dict")
    public CommonResult<Object> syncDictionaryType(@RequestBody SystemOrganizationDTO systemOrganizationDTO) {
        return toAjax(systemDictionaryTypeService.initDictionaryType(systemOrganizationDTO.getId(), null));
    }
}
