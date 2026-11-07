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

package com.privernal.system.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.system.api.ISecretCipherService;
import com.privernal.system.domain.SystemAiConfig;
import com.privernal.system.mapper.SystemAiConfigMapper;
import com.privernal.system.service.ISystemAiConfigService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 模型配置服务实现（v1.4 公网模型口径）。
 * <p>
 * 安全口径：apiKey 只存密文（{@link ISecretCipherService}）；管理查询只回 prefix/last4；
 * 密钥未配置时报错而非降级明文（fail-closed，与 OpenAPI 凭证同纪律）。
 * 数据流向合规：管理接口固定携带 {@link SystemAiConfig#DATA_FLOW_NOTICE}（v1.4：配置即授权 + 明示）。
 */
@Slf4j
@Service
public class SystemAiConfigServiceImpl implements ISystemAiConfigService {

    @Autowired
    private SystemAiConfigMapper configMapper;

    @Autowired(required = false)
    private ISecretCipherService secretCipherService;

    @Override
    public Map<String, Object> currentWithNotice() {
        Map<String, Object> out = new HashMap<>();
        out.put("dataFlowNotice", SystemAiConfig.DATA_FLOW_NOTICE);
        SystemAiConfig config = currentRow();
        if (config != null) {
            config.setApiKeyCipher(null);
            out.put("config", config);
        } else {
            out.put("config", null);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemAiConfig save(SystemAiConfig patch, String rawApiKey) {
        if (patch == null || StringUtils.isEmpty(patch.getBaseUrl())) {
            throw new ServiceException("baseUrl 不能为空（公网模型填公网 API，内网模型填内网推理地址）");
        }
        if (patch.getBaseUrl() != null && !patch.getBaseUrl().startsWith("http://")
                && !patch.getBaseUrl().startsWith("https://")) {
            throw new ServiceException("baseUrl 必须以 http:// 或 https:// 开头");
        }
        boolean intranet = "Y".equals(patch.getIntranetFlag());
        if (!intranet && "Y".equals(patch.getEnabled()) && StringUtils.isEmpty(rawApiKey)
                && currentRow() == null) {
            throw new ServiceException("公网模型必须配置 apiKey");
        }
        SystemAiConfig existing = currentRow();
        SystemAiConfig row = existing == null ? new SystemAiConfig() : existing;
        row.setOrganizationId(currentOrgId());
        row.setProvider(patch.getProvider());
        row.setBaseUrl(patch.getBaseUrl());
        row.setModel(patch.getModel());
        row.setTemperature(patch.getTemperature());
        row.setSessionTokenLimit(patch.getSessionTokenLimit());
        row.setIntranetFlag(intranet ? "Y" : "N");
        row.setEnabled(patch.getEnabled() == null
                ? (existing == null ? "N" : existing.getEnabled())
                : ("Y".equals(patch.getEnabled()) ? "Y" : "N"));
        if (StringUtils.isNotEmpty(rawApiKey)) {
            saveApiKey(row, rawApiKey);
        }
        if (existing == null) {
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
        log.info("AI 模型配置已保存 org={} provider={} baseUrl={} intranet={}",
                row.getOrganizationId(), row.getProvider(), maskUrl(row.getBaseUrl()), row.getIntranetFlag());
        return mask(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeEnabled(String enabled) {
        SystemAiConfig existing = currentRow();
        if (existing == null) {
            throw new ServiceException("尚未配置模型，请先保存配置");
        }
        SystemAiConfig update = new SystemAiConfig();
        update.setId(existing.getId());
        update.setEnabled("Y".equals(enabled) ? "Y" : "N");
        configMapper.updateById(update);
    }

    @Override
    public SystemAiConfig effectiveConfig() {
        SystemAiConfig config = currentRow();
        if (config == null || !"Y".equals(config.getEnabled())) {
            return null;
        }
        return config;
    }

    // ---------------- 内部 ----------------

    /** 组织 id：登录会话优先，holder 兜底（管理请求线程上 holder 可能为空，实测踩坑） */
    private String currentOrgId() {
        String orgId = com.privernal.common.security.service.SecurityUtils.getOrganizationId();
        if (StringUtils.isEmpty(orgId)) {
            orgId = OrganizationContextHolder.getOrganizationId();
        }
        return orgId;
    }

    private SystemAiConfig currentRow() {
        String orgId = currentOrgId();
        return configMapper.selectOne(Wrappers.<SystemAiConfig>lambdaQuery()
                .eq(SystemAiConfig::getOrganizationId, orgId)
                .last("LIMIT 1"));
    }

    /** apiKey 统一加密落库；密文能力不可用必须报错，绝不存明文（fail-closed） */
    private void saveApiKey(SystemAiConfig row, String rawApiKey) {
        if (secretCipherService == null || !secretCipherService.isConfigured()) {
            throw new ServiceException("未配置 openapi.secret-master-key，无法加密 apiKey；请先配置主密钥再保存模型配置");
        }
        row.setApiKeyCipher(secretCipherService.encrypt(rawApiKey));
        row.setApiKeyPrefix(com.privernal.system.support.MachineSecretCipher.prefix(rawApiKey));
        row.setApiKeyLast4(com.privernal.system.support.MachineSecretCipher.last4(rawApiKey));
    }

    /** 管理面掩码视图：抹掉密文与明文痕迹 */
    private SystemAiConfig mask(SystemAiConfig row) {
        SystemAiConfig masked = new SystemAiConfig();
        org.springframework.beans.BeanUtils.copyProperties(row, masked);
        masked.setApiKeyCipher(null);
        return masked;
    }

    private static String maskUrl(String url) {
        return url == null ? null : url.replaceAll("([a-zA-Z0-9._-]{4})[a-zA-Z0-9._-]*\\.[a-z]{2,}", "$1.***");
    }
}
