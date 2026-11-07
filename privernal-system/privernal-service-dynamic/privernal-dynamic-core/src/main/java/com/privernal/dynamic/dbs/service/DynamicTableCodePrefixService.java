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

package com.privernal.dynamic.dbs.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.system.dto.SystemOrganizationDTO;
import com.privernal.system.feign.RemoteUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组织创建的动态表统一带上企业组织编码前缀。
 * <p>
 * 动态表单的 table_code 就是物理表名（见 {@code DynamicForm#getTableCode}），所以加前缀是「一次改名」，
 * 菜单路径、权限标识 dynamic:{code}:*、meta_data 里的关联 formCode 都以它为准。
 * 前缀只解决同一库里按组织归拢与可识别，数据隔离仍由 organization_id 与动态数据源负责。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DynamicTableCodePrefixService {

    /**
     * MySQL 标识符上限 64；子表名（主表_字段）与索引名还要在表名后拼后缀，留出余量在迁移脚本里做
     */
    private static final int MAX_TABLE_CODE_LENGTH = 64;

    /**
     * 组织编码按 orgId 缓存：组织数很少，只在首次建表时回查一次
     */
    private final Map<String, String> prefixCache = new ConcurrentHashMap<>();

    @Autowired
    private RemoteUserService remoteUserService;

    /**
     * 给表单编码补上当前组织前缀，已带前缀的原样返回（幂等，重复调用不会二次加前缀）
     *
     * @param tableCode 用户录入或导入的表单编码
     * @return 带前缀的表单编码；取不到组织编码时原样返回，不阻断建表
     */
    public String decorate(String tableCode) {
        return decorate(tableCode, null);
    }

    /**
 * 显式指定组织的前缀解析重载（第4项）：
     * 调度/执行线程没有登录态，SecurityUtils 取不到组织 → 前缀为空 → decorate
     * 原样返回逻辑表名，动态表/报表类动作会查错表。
     * 解析优先级：OrganizationContextHolder（调度线程执行前已 set）→ 入参 orgId →
     * 会话（Web 线程原路径不变）。
     */
    public String decorate(String tableCode, String organizationId) {
        if (StringUtils.isBlank(tableCode)) {
            return tableCode;
        }
        String code = tableCode.trim();
        String prefix = currentPrefix(organizationId);
        if (StringUtils.isEmpty(prefix) || code.toLowerCase().startsWith(prefix)) {
            return code;
        }
        String decorated = prefix + code;
        if (decorated.length() > MAX_TABLE_CODE_LENGTH) {
            throw new ServiceException(
                    "表单编码过长：企业组织编码前缀 " + prefix + " 加上编码不能超过 " + MAX_TABLE_CODE_LENGTH + " 个字符，当前 " + decorated.length() + " 个");
        }
        return decorated;
    }

    private String currentPrefix() {
        return currentPrefix(null);
    }

    private String currentPrefix(String fallbackOrgId) {
        String orgId = SecurityUtils.getOrganizationId();
        if (StringUtils.isBlank(orgId) && OrganizationContextHolder.getOrganizationId() != null) {
            // 非 Web 线程（调度/执行/Webhook）：先读线程上下文里的组织
            orgId = OrganizationContextHolder.getOrganizationId();
        }
        if (StringUtils.isBlank(orgId)) {
            orgId = fallbackOrgId;
        }
        if (StringUtils.isBlank(orgId)) {
            return "";
        }
        String cached = prefixCache.get(orgId);
        if (cached != null) {
            return cached;
        }
        String prefix = resolvePrefix(orgId);
        // 组织数量级很小，只在异常膨胀时清一次，避免长期持有已删除组织的编码
        if (prefixCache.size() > 512) {
            prefixCache.clear();
        }
        prefixCache.put(orgId, prefix);
        return prefix;
    }

    private String resolvePrefix(String orgId) {
        // 登录时已把组织编码放进会话，只有旧 token 与非 Web 上下文才回查
        String organizationCode = SecurityUtils.getOrganizationCode();
        if (StringUtils.isBlank(organizationCode)) {
            try {
                CommonResult<SystemOrganizationDTO> result = remoteUserService.findOrganizationById(orgId, "");
                if (result != null && result.getData() != null) {
                    organizationCode = result.getData().getOrganizationCode();
                }
            } catch (Exception e) {
                log.warn("查询组织编码失败，本次建表不加前缀 organizationId {}", orgId, e);
                return "";
            }
        }
        String normalized = normalize(organizationCode);
        if (StringUtils.isBlank(normalized)) {
            log.warn("组织编码不能用作表名前缀，本次建表不加前缀 organizationId {} organizationCode {}", orgId, organizationCode);
            return "";
        }
        return normalized + "_";
    }

    /**
     * 组织编码允许中文等字符，表名只能落 [a-z0-9_] 且必须以字母开头
     */
    private static String normalize(String organizationCode) {
        if (StringUtils.isBlank(organizationCode)) {
            return "";
        }
        StringBuilder prefix = new StringBuilder();
        for (char ch : organizationCode.trim().toLowerCase().toCharArray()) {
            boolean letter = ch >= 'a' && ch <= 'z';
            boolean digit = ch >= '0' && ch <= '9';
            if (letter || ((digit || ch == '_') && prefix.length() > 0)) {
                prefix.append(ch);
            }
        }
        return prefix.toString();
    }
}
