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
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.domain.SystemOpenapiCredential;
import com.privernal.system.dto.OpenApiCredentialDTO;
import com.privernal.system.dto.OpenApiTableGrantDTO;
import com.privernal.system.filter.OpenApiCredentialFilterDTO;
import com.privernal.system.mapper.SystemOpenapiCredentialMapper;
import com.privernal.system.support.MachineSecretCipher;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * OpenAPI 应用凭证：生成、轮换、吊销与授权判定。
 * <p>
 * 明文 Secret 只在 create/rotate 的返回值里出现一次，其余出口一律掩码。
 */
@Slf4j
@Service
public class OpenApiCredentialService {

    public static final String SCOPE_DATA_READ = "data:read";
    public static final String SCOPE_DATA_WRITE = "data:write";
    public static final String SCOPE_META_READ = "meta:read";
    public static final String SCOPE_FLOW_START = "flow:start";
    /** 定时任务注册/改期/取消（；准入比 flow:start 更严——只允许 HTTP_CALL/MESSAGE_NOTIFY 动作） */
    public static final String SCOPE_SCHEDULE_WRITE = "schedule:write";
    public static final String SCOPE_SCHEDULE_READ = "schedule:read";
    /**
     * AI 开发专用范围：只允许 /openapi/ai/** 的一档面
     * （读/纯校验/草稿/试运行）。过滤器交叉强制：ai:build 凭证访问其它通道、
     * 其它凭证访问 /openapi/ai/** 一律拒绝并记 DENIED——二档/三档动作在该通道上"不存在"。
     */
    public static final String SCOPE_AI_BUILD = "ai:build";

    private static final Set<String> VALID_SCOPES = new LinkedHashSet<>(
            Arrays.asList(SCOPE_DATA_READ, SCOPE_DATA_WRITE, SCOPE_META_READ, SCOPE_FLOW_START,
                    SCOPE_SCHEDULE_WRITE, SCOPE_SCHEDULE_READ, SCOPE_AI_BUILD));

    /** 动作白名单：读、单条读、增、改、删、发起流程。审批动作刻意不开放 */
    public static final String ACTION_LIST = "list";
    public static final String ACTION_READ = "read";
    public static final String ACTION_ADD = "add";
    public static final String ACTION_EDIT = "edit";
    public static final String ACTION_REMOVE = "remove";
    public static final String ACTION_START = "start";

    private static final Set<String> VALID_ACTIONS = new LinkedHashSet<>(
            Arrays.asList(ACTION_LIST, ACTION_READ, ACTION_ADD, ACTION_EDIT, ACTION_REMOVE, ACTION_START));

    @Autowired
    private SystemOpenapiCredentialMapper credentialMapper;

    @Autowired
    private MachineSecretCipher secretCipher;

    @Transactional(readOnly = true)
    public List<OpenApiCredentialDTO> selectSystemOpenapiCredentialList(OpenApiCredentialFilterDTO filter) {
        LambdaQueryWrapper<SystemOpenapiCredential> wrapper = new LambdaQueryWrapper<>();
        if (filter != null) {
            wrapper.eq(StringUtils.isNotEmpty(filter.getAppId()), SystemOpenapiCredential::getAppId, filter.getAppId());
            wrapper.like(StringUtils.isNotEmpty(filter.getCredentialName()),
                    SystemOpenapiCredential::getCredentialName, filter.getCredentialName());
            wrapper.eq(StringUtils.isNotEmpty(filter.getAccessKey()),
                    SystemOpenapiCredential::getAccessKey, filter.getAccessKey());
            wrapper.eq(filter.getStatus() != null, SystemOpenapiCredential::getStatus, filter.getStatus());
            wrapper.eq(StringUtils.isNotEmpty(filter.getOrganizationId()),
                    SystemOpenapiCredential::getOrganizationId, filter.getOrganizationId());
        }
        wrapper.orderByDesc(SystemOpenapiCredential::getCreatedTime);
        List<OpenApiCredentialDTO> result = new ArrayList<>();
        for (SystemOpenapiCredential entity : credentialMapper.selectList(wrapper)) {
            result.add(toMaskedDTO(entity));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public OpenApiCredentialDTO selectById(String id) {
        SystemOpenapiCredential entity = credentialMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("凭证不存在：" + id);
        }
        return toMaskedDTO(entity);
    }

    /**
     * 新建凭证，返回体里含一次性明文 Secret
     */
    @Transactional
    public OpenApiCredentialDTO createCredential(OpenApiCredentialDTO dto) {
        if (!secretCipher.isConfigured()) {
            throw new ServiceException("服务端未配置 openapi.secret-master-key，无法安全存储密钥");
        }
        if (StringUtils.isEmpty(dto.getAppId())) {
            throw new ServiceException("必须指定归属应用");
        }
        if (StringUtils.isEmpty(dto.getCredentialName())) {
            throw new ServiceException("凭证名称不能为空");
        }
        List<String> scopes = normalizeScopes(dto.getScopes());
        List<OpenApiTableGrantDTO> grants = normalizeGrants(dto.getTableGrants());

        SystemOpenapiCredential entity = new SystemOpenapiCredential();
        entity.setAppId(dto.getAppId());
        entity.setCredentialName(dto.getCredentialName());
        entity.setAccessKey(generateAccessKey());
        String secret = secretCipher.generateSecret();
        entity.setSecretCipher(secretCipher.encrypt(secret));
        entity.setSecretFingerprint(MachineSecretCipher.sha256Hex(secret));
        entity.setSecretPrefix(MachineSecretCipher.prefix(secret));
        entity.setSecretLast4(MachineSecretCipher.last4(secret));
        entity.setScopes(toScopesJson(scopes));
        entity.setTableGrant(toGrantJson(grants));
        // 数据归属默认落在当前操作人身上：机器写动态表时 created_by 要用它，留空会让写入撞 NOT NULL
        entity.setBoundUserId(StringUtils.isNotEmpty(dto.getBoundUserId())
                ? dto.getBoundUserId() : SecurityUtils.getUserId());
        entity.setBoundDepartmentId(StringUtils.isNotEmpty(dto.getBoundDepartmentId())
                ? dto.getBoundDepartmentId() : SecurityUtils.getDepartmentId());
        // 表列 department_id NOT NULL：实体此前缺字段导致 INSERT 缺列必败（B2 E2E 实测暴露的缺陷）
        entity.setDepartmentId(StringUtils.isNotEmpty(dto.getBoundDepartmentId())
                ? dto.getBoundDepartmentId() : SecurityUtils.getDepartmentId());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setExpireTime(dto.getExpireTime());
        entity.setRateLimitPerMinute(dto.getRateLimitPerMinute() == null ? 600 : dto.getRateLimitPerMinute());
        entity.setCallCount(0L);
        entity.setRemark(dto.getRemark());
        credentialMapper.insert(entity);

        OpenApiCredentialDTO result = toMaskedDTO(entity);
        result.setSecret(secret);
        return result;
    }

    /**
     * 修改凭证。Secret 只能通过 rotate 更换，避免列表编辑把明文误回写
     */
    @Transactional
    public OpenApiCredentialDTO updateCredential(OpenApiCredentialDTO dto) {
        SystemOpenapiCredential entity = credentialMapper.selectById(dto.getId());
        if (entity == null) {
            throw new ServiceException("凭证不存在：" + dto.getId());
        }
        if (StringUtils.isNotEmpty(dto.getCredentialName())) {
            entity.setCredentialName(dto.getCredentialName());
        }
        entity.setScopes(toScopesJson(normalizeScopes(dto.getScopes())));
        entity.setTableGrant(toGrantJson(normalizeGrants(dto.getTableGrants())));
        // 数据归属默认落在当前操作人身上：机器写动态表时 created_by 要用它，留空会让写入撞 NOT NULL
        entity.setBoundUserId(StringUtils.isNotEmpty(dto.getBoundUserId())
                ? dto.getBoundUserId() : SecurityUtils.getUserId());
        entity.setBoundDepartmentId(StringUtils.isNotEmpty(dto.getBoundDepartmentId())
                ? dto.getBoundDepartmentId() : SecurityUtils.getDepartmentId());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        entity.setExpireTime(dto.getExpireTime());
        if (dto.getRateLimitPerMinute() != null) {
            entity.setRateLimitPerMinute(dto.getRateLimitPerMinute());
        }
        entity.setRemark(dto.getRemark());
        credentialMapper.updateById(entity);
        return toMaskedDTO(entity);
    }

    /**
     * 轮换密钥，返回体里含一次性新明文 Secret
     */
    @Transactional
    public OpenApiCredentialDTO rotateSecret(String id) {
        if (!secretCipher.isConfigured()) {
            throw new ServiceException("服务端未配置 openapi.secret-master-key，无法安全存储密钥");
        }
        SystemOpenapiCredential entity = credentialMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException("凭证不存在：" + id);
        }
        String secret = secretCipher.generateSecret();
        entity.setSecretCipher(secretCipher.encrypt(secret));
        entity.setSecretFingerprint(MachineSecretCipher.sha256Hex(secret));
        entity.setSecretPrefix(MachineSecretCipher.prefix(secret));
        entity.setSecretLast4(MachineSecretCipher.last4(secret));
        credentialMapper.updateById(entity);

        OpenApiCredentialDTO result = toMaskedDTO(entity);
        result.setSecret(secret);
        return result;
    }

    @Transactional
    public int deleteByIds(String[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return credentialMapper.deleteBatchIds(Arrays.asList(ids));
    }

    /**
     * 按 AccessKey 取凭证，供验签过滤器使用。
     * <p>
     * 必须临时关闭组织隔离：验签发生在建立任何身份之前，此时没有会话组织，
     * 拦截器会把 organization_id 条件拼成 NULL，任何凭证都查不到。
     */
    @Transactional(readOnly = true)
    public SystemOpenapiCredential selectByAccessKey(String accessKey) {
        if (StringUtils.isEmpty(accessKey)) {
            return null;
        }
        Boolean oldIgnored = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            LambdaQueryWrapper<SystemOpenapiCredential> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemOpenapiCredential::getAccessKey, accessKey);
            wrapper.last("limit 1");
            return credentialMapper.selectOne(wrapper);
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnored);
        }
    }

    /**
     * 取出可用于 HMAC 的明文密钥
     */
    public String revealSecret(SystemOpenapiCredential credential) {
        return secretCipher.decrypt(credential.getSecretCipher());
    }

    /**
     * 凭证当前是否可用（状态 + 过期）
     */
    public static String checkUsable(SystemOpenapiCredential credential) {
        if (credential == null) {
            return "凭证不存在";
        }
        if (credential.getStatus() == null || credential.getStatus() != 1) {
            return "凭证已停用";
        }
        if (credential.getExpireTime() != null && credential.getExpireTime().before(new Date())) {
            return "凭证已过期";
        }
        return null;
    }

    public static boolean scopeAllows(SystemOpenapiCredential credential, String scope) {
        return parseScopes(credential.getScopes()).contains(scope);
    }

    /**
     * 表 + 动作白名单判定。写动作额外要求 data:write 范围，读动作要求 data:read
     */
    public static boolean grantAllows(SystemOpenapiCredential credential, String tableCode, String action) {
        if (StringUtils.isEmpty(tableCode) || StringUtils.isEmpty(action)) {
            return false;
        }
        List<OpenApiTableGrantDTO> grants = parseGrants(credential.getTableGrant());
        for (OpenApiTableGrantDTO grant : grants) {
            if (tableCode.equals(grant.getTableCode()) && grant.getActions() != null
                    && grant.getActions().contains(action)) {
                return true;
            }
        }
        return false;
    }

    private String generateAccessKey() {
        for (int i = 0; i < 5; i++) {
            String candidate = "ak_" + Long.toHexString(System.currentTimeMillis())
                    + Integer.toHexString(new java.util.Random().nextInt(0x7fffffff));
            candidate = candidate.substring(0, Math.min(32, candidate.length()));
            if (credentialMapper.selectCount(new LambdaQueryWrapper<SystemOpenapiCredential>()
                    .eq(SystemOpenapiCredential::getAccessKey, candidate)) == 0) {
                return candidate;
            }
        }
        throw new ServiceException("AccessKey 生成失败，请重试");
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new ServiceException("至少要有一个 API 范围");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (!VALID_SCOPES.contains(scope)) {
                throw new ServiceException("未知的 API 范围：" + scope);
            }
            distinct.add(scope);
        }
        return new ArrayList<>(distinct);
    }

    private List<OpenApiTableGrantDTO> normalizeGrants(List<OpenApiTableGrantDTO> grants) {
        List<OpenApiTableGrantDTO> result = new ArrayList<>();
        if (grants == null) {
            return result;
        }
        for (OpenApiTableGrantDTO grant : grants) {
            if (grant == null || StringUtils.isEmpty(grant.getTableCode())) {
                throw new ServiceException("授权项缺少表编码");
            }
            List<String> actions = new ArrayList<>();
            if (grant.getActions() != null) {
                for (String action : grant.getActions()) {
                    if (!VALID_ACTIONS.contains(action)) {
                        throw new ServiceException("未知的动作：" + action);
                    }
                    if (!actions.contains(action)) {
                        actions.add(action);
                    }
                }
            }
            OpenApiTableGrantDTO normalized = new OpenApiTableGrantDTO();
            normalized.setTableCode(grant.getTableCode());
            normalized.setActions(actions);
            result.add(normalized);
        }
        return result;
    }

    private JSONObject toScopesJson(List<String> scopes) {
        JSONObject json = new JSONObject();
        json.put("scopes", scopes);
        return json;
    }

    private JSONObject toGrantJson(List<OpenApiTableGrantDTO> grants) {
        JSONObject json = new JSONObject();
        json.put("grants", grants);
        return json;
    }

    public static List<String> parseScopes(JSONObject json) {
        if (json == null) {
            return new ArrayList<>();
        }
        JSONArray array = json.getJSONArray("scopes");
        if (array == null) {
            return new ArrayList<>();
        }
        return array.toJavaList(String.class);
    }

    public static List<OpenApiTableGrantDTO> parseGrants(JSONObject json) {
        if (json == null) {
            return new ArrayList<>();
        }
        JSONArray array = json.getJSONArray("grants");
        if (array == null) {
            return new ArrayList<>();
        }
        return JSON.parseArray(array.toJSONString(), OpenApiTableGrantDTO.class);
    }

    /**
     * 出口统一掩码：任何查询都不带出 secret
     */
    private OpenApiCredentialDTO toMaskedDTO(SystemOpenapiCredential entity) {
        OpenApiCredentialDTO dto = new OpenApiCredentialDTO();
        dto.setId(entity.getId());
        dto.setAppId(entity.getAppId());
        dto.setCredentialName(entity.getCredentialName());
        dto.setAccessKey(entity.getAccessKey());
        dto.setSecretPrefix(entity.getSecretPrefix());
        dto.setSecretLast4(entity.getSecretLast4());
        dto.setScopes(parseScopes(entity.getScopes()));
        dto.setTableGrants(parseGrants(entity.getTableGrant()));
        dto.setBoundUserId(entity.getBoundUserId());
        dto.setBoundDepartmentId(entity.getBoundDepartmentId());
        dto.setStatus(entity.getStatus());
        dto.setExpireTime(entity.getExpireTime());
        dto.setRateLimitPerMinute(entity.getRateLimitPerMinute());
        dto.setLastCallTime(entity.getLastCallTime());
        dto.setCallCount(entity.getCallCount());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setRemark(entity.getRemark());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedTime(entity.getUpdatedTime());
        dto.setUpdatedBy(entity.getUpdatedBy());
        return dto;
    }
}
