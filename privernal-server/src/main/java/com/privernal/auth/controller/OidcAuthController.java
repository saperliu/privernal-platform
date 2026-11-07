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

import com.privernal.auth.service.OidcClientService;
import com.privernal.auth.service.SystemLoginService;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.redis.service.RedisService;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.domain.SystemSsoOidcConfig;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.system.service.ISystemSocialUserService;
import com.privernal.system.service.SsoOidcConfigService;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * SSO（OIDC 授权码流）的免登端点。这三个地址在 application.yaml 的 ignore.whites 里放行，
 * 所以这里既没有 @PreAuthorize 也不能依赖登录态；身份完全由 state + 外部 id_token/userinfo 决定。
 * <p>
 * 本类刻意放在 com.privernal.auth.controller 包：web 配置会给 controller.pc/controller.app 分别加
 * /back、/app 前缀，换了包路径就会和白名单对不上。
 */
@Tag(name = "SSO单点登录")
@RestController
@Slf4j
public class OidcAuthController {

    private static final String STATE_KEY_PREFIX = "SSO_OIDC_STATE_";
    private static final long STATE_TTL_SECONDS = 600L;

    @Autowired
    private SsoOidcConfigService ssoOidcConfigService;

    @Autowired
    private OidcClientService oidcClientService;

    @Autowired
    private SystemLoginService systemLoginService;

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private ISystemSocialUserService systemSocialUserService;

    @Autowired
    private RedisService redisService;

    @Operation(summary = "登录页可用的单点登录渠道")
    @GetMapping("/auth/oidc/configs")
    public CommonResult<Object> configs() {
        return CommonResult.ok(ssoOidcConfigService.selectEnabledForLogin());
    }

    /**
     * 302 跳到外部身份服务的授权页。state 同时作为 nonce 使用，只发一次、限时 10 分钟。
     */
    @Operation(summary = "跳转授权页")
    @GetMapping("/auth/oidc/authorize/{configCode}")
    public ResponseEntity<Void> authorize(@PathVariable("configCode") String configCode) {
        SystemSsoOidcConfig config = requireEnabled(configCode);
        if (StringUtils.isEmpty(config.getRedirectUri())) {
            throw new ServiceException("渠道 " + configCode + " 未配置回调地址");
        }
        ssoOidcConfigService.ensureEndpoints(config);
        String state = UUID.randomUUID().toString().replace("-", "");
        redisService.setCacheObject(STATE_KEY_PREFIX + state, configCode, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        String location = oidcClientService.buildAuthorizeUrl(config, state, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    /**
     * 授权码回调：换令牌 → 校验身份 → 绑定本地用户 → 颁发平台 token。
     * 返回 JSON 而不是 302 带 token，是为了让 token 不出现在浏览器地址里；
     * 前端把 IdP 的 redirect_uri 配成 SPA 落地页，由页面拿 code/state 再请求本接口（与既有社交登录同构）。
     */
    @Operation(summary = "授权码回调")
    @GetMapping("/auth/oidc/callback/{configCode}")
    public CommonResult<Object> callback(@PathVariable("configCode") String configCode,
                                         @RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error) {
        if (StringUtils.isNotEmpty(error)) {
            throw new ServiceException("外部身份服务返回错误：" + error);
        }
        if (StringUtils.isEmpty(code)) {
            throw new ServiceException("回调缺少授权码 code");
        }
        SystemSsoOidcConfig config = requireEnabled(configCode);
        requireState(configCode, state);
        ssoOidcConfigService.ensureEndpoints(config);

        String clientSecret = ssoOidcConfigService.revealSecret(config);
        JSONObject tokenResponse = oidcClientService.exchangeCode(config, code, clientSecret);
        JSONObject claims = oidcClientService.resolveClaims(config, tokenResponse, state);
        SystemSocialUserDTO socialUser =
                oidcClientService.toSocialUser(config, claims, tokenResponse.getString("access_token"));
        requireBindingAllowed(config, socialUser);

        CommonResult<UserInfo> result = remoteUserService.addJustAuthSocialUser(socialUser, Constants.REQUEST_INNER);
        UserInfo userInfo = result == null ? null : result.getData();
        if (userInfo == null) {
            throw new ServiceException(result == null ? "单点登录失败" : result.getMessage());
        }
        systemLoginService.recordLoginInfo(socialUser.getOpenId(), userInfo.getNickName(),
                Constants.LOGIN_SUCCESS, "登录成功", userInfo.getOrganizationId(), userInfo.getDepartmentId());
        return CommonResult.ok(systemLoginService.createTokenWithOrgSession(userInfo));
    }

    private SystemSsoOidcConfig requireEnabled(String configCode) {
        SystemSsoOidcConfig config = ssoOidcConfigService.selectByConfigCode(configCode);
        if (config == null) {
            throw new ServiceException("单点登录渠道不存在或已停用：" + configCode);
        }
        return config;
    }

    /**
     * state 必须一次性消费：取出即删，重复提交（含并发双击）第二次一律失败
     */
    private void requireState(String configCode, String state) {
        if (StringUtils.isEmpty(state)) {
            throw new ServiceException("缺少 state，无法确认回调来源");
        }
        String key = STATE_KEY_PREFIX + state;
        Object cached = redisService.getCacheObject(key);
        redisService.deleteObject(key);
        if (cached == null) {
            throw new ServiceException("state 已过期或不是本系统签发的，请重新发起登录");
        }
        if (!configCode.equals(String.valueOf(cached))) {
            throw new ServiceException("state 与渠道不匹配");
        }
    }

    /**
     * auto_register=0 时只放行已绑定的外部身份。
     * 绑定链路（saveSysSocialUser）遇到陌生 open_id 会直接建号，所以必须在进入之前拦住。
     */
    private void requireBindingAllowed(SystemSsoOidcConfig config, SystemSocialUserDTO socialUser) {
        if (config.getAutoRegister() != null && config.getAutoRegister() == 1) {
            return;
        }
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            // 免登阶段没有会话组织，组织隔离条件会让绑定查询恒为空
            OrganizationContextHolder.setIgnore(true);
            if (systemSocialUserService.findSocialUser(socialUser) != null) {
                return;
            }
        } catch (Exception e) {
            log.warn("查询单点登录绑定关系失败 configCode={}", config.getConfigCode(), e);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
        }
        throw new ServiceException("该外部账号尚未绑定平台用户，请联系管理员开通");
    }
}
