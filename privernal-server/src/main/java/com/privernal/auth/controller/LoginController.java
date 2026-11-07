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

package com.privernal.auth.controller;

import cn.hutool.core.util.DesensitizedUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.temp.SaTempUtil;
import com.privernal.auth.form.LoginDTO;
import com.privernal.auth.service.PasswordDigestService;
import com.privernal.auth.service.SystemLoginService;
import com.privernal.common.security.annotation.RateLimiter;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.constant.HttpStatus;
import com.privernal.common.core.constant.RoleConstants;
import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.ip.AddressUtils;
import com.privernal.common.core.utils.ip.IpUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.dingtalk.service.DingTalkService;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.security.service.TokenService;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.gateway.service.ValidateCodeService;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.feign.RemoteLogService;
import com.privernal.system.constant.SystemConstant;
import com.privernal.system.dto.*;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.system.filter.SystemDepartmentFilterDTO;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dingtalk.api.response.OapiV2UserGetResponse;
import eu.bitwalker.useragentutils.UserAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthDingTalkRequest;
import me.zhyd.oauth.cache.AuthStateCache;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.privernal.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 登录的接口
 *
 * @author saperliu
 * @date 2022-04-10
 * @Description 登录的接口
 */
@Slf4j
@RestController
@Tag(name = "登录接口", description = "登录PC/APP")
public class LoginController {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SystemLoginService systemLoginService;

    @Autowired
    private PasswordDigestService passwordDigestService;

    @Autowired
    private RemoteLogService remoteLogService;

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private SaTokenDao saTokenDao;

    /** 新-5：初始密码登录拦截开关（true=拒绝初始密码登录，走短信找回改密） */
    @Value("${privernal.auth.block-default-password:true}")
    private boolean blockDefaultPassword;

    private String tokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private String infoUrl = "https://api.weixin.qq.com/sns/userinfo";

    @Autowired
    private ValidateCodeService validateCodeService;

    private static final String CODE = "code";

    private static final String UUID = "id";

    @Value("${security.captcha.enabled}")
    private boolean enabled;

    @Autowired
    private DingTalkService dingTalkService;

    /**
     * 登录接口，
     * 1、密码登录。
     * 2、微信小程序登录。
     * 3、手机验证码登录。
     * 4、手机号+密码登录。
     *
     * @param form
     * @return
     */
    /**
     * F4 挑战-应答第一步：签发一次性 nonce（免登）。主密钥未配置时返回 enabled=false，
     * 客户端据此回退旧口径；migrated=false 时客户端先提交一次旧摘要引导服务端封存（静默），再取新 nonce 重试。
     */
    @Operation(summary = "登录挑战签发", description = "F4：一次性 nonce（60 秒/单次）+ 迁移标志，客户端用其绑定本次登录凭证")
    @RateLimiter(key = "auth-challenge", max = 30, period = 60)
    @GetMapping("/auth/login/challenge")
    @ResponseBody
    public CommonResult<?> loginChallenge(@RequestParam("loginName") String loginName) {
        return CommonResult.ok(systemLoginService.issueChallenge(loginName));
    }

    @Operation(summary = "登录", description = "支持：密码登录，微信小程序登录，手机验证码登录，手机号+密码登录")
    // 限流兜底（网关限流仍是强制项）：30 次/分钟/IP，留足脚本化登录与重试余量
    @RateLimiter(key = "auth-login", max = 30, period = 60)
    @PostMapping(value = "/auth/login")
    public CommonResult<?> login(@RequestBody LoginDTO form) {

        if (StringUtils.isNotEmpty(form.getLoginName()) && StringUtils.isNotEmpty(form.getPassword())) {
            // 先验证验证码，如果打开的话。
//            if (enabled) {
//                validateCodeService.checkCaptcha(form.getCode(), form.getId());
//            }
            // 用户登录
            UserInfo userInfo = systemLoginService.login(form.getLoginName(), form.getPassword(), form.getNonceId());
            if (StringUtils.isNotEmpty(userInfo.getPhone())) {
                // 判断是否是初始密码
                if (form.getPassword().equals(SecurityUtils.getMd5Password(SecurityUtils.getDefaultPassword(userInfo.getPhone())))) {
                    // 新-5：初始密码=手机号后6位，可预测（知道手机号即知口令）。默认拒绝登录，
                    // 改密走"忘记密码"短信链路；存量迁移期可配 privernal.auth.block-default-password=false
                    if (blockDefaultPassword) {
                        throw new ServiceException("当前仍在使用初始密码，请通过『忘记密码』设置新密码后再登录");
                    }
                    userInfo.setInitPassword(true);
                }
            }
            // 获取登录token
            return getToken(form, userInfo);
        } else if (StringUtils.isNotEmpty(form.getPhone()) && StringUtils.isNotEmpty(form.getPassword())) {
            // 先验证验证码，如果打开的话。
//            if (enabled) {
//                validateCodeService.checkCaptcha(form.getCode(), form.getId());
//            }
            // 手机号登录
            UserInfo userInfo = systemLoginService.loginPhone(form.getPhone(), form.getPassword(), form.getNonceId());
            // 获取登录token
            return getToken(form, userInfo);
        } else if (StringUtils.isNotEmpty(form.getPhone()) && StringUtils.isNotEmpty(form.getPhoneCode())) {
            // 手机号验证码登录
            UserInfo userInfo = systemLoginService.loginPhoneCode(form.getPhone(), form.getPhoneCode());
            return getToken(form, userInfo);
        } else {
            // 微信用户登录
            UserInfo userInfo = systemLoginService.weixinLogin(form);
            if (userInfo == null) {
                // 自动登录失败
                return CommonResult.fail(HttpStatus.UNAUTHORIZED, "未注册");
            }
            return getToken(form, userInfo);
        }
    }

    /**
     * 获取token
     *
     * @param form     登录信息
     * @param userInfo 查询到的用户信息
     * @return
     */
    private CommonResult<Object> getToken(LoginDTO form, UserInfo userInfo) {

        // 组织编码随会话下发：动态建表的表名要用企业组织编码做前缀，建表链路不再回查组织
        userInfo.setOrganizationCode(systemLoginService.resolveOrganizationCode(userInfo.getOrganizationId()));

        Map<String, Object> token;
        // 获取登录token
        if (userInfo.getUserServiceOrgDTOList() != null && userInfo.getUserServiceOrgDTOList().size() > 1 && form != null && form.getLoginType() == LoginDTO.LOGIN_TYPE_PC) {
            // PC端在登录时要选择组织，其它端不选择。选完组织走 changeOrganization，那时再设业务库名。
            token = tokenService.createTempToken(userInfo);
        } else {
            token = tokenService.createToken(userInfo);
            systemLoginService.resetOrganizationDatabase(userInfo);
        }

        return CommonResult.ok(token);
    }

    /**
     * 登录时选择组织的页面的接口
     *
     * @param orgId              组织ID
     * @param httpServletRequest 请求
     * @return
     */
    @Operation(summary = "登录时选择组织的页面的接口")
    @PutMapping("/auth/organization/{id}")
    public CommonResult<?> changeOrganization(@PathVariable("id") String orgId, HttpServletRequest httpServletRequest) {
        if (StringUtils.isNotEmpty(orgId)) {
            // 验证 组织ID是否正确
            // 解析 token 获取 value，并转换为指定类型
            String token = SaTempUtil.parseToken(SecurityUtils.getToken(httpServletRequest), String.class);
            UserInfo userInfo = JSON.parseObject(token, UserInfo.class);

            if (userInfo != null) {
                if (userInfo.getRoleKeys() != null) {
                    for (String key : userInfo.getRoleKeys()) {
                        // 如果是超级管理员直接创建token
                        if (RoleConstants.SUPER_PLATFORM_ROLE_ADMIN.equals(key)) {

                            userInfo.setOrganizationId(orgId);
                            // 设置默认的部门
                            SystemDepartmentFilterDTO filterDTO = new SystemDepartmentFilterDTO();
                            filterDTO.setOrganizationId(orgId);
                            CommonResult<List<SystemDepartmentDTO>> deptList = remoteUserService.findDeptListByOrgId(filterDTO, Constants.REQUEST_INNER);
                            if (deptList.getCode() == CommonResult.SUCCESS) {
                                if (deptList.getData() != null) {
                                    userInfo.setDepartmentId(deptList.getData().get(0).getId());
                                } else {
                                    log.error(" find dept by orgId  0, {}", deptList.getMessage());
                                }
                            } else {
                                log.error(" find dept by orgId error, {}", deptList.getMessage());
                            }
                            // 获取登录token
                            return CommonResult.ok(systemLoginService.createTokenWithOrgSession(userInfo));
                        }
                    }
                }
                // 非超级管理员，需要切换组织，重新查询组织的权限。
                UserInfo user = systemLoginService.changOrganization(orgId, userInfo.getUserId(), userInfo.getLoginName());
                // 获取登录token（与登录后切换组织走同一条链路）
                return CommonResult.ok(systemLoginService.createTokenWithOrgSession(user));
            }
        }
        return CommonResult.fail("登录组织失败");
    }

    /**
     * 登录后，切换组织
     *
     * @param orgId              组织ID
     * @param httpServletRequest 请求
     * @return
     */
    @Operation(summary = "登录后，切换组织")
    @PutMapping("/auth/change/organization/{id}")
    public CommonResult<?> changeLoginOrganization(@PathVariable("id") String orgId, HttpServletRequest httpServletRequest) {
        if (StringUtils.isNotEmpty(orgId)) {
            // 验证 组织ID是否正确
            UserInfo userInfo = SecurityUtils.getUserInfo();

            if (userInfo != null) {
                if (userInfo.getRoleKeys() != null) {
                    for (String key : userInfo.getRoleKeys()) {
                        // 如果是超级管理员直接创建token
                        if (RoleConstants.SUPER_PLATFORM_ROLE_ADMIN.equals(key)) {

                            userInfo.setOrganizationId(orgId);
                            // 设置默认的部门
                            SystemDepartmentFilterDTO filterDTO = new SystemDepartmentFilterDTO();
                            filterDTO.setOrganizationId(orgId);
                            CommonResult<List<SystemDepartmentDTO>> deptList = remoteUserService.findDeptListByOrgId(filterDTO, Constants.REQUEST_INNER);
                            if (deptList.getCode() == CommonResult.SUCCESS) {
                                if (deptList.getData() != null) {
                                    userInfo.setDepartmentId(deptList.getData().get(0).getId());
                                } else {
                                    log.error(" find dept by orgId  0, {}", deptList.getMessage());
                                }
                            } else {
                                log.error(" find dept by orgId error, {}", deptList.getMessage());
                            }
                            // 获取登录token
                            return CommonResult.ok(systemLoginService.createTokenWithOrgSession(userInfo));
                        }
                    }
                }
                // 非超级管理员，需要切换组织，重新查询组织的权限。
                UserInfo user = systemLoginService.changOrganization(orgId, userInfo.getUserId(), userInfo.getLoginName());
                // 获取登录token
                return CommonResult.ok(systemLoginService.createTokenWithOrgSession(user));
            }
        }
        return CommonResult.fail("登录组织失败");
    }

    /**
     * 退出系统
     *
     * @param request 请求
     * @return
     */
    @Operation(summary = "退出")
    @DeleteMapping("/auth/logout")
    public CommonResult<?> logout(HttpServletRequest request) {
        UserInfo userInfo = tokenService.getLoginUser();
        if (StringUtils.isNotNull(userInfo)) {
            String username = userInfo.getLoginName();
            // 删除用户缓存记录
            tokenService.delLoginUser(userInfo.getToken());
            // 记录用户退出日志
            systemLoginService.logout(username, userInfo.getNickName());
        }
        return CommonResult.ok();
    }

    /**
     * 刷新令牌
     *
     * @param request
     * @return
     */
    /**
     * A2：APP 内嵌 PC 页面的令牌握手第一步——登录态生成一次性行权码（60 秒/单次）。
     * 需登录（不在免登清单），ek 只对持有有效会话的 APP 可得
     */
    @Operation(summary = "web-view 令牌握手：生成行权码")
    @PostMapping("/auth/web/handshake")
    @ResponseBody
    public CommonResult<?> webHandshake() {
        UserInfo userInfo = tokenService.getLoginUser();
        if (userInfo == null || StringUtils.isEmpty(userInfo.getToken())) {
            return CommonResult.fail("登录态缺失");
        }
        return CommonResult.ok(systemLoginService.createWebHandshake(userInfo.getToken()), "ok");
    }

    /**
     * A2 第二步：PC 页面凭行权码一次性兑换 token。免登（行权码本身就是凭证），
     * 兑换即作废；与 R3/S8 相同的缓存口径，无暴力枚举面（32 位随机码 + 60 秒）
     */
    @Operation(summary = "web-view 令牌握手：兑换 token")
    // 行权码本身 60 秒/单次，限流只防兑换端点被刷
    @RateLimiter(key = "auth-ek-exchange", max = 30, period = 60)
    @PostMapping("/auth/web/exchange")
    @ResponseBody
    public CommonResult<?> webExchange(@RequestBody Map<String, String> body) {
        String ek = body == null ? null : body.get("ek");
        String token = systemLoginService.exchangeWebHandshake(ek);
        if (StringUtils.isEmpty(token)) {
            return CommonResult.fail("握手码无效或已过期");
        }
        return CommonResult.ok(token, "ok");
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/auth/refresh")
    public CommonResult<?> refresh(HttpServletRequest request) {
        UserInfo userInfo = tokenService.getLoginUser();
        if (StringUtils.isNotNull(userInfo)) {
            // 刷新令牌有效期
            tokenService.refreshToken(userInfo);
            return CommonResult.ok();
        }
        return CommonResult.ok();
    }

    /**
     * 注销帐号
     *
     * @return
     */
    @Operation(summary = "注销账号")
    @PostMapping("/auth/account/cancel")
    public CommonResult<?> logOff() {
        UserInfo userInfo = tokenService.getLoginUser();
        if (StringUtils.isNotNull(userInfo)) {
            // 刷新令牌有效期
            systemLoginService.logOff(userInfo);
            return CommonResult.ok();
        }
        return CommonResult.ok();
    }

    /**
     * ①
     * <p>
     * 配合 ② 实现扫码登录
     * 获取 第三方授权的 code
     *
     * @param source  来源
     * @param appCode 自定义的应用的编码，全局唯一
     * @return 授权访问的URL
     */
    @Operation(summary = "授权接口")
    @GetMapping("/auth/render/{source}/{appCode}")
    @ResponseBody
    public Object renderAuth(@PathVariable("source") String source, @PathVariable("appCode") String appCode) {
        log.info("进入render：" + source);
        AuthRequest authRequest = getAuthRequest(source, appCode);
        String state = AuthStateUtils.createState();
        // S20：state 先入库再下发，回调时由 AuthStateCache 比对，防授权 CSRF
        authStateCache().cache(state, state);
        String authorizeUrl = authRequest.authorize(state);
        log.info(authorizeUrl);
        return CommonResult.ok(authorizeUrl, "获取授权url");
    }

    /** OAuth2 state 有效期：5 分钟，过期即拒绝 */
    private static final long AUTH_STATE_TIMEOUT_SECONDS = 300;

    private static final String AUTH_STATE_PREFIX = "auth:state:";

    /**
     * JustAuth state 缓存（S20）：render 时写入、{@code login(callback)} 时比对。
     * ignoreCheckState(true) 会让任何人拿着伪造 code/state 走完授权回调（登录 CSRF）。
     */
    private AuthStateCache authStateCache() {
        return new AuthStateCache() {
            @Override
            public void cache(String key, String value) {
                saTokenDao.set(AUTH_STATE_PREFIX + key, value, AUTH_STATE_TIMEOUT_SECONDS);
            }

            @Override
            public void cache(String key, String value, long timeout) {
                saTokenDao.set(AUTH_STATE_PREFIX + key, value, timeout);
            }

            @Override
            public boolean containsKey(String key) {
                return StringUtils.isNotEmpty(saTokenDao.get(AUTH_STATE_PREFIX + key));
            }

            @Override
            public String get(String key) {
                return saTokenDao.get(AUTH_STATE_PREFIX + key);
            }
        };
    }

    /**
     * ②
     * <p>
     * 配合 ① 实现扫码登录。
     * ① 是请求显示二维码的接口
     * ② 是扫码得到code 然后请求的地址，并且需要配置到对应平台【企微、钉钉等】的应用的回调地址里。
     * oauth平台中配置的授权回调地址，以本项目为例，在创建github授权应用时的回调地址应为：http://127.0.0.1/auth/callback/github
     *
     * @param source  来源
     * @param appCode 自定义的应用的编码，全局唯一
     */
    @Operation(summary = "授权回调接口")
    @GetMapping("/auth/callback/{source}/{appCode}")
    public CommonResult<?> loginCallback(@PathVariable("source") String source, @PathVariable("appCode") String appCode, @RequestParam(value = "code", required = false) String code, @RequestParam(value = "state", required = false) String state, @RequestParam(value = "identityToken", required = false) String identityToken, AuthCallback callback) {
        log.info("callback source  {}  callback params {}", source, JSONObject.toJSONString(callback));
        try {

            if (SystemConstant.SYSTEM_GZH.equals(appCode)) {
                CommonResult<SystemSocialAppConfigDTO> appConfigDTOR = remoteUserService.getAppConfig(SystemConstant.SYSTEM_GZH, Constants.REQUEST_INNER);
                log.info("  get SysSocialAppConfigDTO  {} ", JSONObject.toJSONString(appConfigDTOR));
                if (appConfigDTOR.getCode() == CommonResult.SUCCESS) {
                    SystemSocialAppConfigDTO socialAppConfigDTO = appConfigDTOR.getData();

                    // 公众号授权获取信息, 为了发公众号消息
                    String result = HttpUtil.get(tokenUrl + "?grant_type=authorization_code&code=" + code + "&appid=" + socialAppConfigDTO.getAppId() + "&secret=" + socialAppConfigDTO.getSecret());
                    // 不打印完整 access_token 响应（含 access_token/secret 明文）
                    log.info("  get auth  access token resp, hasOpenid={} ", JSONObject.parseObject(result).containsKey("openid"));
                    JSONObject obj = JSONObject.parseObject(result);
                    String token = obj.getString("access_token");
                    String openid = obj.getString("openid");
                    if (StringUtils.isNotEmpty(token)) {

                        String resultInfo = HttpUtil.get(infoUrl + "?access_token=" + token + "&openid=" + openid + "&lang=zh_CN");
                        log.info("  get auth  token user info   {} ", resultInfo);
                        JSONObject objInfo = JSONObject.parseObject(resultInfo);
                        SystemSocialUserDTO sysSocialUserDTO = new SystemSocialUserDTO();
                        sysSocialUserDTO.setNickName(objInfo.getString("nickname"));
                        sysSocialUserDTO.setGender(objInfo.getInteger("sex") + "");
                        sysSocialUserDTO.setAvatarUrl(objInfo.getString("headimgurl"));
                        sysSocialUserDTO.setSource(source);
                        sysSocialUserDTO.setUnionId(objInfo.getString("unionid"));
                        sysSocialUserDTO.setOpenId(objInfo.getString("openid"));
                        sysSocialUserDTO.setUid(objInfo.getString("unionid"));
                        sysSocialUserDTO.setUuid(objInfo.getString("unionid"));
                        sysSocialUserDTO.setAppCode(appCode);
                        sysSocialUserDTO.setOrganizationId(socialAppConfigDTO.getOrganizationId());

                        CommonResult<UserInfo> userInfoCommonResult = remoteUserService.addJustAuthSocialUser(sysSocialUserDTO, Constants.REQUEST_INNER);
                        if (userInfoCommonResult.getCode() == CommonResult.SUCCESS) {
                            //检查用户是否合法
                            final UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"));
                            final String ip = IpUtils.getIpAddr(ServletUtils.getRequest());
                            String address = AddressUtils.getRealAddressByIP(ip);
                            // 获取客户端操作系统
                            String os = userAgent.getOperatingSystem().getName();
                            // 获取客户端浏览器
                            String browser = userAgent.getBrowser().getName();
                            SystemLogLoginDTO systemLogLoginDTO = new SystemLogLoginDTO();
                            systemLogLoginDTO.setLoginName(objInfo.getString("openid"));
                            systemLogLoginDTO.setNickName(objInfo.getString("nickname"));
                            systemLogLoginDTO.setStatus(Constants.LOGIN_SUCCESS_STATUS);
                            systemLogLoginDTO.setLoginIp(ip);
                            systemLogLoginDTO.setLoginLocation(address);
                            systemLogLoginDTO.setBrowser(browser);
                            systemLogLoginDTO.setOs(os);
                            remoteLogService.saveLoginLog(systemLogLoginDTO, Constants.REQUEST_INNER);
                            return CommonResult.ok("", "授权成功!");
                        }
                    }
                } else {
                    return CommonResult.fail(40001, "信息错误,请联系管理员!");
                }
            }
            if (SystemConstant.SYSTEM_APPLE.equals(appCode)) {
                CommonResult<JSONObject> commonResult = systemLoginService.getAppleUserInfo(identityToken);
                if (commonResult.getCode() == CommonResult.SUCCESS) {
                    JSONObject jsonObject = commonResult.getData();
                    SystemSocialUserDTO sysSocialUserDTO = new SystemSocialUserDTO();
                    sysSocialUserDTO.setNickName(appCode);
                    sysSocialUserDTO.setSource(source);
                    sysSocialUserDTO.setUnionId(jsonObject.getString("sub"));
                    sysSocialUserDTO.setOpenId(jsonObject.getString("sub"));
                    sysSocialUserDTO.setUid(jsonObject.getString("sub"));
                    sysSocialUserDTO.setUuid(jsonObject.getString("sub"));
                    sysSocialUserDTO.setEmail(jsonObject.getString("email"));
                    // 获取 refresh token
                    String token = systemLoginService.getAppleRefreshToken(callback.getCode());
                    Assert.hasLength(token, "授权失败");
                    sysSocialUserDTO.setRefreshToken(token);
                    sysSocialUserDTO.setAppCode(appCode);
                    UserInfo userInfo = systemLoginService.socialLogin(sysSocialUserDTO);
                    if (userInfo != null) {
                        userInfo.setIosRefreshToken(token);
                        // 获取登录token
                        systemLoginService.recordLoginInfo(sysSocialUserDTO.getOpenId(), userInfo.getNickName(), Constants.LOGIN_SUCCESS, "登录成功", userInfo.getOrganizationId(), userInfo.getDepartmentId());
                        return CommonResult.ok(systemLoginService.createTokenWithOrgSession(userInfo));
                    } else {
                        systemLoginService.recordLoginInfo(sysSocialUserDTO.getOpenId(), userInfo.getNickName(), Constants.LOGIN_FAIL, "登录失败", SystemLoginService.organizationId, SystemLoginService.organizationId);
                        return CommonResult.fail("未授权的应用用户,联系管理员!");
                    }
                } else {
                    return CommonResult.fail(40001, "登录失败,请联系管理员!");
                }
            } else {
                AuthRequest authRequest = getAuthRequest(source, appCode);
                AuthResponse<AuthUser> response = authRequest.login(callback);
                log.info(" AuthRequest  source  [{}]  callback params {}", source, JSONObject.toJSONString(response));
                if (response.ok()) {

                    AuthUser authUser = response.getData();
                    SystemSocialUserDTO sysSocialUserDTO = new SystemSocialUserDTO();
                    sysSocialUserDTO.setNickName(authUser.getNickname());
                    sysSocialUserDTO.setSource(authUser.getSource());
                    sysSocialUserDTO.setUnionId(authUser.getToken().getUnionId());
                    sysSocialUserDTO.setOpenId(authUser.getToken().getOpenId());
                    sysSocialUserDTO.setUid(authUser.getRawUserInfo().getString("dingId"));
                    sysSocialUserDTO.setUuid(authUser.getUuid());
                    sysSocialUserDTO.setAppCode(appCode);
                    UserInfo userInfo = systemLoginService.socialLogin(sysSocialUserDTO);
                    if (userInfo != null) {
                        // 获取登录token
                        systemLoginService.recordLoginInfo(sysSocialUserDTO.getOpenId(), authUser.getNickname(), Constants.LOGIN_SUCCESS, "登录成功", userInfo.getOrganizationId(), userInfo.getDepartmentId());
                        return CommonResult.ok(systemLoginService.createTokenWithOrgSession(userInfo));
                    } else {
                        systemLoginService.recordLoginInfo(sysSocialUserDTO.getOpenId(), authUser.getNickname(), Constants.LOGIN_FAIL, "登录失败", SystemLoginService.organizationId, SystemLoginService.organizationId);
                        return CommonResult.fail("未授权的应用用户,联系管理员!");
                    }
                }
            }
        } catch (Exception e) {
            log.error("进入callback  Exception ：", e);
        }
        return CommonResult.fail("授权登录失败!");
    }

    /**
     * 根据具体的授权来源，获取授权请求工具类
     *
     * @param source
     * @return
     */
    private AuthRequest getAuthRequest(String source, String appCode) {
        AuthRequest authRequest = null;
        // 获取前端传过来的数据end
        CommonResult<SystemSocialAppConfigDTO> socialAppConfigDTO = remoteUserService.getAppConfig(appCode, Constants.REQUEST_INNER);
        if (socialAppConfigDTO.getData() != null) {
            switch (source.toLowerCase()) {
                case "dingtalk":
                    authRequest = new AuthDingTalkRequest(AuthConfig.builder()
                            .clientId(socialAppConfigDTO.getData().getAppId())
                            .clientSecret(socialAppConfigDTO.getData().getSecret())
                            .redirectUri(socialAppConfigDTO.getData().getAppUrl())
                            .build(), authStateCache());
                    break;
//            case "baidu":
//                authRequest = new AuthBaiduRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/baidu")
//                        .scopes(Arrays.asList(
//                                AuthBaiduScope.BASIC.getScope(),
//                                AuthBaiduScope.SUPER_MSG.getScope(),
//                                AuthBaiduScope.NETDISK.getScope()
//                        ))
////                        .clientId("")
////                        .clientSecret("")
////                        .redirectUri("http://localhost:9001/oauth/baidu/callback")
//                        .build());
//                break;
//            case "github":
//                authRequest = new AuthGithubRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/github")
//                        .scopes(AuthScopeUtils.getScopes(AuthGithubScope.values()))
//                        // 针对国外平台配置代理
//                        .httpConfig(HttpConfig.builder()
//                                .timeout(15000)
//                                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10080)))
//                                .build())
//                        .build(), stateRedisCache);
//                break;
//            case "gitee":
//                authRequest = new AuthGiteeRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://127.0.0.1:8443/oauth/callback/gitee")
//                        .scopes(AuthScopeUtils.getScopes(AuthGiteeScope.values()))
//                        .build(), stateRedisCache);
//                break;
//            case "weibo":
//                authRequest = new AuthWeiboRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/weibo")
//                        .scopes(Arrays.asList(
//                                AuthWeiboScope.EMAIL.getScope(),
//                                AuthWeiboScope.FRIENDSHIPS_GROUPS_READ.getScope(),
//                                AuthWeiboScope.STATUSES_TO_ME_READ.getScope()
//                        ))
//                        .build());
//                break;
//            case "coding":
//                authRequest = new AuthCodingRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/coding")
//                        .domainPrefix("")
//                        .scopes(Arrays.asList(
//                                AuthCodingScope.USER.getScope(),
//                                AuthCodingScope.USER_EMAIL.getScope(),
//                                AuthCodingScope.USER_PHONE.getScope()
//                        ))
//                        .build());
//                break;
//            case "oschina":
//                authRequest = new AuthOschinaRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/oschina")
//                        .build());
//                break;
//            case "alipay":
//                // 支付宝在创建回调地址时，不允许使用localhost或者127.0.0.1，所以这儿的回调地址使用的局域网内的ip
//                authRequest = new AuthAlipayRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .alipayPublicKey("")
//                        .redirectUri("https://www.zhyd.me/oauth/callback/alipay")
//                        .build());
//                break;
//            case "qq":
//                authRequest = new AuthQqRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/qq")
//                        .build());
//                break;
//            case "wechat_open":
//                authRequest = new AuthWeChatOpenRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://www.zhyd.me/oauth/callback/wechat")
//                        .build());
//                break;
//            case "csdn":
//                authRequest = new AuthCsdnRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/csdn")
//                        .build());
//                break;
//            case "taobao":
//                authRequest = new AuthTaobaoRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/taobao")
//                        .build());
//                break;
//            case "google":
//                authRequest = new AuthGoogleRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/google")
//                        .scopes(AuthScopeUtils.getScopes(AuthGoogleScope.USER_EMAIL, AuthGoogleScope.USER_PROFILE, AuthGoogleScope.USER_OPENID))
//                        // 针对国外平台配置代理
//                        .httpConfig(HttpConfig.builder()
//                                .timeout(15000)
//                                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10080)))
//                                .build())
//                        .build());
//                break;
//            case "facebook":
//                authRequest = new AuthFacebookRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("https://justauth.cn/oauth/callback/facebook")
//                        .scopes(AuthScopeUtils.getScopes(AuthFacebookScope.values()))
//                        // 针对国外平台配置代理
//                        .httpConfig(HttpConfig.builder()
//                                .timeout(15000)
//                                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10080)))
//                                .build())
//                        .build());
//                break;
//            case "douyin":
//                authRequest = new AuthDouyinRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/douyin")
//                        .build());
//                break;
//            case "linkedin":
//                authRequest = new AuthLinkedinRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/linkedin")
//                        .scopes(null)
//                        .build());
//                break;
//            case "microsoft":
//                authRequest = new AuthMicrosoftRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/microsoft")
//                        .scopes(Arrays.asList(
//                                AuthMicrosoftScope.USER_READ.getScope(),
//                                AuthMicrosoftScope.USER_READWRITE.getScope(),
//                                AuthMicrosoftScope.USER_READBASIC_ALL.getScope(),
//                                AuthMicrosoftScope.USER_READ_ALL.getScope(),
//                                AuthMicrosoftScope.USER_READWRITE_ALL.getScope(),
//                                AuthMicrosoftScope.USER_INVITE_ALL.getScope(),
//                                AuthMicrosoftScope.USER_EXPORT_ALL.getScope(),
//                                AuthMicrosoftScope.USER_MANAGEIDENTITIES_ALL.getScope(),
//                                AuthMicrosoftScope.FILES_READ.getScope()
//                        ))
//                        .build());
//                break;
//            case "mi":
//                authRequest = new AuthMiRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/mi")
//                        .build());
//                break;
//            case "toutiao":
//                authRequest = new AuthToutiaoRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/toutiao")
//                        .build());
//                break;
//            case "teambition":
//                authRequest = new AuthTeambitionRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://127.0.0.1:8443/oauth/callback/teambition")
//                        .build());
//                break;
//            case "pinterest":
//                authRequest = new AuthPinterestRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://127.0.0.1:8443/oauth/callback/pinterest")
//                        // 针对国外平台配置代理
//                        .httpConfig(HttpConfig.builder()
//                                .timeout(15000)
//                                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10080)))
//                                .build())
//                        .build());
//                break;
//            case "renren":
//                authRequest = new AuthRenrenRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://127.0.0.1:8443/oauth/callback/teambition")
//                        .build());
//                break;
//            case "stack_overflow":
//                authRequest = new AuthStackOverflowRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("((")
//                        .redirectUri("http://localhost:8443/oauth/callback/stack_overflow")
//                        .stackOverflowKey("")
//                        .build());
//                break;
//            case "huawei":
//                authRequest = new AuthHuaweiRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://127.0.0.1:8443/oauth/callback/huawei")
//                        .scopes(Arrays.asList(
//                                AuthHuaweiScope.BASE_PROFILE.getScope(),
//                                AuthHuaweiScope.MOBILE_NUMBER.getScope(),
//                                AuthHuaweiScope.ACCOUNTLIST.getScope(),
//                                AuthHuaweiScope.SCOPE_DRIVE_FILE.getScope(),
//                                AuthHuaweiScope.SCOPE_DRIVE_APPDATA.getScope()
//                        ))
//                        .build());
//                break;
//            case "wechat_enterprise":
//                authRequest = new AuthWeChatEnterpriseQrcodeRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://justauth.cn/oauth/callback/wechat_enterprise")
//                        .agentId("1000003")
//                        .build());
//                break;
//            case "kujiale":
//                authRequest = new AuthKujialeRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/kujiale")
//                        .build());
//                break;
//            case "gitlab":
//                authRequest = new AuthGitlabRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/gitlab")
//                        .scopes(AuthScopeUtils.getScopes(AuthGitlabScope.values()))
//                        .build());
//                break;
//            case "meituan":
//                authRequest = new AuthMeituanRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/meituan")
//                        .build());
//                break;
//            case "eleme":
//                authRequest = new AuthElemeRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://dblog-web.zhyd.me/oauth/callback/eleme")
//                        .build());
//                break;
//            case "mygitlab":
//                authRequest = new AuthMyGitlabRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://127.0.0.1:8443/oauth/callback/mygitlab")
//                        .build());
//                break;
//            case "twitter":
//                authRequest = new AuthTwitterRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://127.0.0.1:8443/oauth/callback/twitter")
//                        // 针对国外平台配置代理
//                        .httpConfig(HttpConfig.builder()
//                                .timeout(15000)
//                                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10080)))
//                                .build())
//                        .build());
//                break;
//            case "wechat_mp":
//                authRequest = new AuthWeChatMpRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("")
//                        .build());
//                break;
//            case "aliyun":
//                authRequest = new AuthAliyunRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/aliyun")
//                        .build());
//                break;
//            case "xmly":
//                authRequest = new AuthXmlyRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/xmly")
//                        .build());
//                break;
//            case "feishu":
//                authRequest = new AuthFeishuRequest(AuthConfig.builder()
//                        .clientId("")
//                        .clientSecret("")
//                        .redirectUri("http://localhost:8443/oauth/callback/feishu")
//                        .build());
//                break;
                default:
                    break;
            }
            if (null == authRequest) {
                throw new AuthException("未获取到有效的Auth配置");
            }
            return authRequest;
        }
        return null;
    }

    /**
     * 短信验证码的发送
     *
     * @param phone 手机号
     * @return
     */
    @Operation(summary = "短信验证码的发送")
    // 同一手机号已有 60s/10天 服务端频控，这里补 IP 维度防短信轰炸
    @RateLimiter(key = "auth-sms", max = 5, period = 60)
    @PutMapping("/auth/sms/{phone}")
    @ResponseBody
    public CommonResult<?> sendSms(@PathVariable("phone") String phone) {
        log.info("send sms code, phone={}", DesensitizedUtil.mobilePhone(phone));
        Assert.hasLength(phone, "请填写手机号");
        return systemLoginService.getSmsCode(phone);
    }

    /**
     * 忘记密码
     *
     * @param resetPasswordDTO 重置密码
     * @return
     */
    @Operation(summary = "忘记密码")
    @RateLimiter(key = "auth-reset-pwd", max = 10, period = 60)
    @PostMapping("/auth/reset/password")
    @ResponseBody
    public CommonResult<?> resetPassword(@RequestBody ResetPasswordDTO resetPasswordDTO) {
        // 不打印 DTO（含新口令与重置码明文），只记手机号
        log.info("reset password request, phone：{}", resetPasswordDTO.getPhone());
        Assert.hasLength(resetPasswordDTO.getPhone(), "请填写手机号");
        Assert.hasLength(resetPasswordDTO.getCheckCode(), "请填写验证码");

        if (systemLoginService.checkCode(resetPasswordDTO.getCheckCode(), resetPasswordDTO.getPhone())) {
            // 验证码正确，修改密码
            return remoteUserService.updatePassword(resetPasswordDTO, Constants.REQUEST_INNER);
        }
        return CommonResult.fail("验证码错误");
    }

    /**
     * 根据免登授权码, 获取登录用户身份
     *
     * @param authCode 免登授权码
     * @return
     */
    @GetMapping("/auth/dingtalk/login")
    public CommonResult<Object> login(@RequestParam(value = "authCode") String authCode, @RequestParam(value = "corpId") String corpId) {

        Boolean ignore = OrganizationContextHolder.isIgnore();
        try {
            // 钉钉的免登的使用，这时没有登录，没有组织信息
            OrganizationContextHolder.setIgnore(true);
            SystemSocialAppConfigDTO appConfig = remoteUserService.selectSocialAppConfigByCropId(corpId, "dingtalk", "perfect_app");

            OapiV2UserGetResponse.UserGetResponse reresponse = dingTalkService.getUserInfo(authCode, appConfig.getAppId(), appConfig.getSecret());

            SystemSocialUserDTO sysSocialUserDTO = new SystemSocialUserDTO();
            sysSocialUserDTO.setNickName(reresponse.getName());
            sysSocialUserDTO.setSource("dingtalk");
            sysSocialUserDTO.setUnionId(reresponse.getUnionid());
            sysSocialUserDTO.setOpenId(reresponse.getUserid());
            sysSocialUserDTO.setUid(reresponse.getUserid());
            sysSocialUserDTO.setUuid(reresponse.getUserid());
            sysSocialUserDTO.setEmail(reresponse.getEmail());
            sysSocialUserDTO.setPurePhoneNumber(reresponse.getMobile());
            sysSocialUserDTO.setAppCode("perfect_app");
            sysSocialUserDTO.setCorpId(corpId);

            UserInfo userInfo = systemLoginService.socialLogin(sysSocialUserDTO);
            if (userInfo != null) {
                // 获取登录token
                systemLoginService.recordLoginInfo(sysSocialUserDTO.getOpenId(), reresponse.getName(), Constants.LOGIN_SUCCESS, "登录成功", userInfo.getOrganizationId(), userInfo.getDepartmentId());
                return CommonResult.ok(systemLoginService.createTokenWithOrgSession(userInfo));
            } else {
                systemLoginService.recordLoginInfo(sysSocialUserDTO.getOpenId(), reresponse.getName(), Constants.LOGIN_FAIL, "登录失败", SystemLoginService.organizationId, SystemLoginService.organizationId);
                return CommonResult.fail("未授权的应用用户,联系管理员!");
            }
        } finally {
            OrganizationContextHolder.setIgnore(ignore);
            OrganizationContextHolder.clear();
        }
    }
}
