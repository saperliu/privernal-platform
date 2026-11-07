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

package com.privernal.auth.service;

import cn.hutool.core.util.DesensitizedUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import com.privernal.auth.constant.LoginConstant;
import com.privernal.auth.form.LoginDTO;
import com.privernal.common.core.constant.Constants;
import com.privernal.common.core.constant.UserConstants;
import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.exception.PasswordMigrateRequiredException;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.ServletUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.core.utils.ip.AddressUtils;
import com.privernal.common.core.utils.ip.IpUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.security.service.TokenService;
import com.privernal.common.web.domain.CommonResult;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.feign.RemoteDynamicService;
import com.privernal.message.dto.SystemLogLoginDTO;
import com.privernal.message.feign.RemoteLogService;
import com.privernal.system.domain.SystemUser;
import com.privernal.system.dto.SystemOrganizationDTO;
import com.privernal.system.mapper.SystemUserMapper;
import com.privernal.system.dto.SystemSocialAppConfigDTO;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.dto.UserInfo;
import com.privernal.system.feign.RemoteUserService;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsResponse;
import com.auth0.jwk.Jwk;
import darabonba.core.client.ClientOverrideConfiguration;
import eu.bitwalker.useragentutils.UserAgent;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author saperliu
 * @date 2022-04-10
 * @Description 登录的校验
 */
@Component
public class SystemLoginService {

    private static final Logger log = LoggerFactory.getLogger(SystemLoginService.class);

    public static String organizationId = "system-login";
    public static String sendSmsOK = "OK";
    /** @deprecated 已下沉到 {@link com.privernal.common.core.constant.Constants#SMS_CODE_PREFIX}，保留引用兼容 */
    @Deprecated
    public static String SMS_CODE_PREFIX = com.privernal.common.core.constant.Constants.SMS_CODE_PREFIX;

    /** 短信发送频控计数键前缀（ getSmsCode 频控，防匿名轰炸） */
    public static final String SMS_LIMIT_PREFIX = "SMS_LIMIT_";

    /** 登录失败计数/锁定键前缀（S8：图形验证码下线后的撞库防线） */
    public static final String LOGIN_FAIL_PREFIX = "LOGIN_FAIL_LOCK_";

    /** A2：APP→PC web-view 令牌握手行权码前缀（60 秒一次性，兑换即作废） */
    public static final String WEB_EK_PREFIX = "WEB_EK_";

    private static final long WEB_EK_TIMEOUT_SECONDS = 60;

    /**
     * A2 生成一次性行权码：绑定当前会话 token，60 秒有效、兑换即作废。
     * token 本体永不进 URL——访问日志/Referer/历史里只会有一个用后即焚的码
     */
    public String createWebHandshake(String token) {
        if (StringUtils.isEmpty(token)) {
            throw new ServiceException("会话缺失，无法生成握手码");
        }
        String ek = cn.hutool.core.util.RandomUtil.randomStringUpper(32);
        saTokenDao.set(WEB_EK_PREFIX + ek, token, WEB_EK_TIMEOUT_SECONDS);
        return ek;
    }

    /** A2 兑换：一次性消费，未知/过期/已用一律拒绝 */
    public String exchangeWebHandshake(String ek) {
        if (StringUtils.isEmpty(ek)) {
            return null;
        }
        String key = WEB_EK_PREFIX + ek;
        String token = saTokenDao.get(key);
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        saTokenDao.delete(key);
        return token;
    }

    /** 15 分钟窗口内允许的登录失败次数上限，达到即锁定 15 分钟 */
    private static final int LOGIN_FAIL_LOCK_THRESHOLD = 5;

    @Autowired
    private SaTokenDao saTokenDao;

    @Autowired
    private RemoteLogService remoteLogService;

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private SystemUserMapper systemUserMapper;

    @Autowired
    private RemoteDynamicService remoteDynamicService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordDigestService passwordDigestService;

    @Value("${privernal.sms.endpoint}")
    private String endpoint;

    @Value("${privernal.sms.region}")
    private String region;

    @Value("${privernal.sms.signName}")
    private String signName;

    @Value("${privernal.sms.accessKeyId}")
    private String accessKeyId;

    @Value("${privernal.sms.accessKeySecret}")
    private String accessKeySecret;

    @Value("${privernal.sms.loginTemplateCode}")
    private String loginTemplateCode;

    /**
     * Key Id (P8证书下载后可以得到keyId,在苹果的后台)
     */
    @Value("${privernal.apple.keyId}")
    private String keyId;

    /**
     * keySecret (P8证书下载后可以得到的密钥,在苹果的后台)
     */
    @Value("${privernal.apple.keySecret}")
    private String keySecret;

    /**
     * Sign in with Apple 的 client_id（即接入应用的 Bundle ID），由部署方配置。
     */
    @Value("${privernal.apple.client-id:}")
    private String appleClientId;

    /**
     * 苹果开发者后台的 Team ID（10 位），用于 id_token 的 iss 与 client_id。
     */
    @Value("${privernal.apple.team-id:}")
    private String appleTeamId;

    private final static RestTemplate REST_TEMPLATE = new RestTemplate();

    /**
     * 登录
     */
    public UserInfo login(String username, String password) {
        return login(username, password, null);
    }

    /**
     * 登录（F4 挑战-应答：nonceId 非空时走封存摘要校验，详见 PasswordDigestService）
     */
    public UserInfo login(String username, String password, String nonceId) {

        // S8 配套：图形验证码下线后的撞库防线——失败计数与临时锁定共用本键：
        // 计数阶段存 "1".."4"，达到阈值(5)转存 "LOCKED"。守护条件必须只认 LOCKED 标记——
        // 若按 isNotEmpty 判断，1 次失败留下的计数就会被当成锁定，任意人一次错误口令即可锁死任意账号
        String lockKey = LOGIN_FAIL_PREFIX + username;
        if ("LOCKED".equals(saTokenDao.get(lockKey))) {
            throw new ServiceException("登录失败次数过多，账号已临时锁定，请 15 分钟后再试");
        }
        try {
            UserInfo result = doLogin(username, password, nonceId);
            saTokenDao.delete(lockKey);
            return result;
        } catch (ServiceException e) {
            Long fails = saTokenDao.getObject(lockKey, Long.class);
            long next = (fails == null ? 0L : fails) + 1;
            if (next >= LOGIN_FAIL_LOCK_THRESHOLD) {
                // 达到阈值转成锁定标记（15 分钟内直接拒绝，无需再计数）
                saTokenDao.set(lockKey, "LOCKED", 15 * 60);
            } else {
                saTokenDao.set(lockKey, String.valueOf(next), 15 * 60);
            }
            throw e;
        }
    }

    private UserInfo doLogin(String username, String password, String nonceId) {
        // 用户名或密码为空 错误
        if (StringUtils.isAnyBlank(username, password)) {
            recordLoginInfo(username, username, Constants.LOGIN_FAIL, "用户/密码必须填写", organizationId, organizationId);
            throw new ServiceException("用户/密码必须填写");
        }
        // 密码如果不在指定范围内 错误
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH) {
            recordLoginInfo(username, username, Constants.LOGIN_FAIL, "用户密码不在指定范围", organizationId, organizationId);
            throw new ServiceException("用户密码不在指定范围");
        }
        // 用户名不在指定范围内 错误
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH) {
            recordLoginInfo(username, username, Constants.LOGIN_FAIL, "用户名不在指定范围", organizationId, organizationId);
            throw new ServiceException("用户名不在指定范围");
        }
        // 查询用户信息
        CommonResult<UserInfo> userResult = remoteUserService.getUserInfo(username, Constants.REQUEST_INNER);

        if (StringUtils.isNull(userResult) || StringUtils.isNull(userResult.getData())) {
            recordLoginInfo(username, username, Constants.LOGIN_FAIL, "登录用户不存在", organizationId, organizationId);
            throw new ServiceException("登录用户：" + username + " 不存在");
        }

        if (CommonResult.FAIL == userResult.getCode()) {
            throw new ServiceException(userResult.getMessage());
        }
        UserInfo userInfo = userResult.getData();
//        SystemUserDTO user = userInfo.getSystemUserDTO();
        checkUserStatus(userInfo);
        verifyWirePassword(userInfo, username, password, nonceId, "用户不存在/密码错误");
        recordLoginInfo(userInfo.getLoginName(), userInfo.getNickName(), Constants.LOGIN_SUCCESS, "登录成功", userInfo.getOrganizationId(), userInfo.getDepartmentId());
        return userInfo;
    }

    /**
     * 登录
     */
    public UserInfo loginPhone(String phone, String password) {
        return loginPhone(phone, password, null);
    }

    /**
     * 登录（F4 挑战-应答同 login）
     */
    public UserInfo loginPhone(String phone, String password, String nonceId) {

        if (StringUtils.isAnyBlank(phone, password)) {
            recordLoginInfo(phone, phone, Constants.LOGIN_FAIL, "手机号/密码必须填写", organizationId, organizationId);
            throw new ServiceException("手机号/密码必须填写");
        }
        // 查询用户信息
        CommonResult<UserInfo> userResult = remoteUserService.getUserInfo(phone, Constants.REQUEST_INNER);

        if (StringUtils.isNull(userResult) || StringUtils.isNull(userResult.getData())) {
            recordLoginInfo(phone, phone, Constants.LOGIN_FAIL, "手机号不存在", organizationId, organizationId);
            throw new ServiceException("手机号：" + phone + " 不存在");
        }

        if (CommonResult.FAIL == userResult.getCode()) {
            throw new ServiceException(userResult.getMessage());
        }
        UserInfo userInfo = userResult.getData();
        checkUserStatus(userInfo);
        verifyWirePassword(userInfo, phone, password, nonceId, "手机号不存在/密码错误");
        recordLoginInfo(userInfo.getLoginName(), userInfo.getNickName(), Constants.LOGIN_SUCCESS, "登录成功", userInfo.getOrganizationId(), userInfo.getDepartmentId());
        return userInfo;
    }

    /**
     * F4 挑战签发（登录前，免登）：一次性 nonce + 迁移标志。
     * migrated=false 时客户端会先提交一次旧摘要（服务端此时静默写入封存值），再取新 nonce 重试。
     * 查询失败一律按未迁移处理（客户端降级一次可自愈），且不区分"用户不存在"，
     * 避免 challenge 端点变成账号枚举器。
     */
    public java.util.Map<String, Object> issueChallenge(String loginName) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        if (!passwordDigestService.challengeEnabled() || StringUtils.isEmpty(loginName)) {
            data.put("enabled", false);
            return data;
        }
        boolean migrated = false;
        try {
            CommonResult<UserInfo> r = remoteUserService.getUserInfo(loginName, Constants.REQUEST_INNER);
            migrated = r != null && r.getData() != null && StringUtils.isNotEmpty(r.getData().getPasswordV2());
        } catch (Exception e) {
            log.warn("挑战签发查用户失败，按未迁移处理：{}", e.getMessage());
        }
        PasswordDigestService.Challenge c = passwordDigestService.issue(loginName);
        data.put("enabled", true);
        data.put("nonceId", c.nonceId());
        data.put("nonce", c.nonce());
        data.put("ttlSeconds", c.ttlSeconds());
        data.put("migrated", migrated);
        return data;
    }

    /**
     * F4 双口径口令校验：带 nonceId 且用户已迁移 → 挑战协议（AES 封存摘要，重放面=0）；
     * 带 nonceId 但未迁移 → 抛待迁移专用码（客户端自动降级引导一次后重试）；
     * 无 nonceId → bcrypt 旧口径，成功且挑战可用时静默迁移（写封存摘要）。
     *
     * @param userInfo    登录用户（含旧 bcrypt 与可选封存摘要）
     * @param accountName 用户名或手机号（挑战绑定与审计用）
     * @param password    线上口令值：旧口径=SHA512(明文)，挑战口径=SHA512(nonce+SHA512(明文))
     * @param nonceId     挑战编号（可空）
     * @param failMessage 校验失败提示
     */
    private void verifyWirePassword(UserInfo userInfo, String accountName, String password, String nonceId, String failMessage) {
        boolean migrated = StringUtils.isNotEmpty(userInfo.getPasswordV2());
        if (nonceId != null && passwordDigestService.challengeEnabled()) {
            if (!migrated) {
                throw new PasswordMigrateRequiredException();
            }
            String x = passwordDigestService.verifyChallenge(nonceId, accountName, userInfo.getPasswordV2(), password);
            if (x == null) {
                recordLoginInfo(userInfo.getLoginName(), userInfo.getNickName(), Constants.LOGIN_FAIL, failMessage, organizationId, organizationId);
                throw new ServiceException(failMessage);
            }
            return;
        }
        if (migrated && passwordDigestService.isRequireChallenge()) {
            // 已迁移 + 强制新口径：拒绝旧摘要，收掉重放面
            throw new ServiceException("登录凭证需使用新版客户端");
        }
        if (!SecurityUtils.matchesPassword(password, userInfo.getPassword())) {
            recordLoginInfo(userInfo.getLoginName(), userInfo.getNickName(), Constants.LOGIN_FAIL, failMessage, organizationId, organizationId);
            throw new ServiceException(failMessage);
        }
        // 引导迁移：旧口径成功 + 挑战可用 + 未迁移 → 封存本次线摘要；失败不影响本次登录
        if (passwordDigestService.challengeEnabled() && !migrated) {
            try {
                remoteUserService.updatePasswordV2(userInfo.getLoginName(), passwordDigestService.seal(password), Constants.REQUEST_INNER);
            } catch (Exception e) {
                log.warn("口令摘要迁移写入失败（下次登录重试）：{}", e.getMessage());
            }
        }
    }

    /**
     * 登录
     */
    public UserInfo loginPhoneCode(String phone, String code) {

        if (StringUtils.isAnyBlank(phone, code)) {
            recordLoginInfo(phone, phone, Constants.LOGIN_FAIL, "手机号/验证码必须填写", organizationId, organizationId);
            throw new ServiceException("手机号/验证码必须填写");
        }
        // 查询用户信息
        CommonResult<UserInfo> userResult = remoteUserService.getUserInfo(phone, Constants.REQUEST_INNER);

        if (StringUtils.isNull(userResult) || StringUtils.isNull(userResult.getData())) {
            recordLoginInfo(phone, phone, Constants.LOGIN_FAIL, "手机号不存在", organizationId, organizationId);
            throw new ServiceException("手机号：" + phone + " 不存在");
        }

        if (CommonResult.FAIL == userResult.getCode()) {
            throw new ServiceException(userResult.getMessage());
        }
        UserInfo userInfo = userResult.getData();
//        SystemUserDTO user = userInfo.getSystemUserDTO();
        checkUserStatus(userInfo);

        String value = saTokenDao.get(SMS_CODE_PREFIX + phone);

        // R3 修复：缓存为空（从未发送/已过期）或不匹配都必须拒绝——原实现条件写反，
        // 正确验证码被拒、错误或从未发送即放行，等于免凭据接管任意手机号账号
        if (StringUtils.isEmpty(value) || !value.equals(code)) {
            recordLoginInfo(userInfo.getLoginName(), userInfo.getNickName(), Constants.LOGIN_FAIL, "验证码错误", organizationId, organizationId);
            throw new ServiceException("验证码错误");
        }
        // 一次性消费：验证通过立即作废，防重放
        saTokenDao.delete(SMS_CODE_PREFIX + phone);
        recordLoginInfo(userInfo.getLoginName(), userInfo.getNickName(), Constants.LOGIN_SUCCESS, "登录成功", userInfo.getOrganizationId(), userInfo.getDepartmentId());
        return userInfo;
    }

    /**
     * 切换组织
     */
    public UserInfo changOrganization(String organizationId, String userId, String userName) {
        CommonResult<UserInfo> userResult = remoteUserService.changeOrganization(organizationId, userId, Constants.REQUEST_INNER);
        if (StringUtils.isNull(userResult) || StringUtils.isNull(userResult.getData())) {
            recordLoginInfo(userName, userName, Constants.LOGIN_FAIL, "用户切换组织失败", organizationId, organizationId);
            throw new ServiceException("登录用户：" + userName + " 切换组织失败");
        }
        return userResult.getData();
    }

    /**
     * 退出的日志
     *
     * @param loginName 登录名
     * @param nickName 昵称
     */
    public void logout(String loginName, String nickName) {
        recordLoginInfo(loginName, nickName, Constants.LOGIN_SUCCESS, "退出成功", organizationId, organizationId);
    }

    /**
     * 帐号注销
     *
     * @param userInfo 帐号信息
     */
    public void logOff(UserInfo userInfo) {
        CommonResult<Object> result = remoteUserService.deleteUser(userInfo.getUserId(), Constants.REQUEST_INNER);
        if (result.getCode() == CommonResult.SUCCESS) {
            // 注销后登录名/手机号追加 del 后缀，释放原值供重新注册；
            // 已删行（deleted_flag=Y）不计入查重，但追加后缀可避免占用原登录名
            suffixDelOnCancel(userInfo.getUserId());
            revokeToken(userInfo);
        }
    }

    /** 注销账号的登录名/手机号追加 del 后缀（幂等：仅当未带后缀时追加） */
    private void suffixDelOnCancel(String userId) {
        try {
            SystemUser user = systemUserMapper.selectById(userId);
            if (user == null) {
                return;
            }
            String suffix = "del";
            LambdaUpdateWrapper<SystemUser> wrapper = new LambdaUpdateWrapper<SystemUser>()
                    .eq(SystemUser::getId, userId);
            if (StringUtils.isNotEmpty(user.getLoginName()) && !user.getLoginName().endsWith(suffix)) {
                wrapper.set(SystemUser::getLoginName, user.getLoginName() + suffix);
            }
            if (StringUtils.isNotEmpty(user.getPhone()) && !user.getPhone().endsWith(suffix)) {
                wrapper.set(SystemUser::getPhone, user.getPhone() + suffix);
            }
            systemUserMapper.update(null, wrapper);
        } catch (Exception e) {
            // 后缀追加失败不影响注销主流程（该账号已删除、无法登录），仅影响登录名释放
            log.warn("注销账号追加 del 后缀失败 userId={}", userId, e);
        }
    }

    /**
     * 微信登录
     *
     * @param loginDTO 登录的信息
     */
    public UserInfo weixinLogin(LoginDTO loginDTO) {

        // 获取前端传过来的数据start
        String code = loginDTO.getCode();
        String iv = loginDTO.getIv();
        String encryptedData = loginDTO.getEncryptedData();
        // 获取前端传过来的数据end
        // 获取openid和session_key  start
        Map<String, String> stringStringMap = getOpenIdAndSessionKey(code, loginDTO.getProductCode());
        String openid = stringStringMap.get("openid");
        String sessionKey = stringStringMap.get("session_key");
        String appCode = stringStringMap.get("app_code");
        String deptId = stringStringMap.get("departmentId");
        String roleId = stringStringMap.get("roleId");
        String organizationId = stringStringMap.get("organizationId");
        String examineUser = stringStringMap.get("examine_user");
        // 获取openid和session_key  end
        log.info("微信小程序授权请求的参数:" + JSONObject.toJSONString(stringStringMap));

        try {
            if (StringUtils.isNotEmpty(encryptedData) && StringUtils.isNotEmpty(iv)) {
                // 解密数据
                byte[] resultByte = decryptInfoMethod(encryptedData, sessionKey, iv);

                if (null != resultByte) {
                    // 解密数据转成SystemSocialUserDTO
                    SystemSocialUserDTO systemSocialUserDTO = JSONObject.parseObject(new String(resultByte), SystemSocialUserDTO.class);
                    if (StringUtils.isNotEmpty(systemSocialUserDTO.getNickName())) {
                        if (StringUtils.isNotEmpty(systemSocialUserDTO.getGender()) && systemSocialUserDTO.getGender().equals("0")) {
                            // 系统里的 2是未知，0是男，1是女;  微信 0是未知，1是男，2是女
                            systemSocialUserDTO.setGender("2");
                        } else {
                            systemSocialUserDTO.setGender((Long.parseLong(systemSocialUserDTO.getGender()) - 1) + "");
                        }
                    }
                    systemSocialUserDTO.setSource(Constants.USER_SOURCE_WX_MINI_PROGRAM);
                    systemSocialUserDTO.setExamineUser(examineUser);
                    systemSocialUserDTO.setOpenId(openid);
                    systemSocialUserDTO.setAppCode(appCode);
                    systemSocialUserDTO.setDepartmentId(deptId);
                    systemSocialUserDTO.setOrganizationId(organizationId);
                    systemSocialUserDTO.setRoleId(roleId);
                    // 保存或更新用户
                    CommonResult<UserInfo> userResult = remoteUserService.addJustAuthSocialUser(systemSocialUserDTO, Constants.REQUEST_INNER);
                    // 检查用户状态
                    checkUserStatus(userResult.getData());
                    return userResult.getData();
                } else {
                    log.error("微信登录解密用户信息失败");
                    throw new ServiceException("微信登录解析用户信息失败!");
                }
            } else {
                // 自动登录
                SystemSocialUserDTO systemSocialUserDTO = new SystemSocialUserDTO();
                systemSocialUserDTO.setOpenId(openid);
                systemSocialUserDTO.setAppCode(appCode);
                CommonResult<UserInfo> userResult = remoteUserService.findSocialUser(systemSocialUserDTO, Constants.REQUEST_INNER);
                // 检查用户状态
                checkUserStatus(userResult.getData());
                return userResult.getData();
            }
        } catch (ServiceException b) {
            throw b;
        } catch (Exception e) {
            log.error("微信登录解密用户信息异常");
            throw new ServiceException("微信授权信息登录失败!");
        }
    }

    private Map<String, String> getOpenIdAndSessionKey(String code, String miniCode) {

        // 查询配置的小程序的密钥
        CommonResult<SystemSocialAppConfigDTO> socialAppConfigDTO = remoteUserService.getAppConfig(miniCode, Constants.REQUEST_INNER);
        if (StringUtils.isNull(socialAppConfigDTO) || StringUtils.isNull(socialAppConfigDTO.getData())) {
            throw new ServiceException("微信授权信息登录失败!");
        } else {
            // 构建请求微信接口获取openid和session_key的所需参数start
            Map<String, Object> pa = new HashMap<>(12);
            pa.put("appid", socialAppConfigDTO.getData().getAppId());
            pa.put("secret", socialAppConfigDTO.getData().getSecret());
            pa.put("js_code", code);
            pa.put("grant_type", "authorization_code");
            String openidUrl = "https://api.weixin.qq.com/sns/jscode2session";

            // 构建请求微信接口获取openid和session_key的所需参数end
            log.info("微信小程序授权请求的参数:" + JSONObject.toJSONString(pa));
            // 调用微信接口获取openid和session_key start
            String body = HttpUtil.post(openidUrl, pa);
            JSONObject jsonObject = JSON.parseObject(body);
            String openid = jsonObject.getString("openid");
            String sessionKey = jsonObject.getString("session_key");
            // 调用微信接口获取openid和session_key end

            // 对openid和session_key进行判断start
            if (openid == null || sessionKey == null) {
                log.error("微信小程序授权失败，原因是:" + JSONObject.toJSONString(jsonObject));
                throw new ServiceException("微信授权信息登录失败!");
            }
            // 对openid和session_key进行判断end

            Map<String, String> result = new HashMap<>(6);
            result.put("openid", openid);
            result.put("session_key", sessionKey);
            result.put("app_code", socialAppConfigDTO.getData().getAppCode());
            result.put("departmentId", socialAppConfigDTO.getData().getDepartmentId());
            result.put("organizationId", socialAppConfigDTO.getData().getOrganizationId());
            result.put("roleId", socialAppConfigDTO.getData().getRoleId());
            result.put("examine_user", socialAppConfigDTO.getData().getExamineUser());
            return result;
        }
    }

    /**
     * 解密用户信息
     *
     * @param encryptedData 加密数据
     * @param sessionKey    密钥
     * @param iv            向量
     * @return
     */
    private byte[] decryptInfoMethod(String encryptedData, String sessionKey, String iv) {

        try {
            // 解密用户信息start
            // 被加密的数据
            byte[] encryptedDataB = Base64.decode(encryptedData);
            // 加密秘钥
            byte[] sessionKeyB = Base64.decode(sessionKey);
            // 偏移量
            byte[] ivB = Base64.decode(iv);
            AES aes = new AES(Mode.CBC, Padding.PKCS5Padding, sessionKeyB, ivB);
            byte[] resultByte = aes.decrypt(encryptedDataB);
            // 解密用户信息end
            return resultByte;

        } catch (Exception e) {
            log.error("解密微信信息出错，错误信息如下：", e);
            throw new ServiceException("微信授权信息解析失败!");
        }
    }

    /**
     * 检查用户的状态
     *
     * @param user 用户信息
     */
    private void checkUserStatus(UserInfo user) {
        if (user != null) {
//            SystemUserDTO user = userInfo.getSystemUserDTO();
            if (DataConstants.DATA_DELETE_FLAG_YES.equals(user.getDeletedFlag())) {
                SystemLogLoginDTO systemLogLoginDTO = new SystemLogLoginDTO();
                systemLogLoginDTO.setLoginName(user.getLoginName());
                systemLogLoginDTO.setNickName(user.getNickName());
                systemLogLoginDTO.setStatus(Constants.LOGIN_FAIL_STATUS);
                systemLogLoginDTO.setMessage("对不起，您的账号已被删除");
                remoteLogService.saveLoginLog(systemLogLoginDTO, Constants.REQUEST_INNER);
                throw new ServiceException("对不起，您的账号：" + user.getLoginName() + " 已被删除");
            }
            if (String.valueOf(UserConstants.USER_STATUS_DISABLE).equals(user.getStatus() + "")) {
                SystemLogLoginDTO systemLogLoginDTO = new SystemLogLoginDTO();
                systemLogLoginDTO.setLoginName(user.getLoginName());
                systemLogLoginDTO.setNickName(user.getNickName());
                systemLogLoginDTO.setStatus(Constants.LOGIN_FAIL_STATUS);
                systemLogLoginDTO.setMessage("用户已停用，请联系管理员");
                remoteLogService.saveLoginLog(systemLogLoginDTO, Constants.REQUEST_INNER);
                throw new ServiceException("对不起，您的账号：" + user.getLoginName() + " 已停用");
            }

            if (String.valueOf(UserConstants.USER_STATUS_APPROVE).equals(user.getStatus() + "")) {
                SystemLogLoginDTO systemLogLoginDTO = new SystemLogLoginDTO();
                systemLogLoginDTO.setLoginName(user.getLoginName());
                systemLogLoginDTO.setNickName(user.getNickName());
                systemLogLoginDTO.setStatus(Constants.LOGIN_FAIL_STATUS);
                systemLogLoginDTO.setMessage("用户审核中，请联系管理员");
                remoteLogService.saveLoginLog(systemLogLoginDTO, Constants.REQUEST_INNER);
                throw new ServiceException("对不起，您的账号：" + user.getLoginName() + " 审核中");
            }
        }
    }

    /**
     * 第三方授权登录
     *
     * @param sysSocialUserDTO 登录的信息
     */
    public UserInfo socialLogin(SystemSocialUserDTO sysSocialUserDTO) {

        // 获取前端传过来的数据end
        CommonResult<SystemSocialAppConfigDTO> socialAppConfigDTO = remoteUserService.getSocialAppConfig(sysSocialUserDTO.getAppCode(), sysSocialUserDTO.getCorpId());

        try {

            sysSocialUserDTO.setAppCode(socialAppConfigDTO.getData().getAppCode());
            sysSocialUserDTO.setOrganizationId(socialAppConfigDTO.getData().getOrganizationId());
            sysSocialUserDTO.setDepartmentId(socialAppConfigDTO.getData().getDepartmentId());
            sysSocialUserDTO.setRoleId(socialAppConfigDTO.getData().getRoleId());
            // 保存或更新用户
            CommonResult<UserInfo> userResult = remoteUserService.addJustAuthSocialUser(sysSocialUserDTO, Constants.REQUEST_INNER);
            // 检查用户状态
            checkUserStatus(userResult.getData());
            recordLoginInfo(userResult.getData().getLoginName(), userResult.getData().getNickName(), Constants.LOGIN_SUCCESS, "登录成功", sysSocialUserDTO.getOrganizationId(), sysSocialUserDTO.getDepartmentId());
            return userResult.getData();
        } catch (ServiceException b) {
            log.error("social login ", b);
            recordLoginInfo(sysSocialUserDTO.getOpenId(), sysSocialUserDTO.getNickName(), Constants.LOGIN_FAIL, "授权登录失败", sysSocialUserDTO.getOrganizationId(), sysSocialUserDTO.getDepartmentId());
            throw b;
        } catch (Exception e) {
            log.error("social login ", e);
            recordLoginInfo(sysSocialUserDTO.getOpenId(), sysSocialUserDTO.getNickName(), Constants.LOGIN_FAIL, "授权登录用户信息失败", sysSocialUserDTO.getOrganizationId(), sysSocialUserDTO.getDepartmentId());
            throw new ServiceException("授权信息登录失败!");
        }
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     * @return
     */
    public void recordLoginInfo(String username, String nickName, String status, String message, String organizationId, String deptId) {
        SystemLogLoginDTO systemLogLoginDTO = new SystemLogLoginDTO();
        systemLogLoginDTO.setLoginName(username);
        systemLogLoginDTO.setNickName(username);
        systemLogLoginDTO.setMessage(message);
        final UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"));
        final String ip = IpUtils.getIpAddr(ServletUtils.getRequest());
        String address = AddressUtils.getRealAddressByIP(ip);
        // 获取客户端操作系统
        String os = userAgent.getOperatingSystem().getName();
        // 获取客户端浏览器
        String browser = userAgent.getBrowser().getName();
        systemLogLoginDTO.setLoginIp(ip);
        systemLogLoginDTO.setLoginLocation(address);
        systemLogLoginDTO.setBrowser(browser);
        systemLogLoginDTO.setOs(os);
        systemLogLoginDTO.setOrganizationId(organizationId);
        systemLogLoginDTO.setDepartmentId(deptId);
        // 日志状态
        if (StringUtils.equalsAny(status, Constants.LOGIN_SUCCESS, Constants.LOGOUT, Constants.REGISTER)) {
            systemLogLoginDTO.setStatus(Constants.LOGIN_SUCCESS_STATUS);
        } else if (Constants.LOGIN_FAIL.equals(status)) {
            systemLogLoginDTO.setStatus(Constants.LOGIN_FAIL_STATUS);
        } else {
            systemLogLoginDTO.setStatus(Constants.LOGIN_SUCCESS_STATUS);
        }
        remoteLogService.saveLoginLog(systemLogLoginDTO, Constants.REQUEST_INNER);
    }

    /**
     * 获取验证码
     *
     * @param phone 手机号
     * @return result CommonResult
     */
    public CommonResult<Object> getSmsCode(String phone) {

        String code = saTokenDao.get(SMS_CODE_PREFIX + phone);
        if (StringUtils.isNotEmpty(code)) {
            return CommonResult.ok("已经发送");
        }
        // 短信频控（R3/S8 配套）：该端点免登开放，无频控等于匿名短信轰炸面。
        // 同一手机号 60 秒一条、24 小时内最多 10 条；发送成功才计数
        String minuteKey = SMS_LIMIT_PREFIX + phone + ":60s";
        if (StringUtils.isNotEmpty(saTokenDao.get(minuteKey))) {
            return CommonResult.fail("发送过于频繁，请稍后再试");
        }
        String dayKey = SMS_LIMIT_PREFIX + phone + ":24h";
        String dayCount = saTokenDao.get(dayKey);
        if (dayCount != null && Integer.parseInt(dayCount) >= 10) {
            return CommonResult.fail("该手机号今日发送次数已达上限");
        }

        String codeNum = RandomUtil.randomNumbers(6);

        // Configure Credentials authentication information, including ak, secret, token
        StaticCredentialProvider provider = StaticCredentialProvider.create(Credential.builder()
                .accessKeyId(accessKeyId)
                .accessKeySecret(accessKeySecret)
                //.securityToken("<your-token>") // use STS token
                .build());

        try (AsyncClient client = AsyncClient.builder()
                .region(region) // Region ID
                //.httpClient(httpClient) // Use the configured HttpClient, otherwise use the default HttpClient (Apache HttpClient)
                .credentialsProvider(provider)
                //.serviceConfiguration(Configuration.create()) // Service-level configuration
                // Client-level configuration rewrite, can set Endpoint, Http request parameters, etc.
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                .setEndpointOverride(endpoint)
                                .setConnectTimeout(Duration.ofSeconds(10))
                )
                .build()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("code", codeNum);
            SendSmsRequest sendSmsRequest = SendSmsRequest.builder()
                    .phoneNumbers(phone)
                    .signName(signName)
                    .templateCode(loginTemplateCode)
                    .templateParam(jsonObject.toJSONString())
                    // Request-level configuration rewrite, can set Http request parameters, etc.
                    // .requestConfiguration(RequestConfiguration.create().setHttpHeaders(new HttpHeaders()))
                    .build();
            // Asynchronously get the return value of the API request
            CompletableFuture<SendSmsResponse> response = client.sendSms(sendSmsRequest);
            // Synchronously get the return value of the API request
            SendSmsResponse resp = response.get();

            if (sendSmsOK.equals(resp.getBody().getCode())) {
                // 存储验证码，有效期 300 秒；验证码只进缓存，绝不进响应体也绝不进日志
                // （S2：回显 = 任何人可免登重置任意账号密码；明文进日志 = 日志泄露即等同泄露）
                saTokenDao.set(SMS_CODE_PREFIX + phone, codeNum, 300);
                // 频控计数：60s 窗口 + 24h 累计（发送成功才计，失败重试不占额度）
                saTokenDao.set(SMS_LIMIT_PREFIX + phone + ":60s", "1", 60);
                String sentCount = saTokenDao.get(SMS_LIMIT_PREFIX + phone + ":24h");
                long next = sentCount == null ? 1L : Long.parseLong(sentCount) + 1;
                saTokenDao.set(SMS_LIMIT_PREFIX + phone + ":24h", String.valueOf(next), 24 * 3600);
                log.info("send sms code success, phone={}", DesensitizedUtil.mobilePhone(phone));
                return CommonResult.ok("验证码已发送，请查收短信");
            }
            log.error("send sms code error: {}", JSON.toJSON(resp));
            return CommonResult.fail(resp.getBody().getMessage());
        } catch (Exception e) {
            log.error("send sms code error", e);
            return CommonResult.fail(e.getMessage());
        }
    }

    /**
     * 校验验证码
     *
     * @param code  码
     * @param phone 手机号
     * @return
     */
    public boolean checkCode(String code, String phone) {
        if (StringUtils.isNotEmpty(code) && StringUtils.isNotEmpty(phone)) {
            if (code.equals(saTokenDao.get(SMS_CODE_PREFIX + phone))) {
                // 一次性消费：校验通过立即作废，防重放
                saTokenDao.delete(SMS_CODE_PREFIX + phone);
                return true;
            }
        }
        return false;
    }

    /**
     * 获取苹果授权的用户信息
     *
     * @param identityToken 加密的token
     * @return
     * @throws Exception
     */
    public CommonResult<JSONObject> getAppleUserInfo(String identityToken) throws Exception {

        //验证identityToken
        if (!verify(identityToken)) {
            return CommonResult.fail("验证失败");
        }
        //对identityToken解码
        JSONObject json = parserIdentityToken(identityToken);
        if (json == null) {
            return CommonResult.fail("解码失败");
        }
        return CommonResult.ok(json);
    }

    /**
     * 对前端传来的JWT字符串identityToken的第二部分进行解码
     * 主要获取其中的aud和sub，aud大概对应ios前端的包名，sub大概对应当前用户的授权的openID
     *
     * @param identityToken 身份token
     * @return {"aud":"com.xkj.****","sub":"000***.8da764d3f9e34d2183e8da08a1057***.0***","c_hash":"UsKAuEoI-****","email_verified":"true","auth_time":1574673481,"iss":"https://appleid.apple.com","exp":1574674081,"iat":1574673481,"email":"****@qq.com"}
     * 字段名	        说明
     * iss	           签发机构网址
     * aud	           bundle id
     * exp	           int 过期时间戳
     * iat	            签发时间
     * sub	            user id
     * nouce	         客户端发出请求时携带的随机串，用于对照
     * c_hash	          一段哈希
     * email	          email
     * email_verified	 email 是否确认了
     * is_private_email	 是否为 private email
     * auth_time	     email 授权时间
     **/
    private JSONObject parserIdentityToken(String identityToken) {
        String[] arr = identityToken.split("\\.");
        String decode = new String(Base64.decode(arr[1]));
        String substring = decode.substring(0, decode.indexOf("}") + 1);
        // 不打印 identity token 载荷（含用户邮箱等 PII）
        return JSONObject.parseObject(substring);
    }

    /**
     * 解析jwt，验证信息是否正确
     *
     * @param jwt
     * @return
     * @throws Exception
     */
    public Boolean verify(String jwt) throws Exception {
        JSONArray arr = getAuthKeys();
        if (arr == null) {
            return false;
        }
        JSONObject authKey = null;
        //先取苹果第一个key进行校验
        authKey = JSONObject.parseObject(arr.getString(0));
        if (verifyExc(jwt, authKey)) {
            return true;
        } else {
            //再取第二个key校验
            authKey = JSONObject.parseObject(arr.getString(1));
            if (verifyExc(jwt, authKey)) {
                return true;
            } else {
                if (arr.size() == 3) {
                    authKey = JSONObject.parseObject(arr.getString(2));
                    return verifyExc(jwt, authKey);
                } else {
                    return false;
                }
            }
        }

    }

    /**
     * 对前端传来的identityToken进行验证
     *
     * @param jwt     对应前端传来的 identityToken
     * @param authKey 苹果的公钥 authKey
     * @return
     * @throws Exception
     */
    private static Boolean verifyExc(String jwt, JSONObject authKey) throws Exception {
        Jwk jwa = Jwk.fromValues(authKey);
        PublicKey publicKey = jwa.getPublicKey();

        String aud = "";
        String sub = "";
        if (jwt.split("\\.").length > 1) {
            String claim = new String(Base64.decode(jwt.split("\\.")[1]));
            // 不打印 identity token 载荷（含用户标识/邮箱等 PII）
            aud = JSONObject.parseObject(claim).get("aud").toString();
            sub = JSONObject.parseObject(claim).get("sub").toString();
        }
        JwtParserBuilder jwtParser = Jwts.parserBuilder().setSigningKey(publicKey);
        jwtParser.requireIssuer(LoginConstant.REVOKE_AUD);
        jwtParser.requireAudience(aud);
        jwtParser.requireSubject(sub);

        try {
            Jws<Claims> claim = jwtParser.build().parseClaimsJws(jwt);
            // 不打印解析后的 JWT claims（含用户标识/邮箱等 PII）
            // 验证通过
            return claim != null && claim.getBody().containsKey("auth_time");
        } catch (ExpiredJwtException e) {
            log.error("[AppleServiceImpl.verifyExc] [error] [apple identityToken expired]", e);
            return false;
        } catch (Exception e) {
            log.error("[AppleServiceImpl.verifyExc] [error] [apple identityToken illegal]", e);
            return false;
        }
    }

    /**
     * 获取苹果的公钥
     *
     * @return
     */
    private static JSONArray getAuthKeys() {
        JSONObject json = REST_TEMPLATE.getForObject(LoginConstant.AUTH_KEYS_URL, JSONObject.class);
        if (json != null) {
            return json.getJSONArray("keys");
        }
        return null;
    }

    /**
     * 生成客户端密钥
     */
    private String generateClientSecret() throws Exception {
        Map<String, Object> header = new HashMap<>();
        header.put("kid", keyId); //P8文件下载得到的Key ID
        header.put("alg", SignatureAlgorithm.ES256.getValue()); //SHA256
        Map<String, Object> claims = new HashMap<>();
        long now = System.currentTimeMillis() / 1000;
        claims.put("iss", appleTeamId);
        claims.put("iat", now);
        claims.put("exp", now + 648000); // 最长半年，单位秒
        claims.put("aud", LoginConstant.REVOKE_AUD);
        claims.put("sub", appleClientId);
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(Base64.decode(keySecret));//P8文件下载得到的Key Value
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PrivateKey privateKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        return Jwts.builder().setHeader(header).setClaims(claims).signWith(privateKey, SignatureAlgorithm.ES256).compact();
    }

    /**
     * 根据authorizationCode获取refreshToken，在注销用户时使用
     */
    public String getAppleRefreshToken(String authorizationCode) {
        String refreshToken = null;
        try {
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("client_id", appleClientId);
            map.add("client_secret", generateClientSecret());
            map.add("grant_type", "authorization_code");
            map.add("code", authorizationCode);
            map.add("redirect_uri", authorizationCode);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            String value = REST_TEMPLATE.postForEntity(LoginConstant.AUTH_TOKEN_URL, request, String.class).getBody();
            if (StringUtils.isNotBlank(value)) {
                JSONObject json = JSONObject.parseObject(value);
                refreshToken = json.getString("refresh_token");
            }
        } catch (HttpClientErrorException e) {
            log.error("getAppleRefreshToken HttpClientErrorException ", e);
            throw new ServiceException(e.getResponseBodyAsString(), "401");
        } catch (Exception e) {
            log.error("getAppleRefreshToken Exception ", e);
            throw new ServiceException(e.getMessage(), "401");
        }
        log.info("刷新token成功");
        return refreshToken;
    }

    /**
     * 根据refreshToken刷新accessToken
     */
    private String refreshToken(String refreshToken) {
        String accessToken = null;
        try {
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("client_id", appleClientId);
            map.add("client_secret", generateClientSecret());
            map.add("grant_type", "refresh_token");
            map.add("refresh_token", refreshToken);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            String value = REST_TEMPLATE.postForEntity(LoginConstant.AUTH_TOKEN_URL, request, String.class).getBody();
            if (StringUtils.isNotBlank(value)) {
                JSONObject json = JSONObject.parseObject(value);
                accessToken = json.getString("access_token");
            }
        } catch (HttpClientErrorException e) {
            log.error("refreshToken HttpClientErrorException ", e);
            throw new ServiceException(e.getResponseBodyAsString(), "401");
        } catch (Exception e) {
            log.error("refreshToken Exception ", e);
            throw new ServiceException(e.getMessage(), "401");
        }
        log.info("刷新token成功");
        return accessToken;
    }

    /**
     * 注销用户token
     */
    public CommonResult<String> revokeToken(UserInfo userInfo) {
        try {
            String accessToken = refreshToken(userInfo.getIosRefreshToken());//更新accessToken
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("client_id", appleClientId);
            map.add("client_secret", generateClientSecret());
            map.add("token_type_hint", "access_token");
            map.add("token", accessToken);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            ResponseEntity<String> values = REST_TEMPLATE.postForEntity(LoginConstant.AUTH_REVOKE_URL, request, String.class);
            log.info("注销token成功  {}", values);//Apple官网上请求只有200与400,这里没有抛异常,证明是成功注销的
            return CommonResult.ok("注销成功");
        } catch (HttpClientErrorException e) {
            log.error("revokeToken HttpClientErrorException ", e);
            throw new ServiceException(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("revokeToken Exception ", e);
            throw new ServiceException(e.getMessage());
        }
    }

    private byte[] readPrimaryKey(String primaryKey) {
        StringBuilder pkcs8Lines = new StringBuilder();
        BufferedReader rdr = new BufferedReader(new StringReader(primaryKey));
        String line = "";
        try {
            while ((line = rdr.readLine()) != null) {
                pkcs8Lines.append(line);
            }
        } catch (IOException e) {
            log.error("读取RSA私钥配置失败", e);
        }
        // 需要注意删除 "BEGIN" and "END" 行, 以及空格
        String pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+", "");
        // Base64 转码
        return Base64.decode(pkcs8Pem);
    }

    /**
     * 取组织编码放入会话；取不到就留空，建表链路会按组织ID回查，不阻断登录
     */
    public String resolveOrganizationCode(String organizationId) {
        if (StringUtils.isEmpty(organizationId)) {
            return "";
        }
        try {
            CommonResult<SystemOrganizationDTO> result = remoteUserService.findOrganizationById(organizationId, "");
            if (result != null && result.getCode() == CommonResult.SUCCESS && result.getData() != null
                    && StringUtils.isNotEmpty(result.getData().getOrganizationCode())) {
                return result.getData().getOrganizationCode();
            }
        } catch (Exception e) {
            log.error(" resolve organization code error, orgId {} ", organizationId, e);
        }
        return "";
    }

    /**
     * 重设会话里的业务库名：动态表单的数据读写按这个库名路由数据源。
     * 库名写在 token 会话上，必须在建 token 之后调用；
     * sa-token 是 is-share 模式，同一账号复用同一个 token 会话，
     * 上一组织的库名不覆盖就会继续读写到旧组织的业务库，所以新组织没配库也要清空。
     */
    public void resetOrganizationDatabase(UserInfo userInfo) {
        String databaseName = "";
        if (StringUtils.isNotEmpty(userInfo.getOrganizationId())) {
            DynamicDatabaseDTO dynamicDatabaseDTO = remoteDynamicService.findDynamicDatabaseByCode(userInfo.getOrganizationId());
            if (dynamicDatabaseDTO != null && StringUtils.isNotEmpty(dynamicDatabaseDTO.getDatabaseName())) {
                databaseName = dynamicDatabaseDTO.getDatabaseName();
            }
        }
        tokenService.setDatabase(databaseName);
    }

    /**
     * 建 token 并绑定组织上下文：组织编码与业务库名都要跟着组织走，缺一个动态表单就会用上一个组织的前缀或库。
     * 所有登录入口（账密、短信、社交、SSO）都必须走这里收尾。
     */
    public Map<String, Object> createTokenWithOrgSession(UserInfo userInfo) {
        userInfo.setOrganizationCode(resolveOrganizationCode(userInfo.getOrganizationId()));
        Map<String, Object> token = tokenService.createToken(userInfo);
        resetOrganizationDatabase(userInfo);
        return token;
    }
}
