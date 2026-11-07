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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.system.domain.SystemSsoOidcConfig;
import com.privernal.system.dto.SystemSocialUserDTO;
import com.privernal.system.service.SsoOidcConfigService;
import com.alibaba.fastjson2.JSONObject;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.JWTVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OIDC 授权码流的对外交互：拼授权地址、用授权码换令牌、校验 id_token、取 userinfo、映射成本地社交账号。
 * <p>
 * 只做协议，不碰登录态：换到的外部身份交给 {@code SystemSocialUser} 那套绑定链路落地成本地用户，
 * 建 token 由调用方（{@code LoginController} 同级的那个 controller）收尾。
 */
@Slf4j
@Service
public class OidcClientService {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 10;
    /**
     * 社交账号 open_id 的渠道命名空间前缀，见 toSocialUser 里的说明
     */
    private static final String OPEN_ID_PREFIX = "oidc:";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * JWKS 拉取有网络开销，按地址缓存；密钥轮换靠 java-jwt 拿不到 kid 时重新加载
     */
    private final Map<String, JwkProvider> jwkProviders = new ConcurrentHashMap<>();

    /**
     * 授权地址：state 兼作 nonce，回调时既验 CSRF 也验 id_token 归属
     */
    public String buildAuthorizeUrl(SystemSsoOidcConfig config, String state, String nonce) {
        StringBuilder url = new StringBuilder(config.getAuthorizeUrl())
                .append(config.getAuthorizeUrl().contains("?") ? "&" : "?")
                .append("response_type=code")
                .append("&client_id=").append(encode(config.getClientId()))
                .append("&redirect_uri=").append(encode(config.getRedirectUri()))
                .append("&scope=").append(encode(StringUtils.isEmpty(config.getScopes()) ? "openid" : config.getScopes()))
                .append("&state=").append(encode(state));
        if (StringUtils.isNotEmpty(nonce)) {
            url.append("&nonce=").append(encode(nonce));
        }
        return url.toString();
    }

    /**
     * 授权码换令牌。client_secret 走表单体（绝大多数 IdP 的默认方式），
     * 需要 client_secret_basic 的 IdP 也普遍接受 body，先不做分支。
     */
    public JSONObject exchangeCode(SystemSsoOidcConfig config, String code, String plainSecret) {
        if (StringUtils.isEmpty(plainSecret)) {
            throw new ServiceException("渠道 " + config.getConfigCode() + " 缺少 client_secret");
        }
        StringBuilder form = new StringBuilder()
                .append("grant_type=authorization_code")
                .append("&code=").append(encode(code))
                .append("&client_id=").append(encode(config.getClientId()))
                .append("&client_secret=").append(encode(plainSecret));
        if (StringUtils.isNotEmpty(config.getRedirectUri())) {
            form.append("&redirect_uri=").append(encode(config.getRedirectUri()));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getTokenUrl()))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                .build();
        String body = send(request);
        JSONObject json = JSONObject.parseObject(body);
        if (json == null) {
            throw new ServiceException("令牌端点返回空响应");
        }
        if (StringUtils.isNotEmpty(json.getString("error"))) {
            throw new ServiceException("换取令牌失败：" + json.getString("error_description")
                    + "（" + json.getString("error") + "）");
        }
        if (StringUtils.isEmpty(json.getString("access_token")) && StringUtils.isEmpty(json.getString("id_token"))) {
            throw new ServiceException("令牌端点未返回 access_token 或 id_token");
        }
        return json;
    }

    /**
     * 取外部身份声明：优先校验过的 id_token，没有 id_token 时回退 userinfo 端点。
     * <p>
     * 回退是必要的：部分 IdP 在 scope 未含 openid 时不发 id_token，此时只能信 TLS 通道下的 userinfo。
     */
    public JSONObject resolveClaims(SystemSsoOidcConfig config, JSONObject tokenResponse, String state) {
        String idToken = tokenResponse.getString("id_token");
        if (StringUtils.isNotEmpty(idToken)) {
            return verifyIdToken(config, idToken, state);
        }
        String accessToken = tokenResponse.getString("access_token");
        if (StringUtils.isEmpty(accessToken) || StringUtils.isEmpty(config.getUserinfoUrl())) {
            throw new ServiceException("令牌响应里既没有 id_token 也没有可用的 userinfo 地址");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getUserinfoUrl()))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        JSONObject claims = JSONObject.parseObject(send(request));
        if (claims == null || StringUtils.isEmpty(claims.getString("sub"))) {
            throw new ServiceException("userinfo 未返回 sub");
        }
        // userinfo 场景没有 state/nonce 可校验，靠 access_token 与 TLS；记录一次渠道与 subject 便于排查
        log.info("OIDC 走 userinfo 取身份 configCode={} sub={}", config.getConfigCode(), claims.getString("sub"));
        return claims;
    }

    /**
     * id_token 校验：JWKS 验签 + iss + aud(client_id) + exp（java-jwt 内建）+ nonce
     */
    private JSONObject verifyIdToken(SystemSsoOidcConfig config, String idToken, String state) {
        if (StringUtils.isEmpty(config.getJwksUri())) {
            throw new ServiceException("渠道 " + config.getConfigCode() + " 未配置 jwks_uri，无法校验 id_token");
        }
        try {
            DecodedJWT header = JWT.decode(idToken);
            if (StringUtils.isEmpty(header.getKeyId())) {
                throw new ServiceException("id_token 未携带 kid，无法定位签名密钥");
            }
            JwkProvider provider = jwkProviders.computeIfAbsent(config.getJwksUri(), this::buildJwkProvider);
            Jwk jwk = provider.get(header.getKeyId());
            RSAPublicKey publicKey = (RSAPublicKey) jwk.getPublicKey();
            JWTVerifier.BaseVerification verification =
                    (JWTVerifier.BaseVerification) JWT.require(Algorithm.RSA256(publicKey, null));
            if (StringUtils.isNotEmpty(config.getIssuer())) {
                verification.withIssuer(config.getIssuer());
            }
            DecodedJWT verified = verification.withAudience(config.getClientId()).build().verify(idToken);
            String idNonce = verified.getClaim("nonce").asString();
            if (StringUtils.isNotEmpty(idNonce)) {
                if (!idNonce.equals(state)) {
                    throw new ServiceException("id_token 的 nonce 与本次请求不一致");
                }
            } else {
                log.warn("渠道 {} 的 id_token 未携带 nonce，跳过防注入校验 sub={}",
                        config.getConfigCode(), verified.getSubject());
            }
            return JSONObject.parseObject(verified.getPayload());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("id_token 校验失败：" + e.getMessage());
        }
    }

    /**
     * 外部身份 → 本地社交账号入参。
     * <p>
     * open_id 加了 {@code oidc:{configCode}:} 命名空间：既有绑定链路里
     * {@code selectSysUserSocialUserByOpenId} 只按 open_id 单列查，不带 source/app_code，
     * 不同 IdP 的 subject 若字面值相同就会被认成同一个人。加前缀在不改既有链路的前提下消除串号。
     */
    public SystemSocialUserDTO toSocialUser(SystemSsoOidcConfig config, JSONObject claims, String accessToken) {
        String externalUserId = claimsOf(config, claims, SsoOidcConfigService.CLAIM_EXTERNAL_USER_ID);
        if (StringUtils.isEmpty(externalUserId)) {
            throw new ServiceException("外部身份缺少用户标识（claim "
                    + SsoOidcConfigService.claimName(config, SsoOidcConfigService.CLAIM_EXTERNAL_USER_ID) + "）");
        }
        String nickName = claimsOf(config, claims, SsoOidcConfigService.CLAIM_NICK_NAME);
        String userName = claimsOf(config, claims, SsoOidcConfigService.CLAIM_USER_NAME);
        String email = claimsOf(config, claims, SsoOidcConfigService.CLAIM_EMAIL);
        String phone = claimsOf(config, claims, SsoOidcConfigService.CLAIM_PHONE);

        SystemSocialUserDTO dto = new SystemSocialUserDTO();
        dto.setSource(SsoOidcConfigService.sourceOf(config));
        dto.setAppCode(config.getConfigCode());
        dto.setOpenId(OPEN_ID_PREFIX + config.getConfigCode() + ":" + externalUserId);
        dto.setUuid(externalUserId);
        dto.setNickName(StringUtils.isNotEmpty(nickName) ? nickName : userName);
        dto.setEmail(email);
        dto.setPurePhoneNumber(phone);
        dto.setAccessToken(accessToken);
        dto.setIdToken(claims.toJSONString());
        dto.setOrganizationId(config.getOrganizationId());
        dto.setDepartmentId(config.getDepartmentId());
        dto.setRoleId(firstRoleId(config.getDefaultRoleIds()));
        return dto;
    }

    private String claimsOf(SystemSsoOidcConfig config, JSONObject claims, String logicalKey) {
        String claimName = SsoOidcConfigService.claimName(config, logicalKey);
        Object value = StringUtils.isEmpty(claimName) ? null : claims.get(claimName);
        return value == null ? null : String.valueOf(value);
    }

    private String firstRoleId(String defaultRoleIds) {
        if (StringUtils.isEmpty(defaultRoleIds)) {
            return null;
        }
        return defaultRoleIds.split(",")[0].trim();
    }

    private JwkProvider buildJwkProvider(String jwksUri) {
        try {
            return new JwkProviderBuilder(jwksUri).build();
        } catch (Exception e) {
            throw new ServiceException("加载 JWKS 失败：" + e.getMessage());
        }
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new ServiceException("外部身份服务返回 " + status + "：" + StringUtils.substring(response.body(), 0, 300));
            }
            return response.body();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("调用外部身份服务失败：" + e.getMessage());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
