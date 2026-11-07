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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.system.domain.SystemSsoOidcConfig;
import com.privernal.system.dto.SsoOidcConfigDTO;
import com.privernal.system.filter.SsoOidcConfigFilterDTO;
import com.privernal.system.mapper.SystemSsoOidcConfigMapper;
import com.privernal.system.support.MachineSecretCipher;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSO（OIDC 授权码流）渠道配置。
 * <p>
 * client_secret 由外部 IdP 下发、我们只做存储，因此与 OpenAPI 凭证同理用可逆密文保存
 * （授权码换令牌时必须能还原出原文），明文只在保存成功的那次响应里回显。
 * <p>
 * 一个渠道绑死一个组织：外部身份落库时要写 organization_id（NOT NULL），
 * 而免登阶段没有会话组织，只能由配置提供，这也决定了「同一 IdP 的账号属于哪个租户」。
 */
@Slf4j
@Service
public class SsoOidcConfigService {

    public static final int STATUS_ENABLED = 1;

    /**
     * system_social_user.source 的取值前缀，与钉钉/企微等既有来源区分开
     */
    public static final String SOURCE_PREFIX = "oidc_";

    public static final String CLAIM_EXTERNAL_USER_ID = "externalUserId";
    public static final String CLAIM_USER_NAME = "userName";
    public static final String CLAIM_NICK_NAME = "nickName";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_PHONE = "phone";
    public static final String CLAIM_DEPARTMENT = "department";

    private static final Map<String, String> DEFAULT_CLAIMS = defaultClaims();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Autowired
    private SystemSsoOidcConfigMapper configMapper;

    @Autowired
    private MachineSecretCipher secretCipher;

    @Transactional(readOnly = true)
    public List<SsoOidcConfigDTO> selectList(SsoOidcConfigFilterDTO filter) {
        LambdaQueryWrapper<SystemSsoOidcConfig> wrapper = new LambdaQueryWrapper<>();
        if (filter != null) {
            wrapper.eq(StringUtils.isNotEmpty(filter.getConfigCode()),
                    SystemSsoOidcConfig::getConfigCode, filter.getConfigCode());
            wrapper.like(StringUtils.isNotEmpty(filter.getConfigName()),
                    SystemSsoOidcConfig::getConfigName, filter.getConfigName());
            wrapper.eq(filter.getStatus() != null, SystemSsoOidcConfig::getStatus, filter.getStatus());
            wrapper.eq(StringUtils.isNotEmpty(filter.getOrganizationId()),
                    SystemSsoOidcConfig::getOrganizationId, filter.getOrganizationId());
        }
        wrapper.orderByDesc(SystemSsoOidcConfig::getCreatedTime);
        List<SsoOidcConfigDTO> result = new ArrayList<>();
        for (SystemSsoOidcConfig entity : configMapper.selectList(wrapper)) {
            result.add(toDTO(entity));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SsoOidcConfigDTO selectById(String id) {
        return toDTO(requireEntity(id));
    }

    @Transactional
    public SsoOidcConfigDTO create(SsoOidcConfigDTO dto) {
        validate(dto);
        if (!secretCipher.isConfigured()) {
            throw new ServiceException("服务端未配置 openapi.secret-master-key，无法安全存储 client_secret");
        }
        SystemSsoOidcConfig entity = new SystemSsoOidcConfig();
        applyEditableFields(entity, dto);
        if (StringUtils.isEmpty(dto.getClientSecret())) {
            throw new ServiceException("client_secret 不能为空");
        }
        applySecret(entity, dto.getClientSecret());
        entity.setStatus(dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus());
        configMapper.insert(entity);
        SsoOidcConfigDTO result = toDTO(entity);
        result.setClientSecret(dto.getClientSecret());
        return result;
    }

    /**
     * 修改配置。client_secret 留空表示保持原值不动，不能因为前端只回显了掩码就把它清空。
     */
    @Transactional
    public SsoOidcConfigDTO update(SsoOidcConfigDTO dto) {
        SystemSsoOidcConfig entity = requireEntity(dto.getId());
        validate(dto);
        applyEditableFields(entity, dto);
        String plainSecret = null;
        if (StringUtils.isNotEmpty(dto.getClientSecret())) {
            if (!secretCipher.isConfigured()) {
                throw new ServiceException("服务端未配置 openapi.secret-master-key，无法安全存储 client_secret");
            }
            applySecret(entity, dto.getClientSecret());
            plainSecret = dto.getClientSecret();
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        configMapper.updateById(entity);
        SsoOidcConfigDTO result = toDTO(entity);
        result.setClientSecret(plainSecret);
        return result;
    }

    @Transactional
    public int deleteByIds(String[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return configMapper.deleteBatchIds(Arrays.asList(ids));
    }

    public String revealSecret(SystemSsoOidcConfig config) {
        if (StringUtils.isEmpty(config.getClientSecretCipher())) {
            return null;
        }
        return secretCipher.decrypt(config.getClientSecretCipher());
    }

    /**
     * 登录页渠道列表。免登阶段没有会话组织，必须忽略组织隔离，否则条件会被拼成 organization_id = NULL。
     */
    @Transactional(readOnly = true)
    public List<SsoOidcConfigDTO> selectEnabledForLogin() {
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            LambdaQueryWrapper<SystemSsoOidcConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemSsoOidcConfig::getStatus, STATUS_ENABLED);
            wrapper.orderByAsc(SystemSsoOidcConfig::getConfigCode);
            List<SsoOidcConfigDTO> result = new ArrayList<>();
            for (SystemSsoOidcConfig entity : configMapper.selectList(wrapper)) {
                SsoOidcConfigDTO dto = toDTO(entity);
                // 登录页只需要展示与跳转信息，密钥掩码也不给
                dto.setClientSecretPrefix(null);
                dto.setClientSecretLast4(null);
                result.add(dto);
            }
            return result;
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
        }
    }

    @Transactional(readOnly = true)
    public SystemSsoOidcConfig selectByConfigCode(String configCode) {
        if (StringUtils.isEmpty(configCode)) {
            return null;
        }
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            LambdaQueryWrapper<SystemSsoOidcConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemSsoOidcConfig::getConfigCode, configCode);
            wrapper.eq(SystemSsoOidcConfig::getStatus, STATUS_ENABLED);
            wrapper.last("limit 1");
            return configMapper.selectOne(wrapper);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
        }
    }

    /**
     * 发现文档连通性测试，并把解析到的端点回填到空着的手填字段上
     */
    @Transactional
    public SsoOidcConfigDTO testConnection(String id) {
        SystemSsoOidcConfig entity = requireEntity(id);
        JSONObject discovery = fetchDiscovery(entity);
        applyDiscovery(entity, discovery, true);
        configMapper.updateById(entity);
        return toDTO(entity);
    }

    /**
     * 拉发现文档。配置了 discoveryUrl 用配置的，否则由 issuer 拼标准路径。
     */
    public JSONObject fetchDiscovery(SystemSsoOidcConfig config) {
        String url = StringUtils.isNotEmpty(config.getDiscoveryUrl()) ? config.getDiscoveryUrl()
                : joinIssuer(config.getIssuer()) + "/.well-known/openid-configuration";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ServiceException("发现文档地址必须是 http(s) 地址：" + url);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ServiceException("发现文档返回 " + response.statusCode() + "：" + url);
            }
            JSONObject json = JSONObject.parseObject(response.body());
            if (json == null || StringUtils.isEmpty(json.getString("authorization_endpoint"))) {
                throw new ServiceException("发现文档缺少 authorization_endpoint：" + url);
            }
            return json;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("拉取发现文档失败：" + e.getMessage());
        }
    }

    /**
     * 端点取值：手填优先，缺的用发现文档补；两边都没有则抛错，避免拼出半截授权地址
     */
    public void ensureEndpoints(SystemSsoOidcConfig config) {
        if (StringUtils.isEmpty(config.getTokenUrl())) {
            applyDiscovery(config, fetchDiscovery(config), false);
        }
        if (StringUtils.isEmpty(config.getAuthorizeUrl()) || StringUtils.isEmpty(config.getTokenUrl())) {
            throw new ServiceException("渠道 " + config.getConfigCode() + " 未配置且无法发现 authorize/token 端点");
        }
    }

    /**
     * claim 名解析：配置里给了就用配置的，没给用标准名
     */
    public static String claimName(SystemSsoOidcConfig config, String logicalKey) {
        JSONObject mapping = config == null ? null : config.getClaimMapping();
        String configured = mapping == null ? null : mapping.getString(logicalKey);
        return StringUtils.isNotEmpty(configured) ? configured : DEFAULT_CLAIMS.get(logicalKey);
    }

    public static String sourceOf(SystemSsoOidcConfig config) {
        return SOURCE_PREFIX + config.getConfigCode();
    }

    private void applyDiscovery(SystemSsoOidcConfig entity, JSONObject discovery, boolean fillIssuer) {
        if (StringUtils.isEmpty(entity.getAuthorizeUrl())) {
            entity.setAuthorizeUrl(discovery.getString("authorization_endpoint"));
        }
        if (StringUtils.isEmpty(entity.getTokenUrl())) {
            entity.setTokenUrl(discovery.getString("token_endpoint"));
        }
        if (StringUtils.isEmpty(entity.getUserinfoUrl())) {
            entity.setUserinfoUrl(discovery.getString("userinfo_endpoint"));
        }
        if (StringUtils.isEmpty(entity.getJwksUri())) {
            entity.setJwksUri(discovery.getString("jwks_uri"));
        }
        if (fillIssuer && StringUtils.isEmpty(entity.getIssuer())) {
            entity.setIssuer(discovery.getString("issuer"));
        }
    }

    private SystemSsoOidcConfig requireEntity(String id) {
        SystemSsoOidcConfig entity = configMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("SSO渠道不存在：" + id);
        }
        return entity;
    }

    private void validate(SsoOidcConfigDTO dto) {
        if (StringUtils.isEmpty(dto.getConfigCode())) {
            throw new ServiceException("渠道编码不能为空");
        }
        if (!dto.getConfigCode().matches("^[A-Za-z][A-Za-z0-9_-]{1,49}$")) {
            throw new ServiceException("渠道编码需以字母开头，仅允许字母、数字、下划线和连字符，长度 2-50");
        }
        if (StringUtils.isEmpty(dto.getConfigName())) {
            throw new ServiceException("渠道名称不能为空");
        }
        if (StringUtils.isEmpty(dto.getClientId())) {
            throw new ServiceException("client_id 不能为空");
        }
        if (StringUtils.isEmpty(dto.getDiscoveryUrl()) && StringUtils.isEmpty(dto.getIssuer())) {
            throw new ServiceException("issuer 与发现文档地址至少填一个");
        }
        if (StringUtils.isNotEmpty(dto.getRedirectUri())
                && !dto.getRedirectUri().startsWith("http://") && !dto.getRedirectUri().startsWith("https://")) {
            throw new ServiceException("回调地址必须是 http(s) 地址");
        }
    }

    private void applyEditableFields(SystemSsoOidcConfig entity, SsoOidcConfigDTO dto) {
        entity.setConfigCode(dto.getConfigCode().trim());
        entity.setConfigName(dto.getConfigName());
        entity.setIcon(dto.getIcon());
        entity.setIssuer(StringUtils.trimToNull(dto.getIssuer()));
        entity.setDiscoveryUrl(StringUtils.trimToNull(dto.getDiscoveryUrl()));
        entity.setAuthorizeUrl(StringUtils.trimToNull(dto.getAuthorizeUrl()));
        entity.setTokenUrl(StringUtils.trimToNull(dto.getTokenUrl()));
        entity.setUserinfoUrl(StringUtils.trimToNull(dto.getUserinfoUrl()));
        entity.setJwksUri(StringUtils.trimToNull(dto.getJwksUri()));
        entity.setClientId(dto.getClientId());
        entity.setScopes(StringUtils.isEmpty(dto.getScopes()) ? "openid profile email" : dto.getScopes());
        entity.setRedirectUri(StringUtils.trimToNull(dto.getRedirectUri()));
        entity.setClaimMapping(dto.getClaimMapping());
        entity.setAutoRegister(dto.getAutoRegister() == null ? 0 : dto.getAutoRegister());
        entity.setDefaultRoleIds(dto.getDefaultRoleIds());
        entity.setRemark(dto.getRemark());
    }

    private void applySecret(SystemSsoOidcConfig entity, String plainSecret) {
        entity.setClientSecretCipher(secretCipher.encrypt(plainSecret));
        entity.setClientSecretPrefix(MachineSecretCipher.prefix(plainSecret));
        entity.setClientSecretLast4(MachineSecretCipher.last4(plainSecret));
    }

    private SsoOidcConfigDTO toDTO(SystemSsoOidcConfig entity) {
        SsoOidcConfigDTO dto = new SsoOidcConfigDTO();
        dto.setId(entity.getId());
        dto.setConfigCode(entity.getConfigCode());
        dto.setConfigName(entity.getConfigName());
        dto.setIcon(entity.getIcon());
        dto.setIssuer(entity.getIssuer());
        dto.setDiscoveryUrl(entity.getDiscoveryUrl());
        dto.setAuthorizeUrl(entity.getAuthorizeUrl());
        dto.setTokenUrl(entity.getTokenUrl());
        dto.setUserinfoUrl(entity.getUserinfoUrl());
        dto.setJwksUri(entity.getJwksUri());
        dto.setClientId(entity.getClientId());
        dto.setClientSecretPrefix(entity.getClientSecretPrefix());
        dto.setClientSecretLast4(entity.getClientSecretLast4());
        dto.setScopes(entity.getScopes());
        dto.setRedirectUri(entity.getRedirectUri());
        dto.setClaimMapping(entity.getClaimMapping());
        dto.setAutoRegister(entity.getAutoRegister());
        dto.setDefaultRoleIds(entity.getDefaultRoleIds());
        dto.setStatus(entity.getStatus());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setRemark(entity.getRemark());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());
        return dto;
    }

    private static String joinIssuer(String issuer) {
        if (StringUtils.isEmpty(issuer)) {
            throw new ServiceException("渠道未配置 issuer 或发现文档地址");
        }
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }

    private static Map<String, String> defaultClaims() {
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put(CLAIM_EXTERNAL_USER_ID, "sub");
        claims.put(CLAIM_USER_NAME, "preferred_username");
        claims.put(CLAIM_NICK_NAME, "name");
        claims.put(CLAIM_EMAIL, "email");
        claims.put(CLAIM_PHONE, "phone_number");
        claims.put(CLAIM_DEPARTMENT, "department");
        return claims;
    }
}
