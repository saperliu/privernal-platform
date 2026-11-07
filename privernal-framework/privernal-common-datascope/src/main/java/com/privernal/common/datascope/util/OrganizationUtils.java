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

package com.privernal.common.datascope.util;


import com.privernal.common.datascope.context.OrganizationContextHolder;

import java.util.Map;
import java.util.concurrent.Callable;

import static com.privernal.common.security.service.SecurityUtils.HEADER_ORGANIZATION_ID;


/**
 * 多组织 Util
 *
 * @author saperliu
 */
public class OrganizationUtils {

    /**
     * 使用指定组织，执行对应的逻辑
     *
     * 注意，如果当前是忽略组织的情况下，会被强制设置成不忽略组织
     * 当然，执行完成后，还是会恢复回去
     *
     * @param organizationId 组织编号
     * @param runnable 逻辑
     */
    public static void execute(String organizationId, Runnable runnable) {
        String oldTenantId = OrganizationContextHolder.getOrganizationId();
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setOrganizationId(organizationId);
            OrganizationContextHolder.setIgnore(false);
            // 执行逻辑
            runnable.run();
        } finally {
            OrganizationContextHolder.setOrganizationId(oldTenantId);
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 使用指定组织，执行对应的逻辑
     *
     * 注意，如果当前是忽略组织的情况下，会被强制设置成不忽略组织
     * 当然，执行完成后，还是会恢复回去
     *
     * @param organizationId 组织编号
     * @param callable 逻辑
     */
    public static <V> V execute(String organizationId, Callable<V> callable) {
        String oldTenantId = OrganizationContextHolder.getOrganizationId();
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setOrganizationId(organizationId);
            OrganizationContextHolder.setIgnore(false);
            // 执行逻辑
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            OrganizationContextHolder.setOrganizationId(oldTenantId);
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 忽略组织，执行对应的逻辑
     *
     * @param runnable 逻辑
     */
    public static void executeIgnore(Runnable runnable) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            // 执行逻辑
            runnable.run();
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
            OrganizationContextHolder.clear();
        }
    }

    /**
     * 将多组织编号，添加到 header 中
     *
     * @param headers HTTP 请求 headers
     */
    public static void addTenantHeader(Map<String, String> headers) {
        String organizationId = OrganizationContextHolder.getOrganizationId();
        if (organizationId != null) {
            headers.put(HEADER_ORGANIZATION_ID, organizationId.toString());
        }
    }

}
