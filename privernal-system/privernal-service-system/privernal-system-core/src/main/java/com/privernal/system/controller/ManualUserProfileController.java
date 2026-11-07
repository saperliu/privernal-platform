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

import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.enums.ProductModuleEnums;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.DataScope;
import com.privernal.common.datascope.context.DataScopeConstants;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.security.service.TokenService;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.ResetPasswordDTO;
import com.privernal.system.dto.SystemFileDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.feign.RemoteFileService;
import com.privernal.system.service.ISystemUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static com.privernal.system.constant.UploadConstants.AVATAR_STORE_PATH;
import static com.privernal.system.constant.UploadConstants.FILE_TYPE_IMAGE;

/**
 * 系统配置Controller
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 系统配置的接口
 */
@Tag(name = "个人信息相关接口")
@RestController
public class ManualUserProfileController extends BaseController {

    @Autowired
    private ISystemUserService systemUserService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private cn.dev33.satoken.dao.SaTokenDao saTokenDao;

    @Autowired
    private com.privernal.common.security.service.PasswordDigestSealer passwordDigestSealer;

    @Autowired
    private RemoteFileService remoteFileService;


    /**
     * 修改用户
     */
    @Log(title = "个人信息", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/user/manual/profile")
    public CommonResult<Object> updateProfile(@RequestBody SystemUser user) {
        user.setId(SecurityUtils.getUserId());
        if (StringUtils.isNotEmpty(user.getPhone())
                && !systemUserService.checkPhoneUnique(user)) {
            return CommonResult.fail("修改用户'" + user.getLoginName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail())
                && !systemUserService.checkEmailUnique(user)) {
            return CommonResult.fail("修改用户'" + user.getLoginName() + "'失败，邮箱账号已存在");
        }
        user.setPassword(null);
        if (systemUserService.updateUserProfile(user) > 0) {
            UserInfo userInfo = tokenService.getLoginUser();
            // 更新缓存用户信息
            userInfo.setNickName(user.getNickName());
            userInfo.setPhone(user.getPhone());
            userInfo.setEmail(user.getEmail());
            userInfo.setGender(user.getGender());
            tokenService.setLoginUser(userInfo);
            return CommonResult.ok();
        }
        return CommonResult.fail("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     */
    @Log(title = "个人信息", operationType = OperationType.UPDATE)
    @PutMapping("/back/system/user/manual/updatePwd")
    public CommonResult<Object> updatePwd(@RequestBody ResetPasswordDTO resetPasswordDTO) {
        String username = SecurityUtils.getLoginName();
        SystemUserDTO user = systemUserService.selectUserByLoginName(username);
        String password = user.getPassword();
        if (!SecurityUtils.matchesPassword(resetPasswordDTO.getOldPassword(), password)) {
            return CommonResult.fail("修改密码失败，旧密码错误");
        }
        if (SecurityUtils.matchesPassword(resetPasswordDTO.getPassword(), password)) {
            return CommonResult.fail("新密码不能与旧密码相同");
        }
        if (systemUserService.resetUserPwd(username, SecurityUtils.encryptPassword(resetPasswordDTO.getPassword())) > 0) {
            // F4 双写：挑战登录用的封存线摘要同步更新（未配主密钥时 seal 返回 null，留空走引导迁移）
            systemUserService.savePasswordV2(username, passwordDigestSealer.seal(resetPasswordDTO.getPassword()));
            return CommonResult.ok();
        }
        return CommonResult.fail("修改密码异常，请联系管理员");
    }

    /**
     * 头像上传
     */
    @Log(title = "用户头像", operationType = OperationType.UPDATE)
    @PostMapping("/back/system/user/manual/avatar")
    public CommonResult<Object> avatar(@RequestParam("avatarfile") MultipartFile file) throws IOException {
        if (!file.isEmpty()) {
            UserInfo userInfo = tokenService.getLoginUser();
            CommonResult<SystemFileDTO> fileResult = remoteFileService.upload(file, ProductModuleEnums.GLOBAL.getCode(), AVATAR_STORE_PATH, userInfo.getUserId(), AVATAR_STORE_PATH, FILE_TYPE_IMAGE, Constants.REQUEST_INNER);
            if (StringUtils.isNull(fileResult) || StringUtils.isNull(fileResult.getData())) {
                return CommonResult.fail("文件服务异常，请联系管理员");
            }
            String url = fileResult.getData().getUrl();
            if (systemUserService.updateUserAvatar(userInfo.getLoginName(), url)) {
                CommonResult<Object> ajax = CommonResult.ok();
                ajax.setData(url);
                // 更新缓存用户头像
                userInfo.setAvatar(url);
                tokenService.setLoginUser(userInfo);
                return ajax;
            }
        }
        return CommonResult.fail("上传图片异常，请联系管理员");
    }

    /**
     * 忘记密码，重置密码
     */
    @ServiceRequest()
    @Log(title = "个人信息", operationType = OperationType.UPDATE)
    @PutMapping("/inner/system/user/updatePassword")
    public CommonResult<Object> updatePassword(@RequestBody ResetPasswordDTO resetPasswordDTO) {
        // S3：按手机号改密必须在本端点自证短信验证码——不能信任调用方已验证，
        // 否则内部头被伪造即可按手机号重置任意账号口令
        if (resetPasswordDTO.getCheckCode() == null
                || !resetPasswordDTO.getCheckCode().equals(
                        saTokenDao.get(com.privernal.common.core.constant.Constants.SMS_CODE_PREFIX + resetPasswordDTO.getPhone()))) {
            throw new com.privernal.common.core.exception.ServiceException("验证码错误或已失效");
        }
        saTokenDao.delete(com.privernal.common.core.constant.Constants.SMS_CODE_PREFIX + resetPasswordDTO.getPhone());
        SystemUserDTO user = systemUserService.selectUserByPhone(resetPasswordDTO.getPhone());
        if (systemUserService.resetUserPwd(user.getLoginName(), SecurityUtils.encryptPassword(resetPasswordDTO.getPassword())) > 0) {
            return CommonResult.ok();
        }
        return CommonResult.fail("修改密码异常，请联系管理员");
    }

    /**
     * 用户注销
     */
    @Log(title = "用户注销", operationType = OperationType.DELETE)
    @PutMapping("/back/system/user/manual/delete/{id}")
    public CommonResult<Object> deleteUser(@PathVariable("id") String id) {
        String userId = SecurityUtils.getUserId();
        if (StringUtils.isNotEmpty(id) && id.equals(userId)) {
            int i = systemUserService.deleteSystemUserById(id);
            return CommonResult.ok(i);
        }
        return CommonResult.fail("修改个人信息异常，请联系管理员");
    }

    /**
     * 个人可授权的数据范围
     */
    @Log(title = "个人可授权的数据范围", operationType = OperationType.QUERY)
    @PutMapping("/back/system/user/manual/data/scope")
    public CommonResult<Object> queryDataScope() {
        if (SecurityUtils.isSuperRoleUser() || SecurityUtils.isSuperPlatformRoleUser()) {
            // 全部数据范围，只有组织管理员和平台超级管理员才会有"全部"这个数据范围
            return CommonResult.ok(DataScopeConstants.SCOPE_LIST);
        } else {
            // 去掉全部这个范围，
            List<DataScope> list = DataScopeConstants.SCOPE_LIST.stream().filter(item -> item.getValue() != 50).toList();
            return CommonResult.ok(list);
        }
    }
}
