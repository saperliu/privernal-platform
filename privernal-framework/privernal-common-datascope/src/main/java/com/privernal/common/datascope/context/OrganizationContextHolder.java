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

package com.privernal.common.datascope.context;

import com.privernal.common.datascope.aspect.DataScopeHelper;
import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 多组织上下文 Holder
 *
 * @author saperliu
 */
public class OrganizationContextHolder {

    /**
     * 当前组织编号
     */
    private static final ThreadLocal<String> ORGANIZATION_ID = new TransmittableThreadLocal<>();

    /**
     * 获得组织编号。
     *
     * @return 组织编号
     */
    public static String getOrganizationId() {
        return ORGANIZATION_ID.get();
    }

    public static void setOrganizationId(String organizationId) {
        ORGANIZATION_ID.set(organizationId);
    }

    /**
     * 设置忽略组织
     *
     * @param ignore 是否忽略
     */
    public static void setIgnore(Boolean ignore) {
        DataScopeHelper.setIgnoreOrganization(ignore);
    }

    /**
     * 设置忽略部门
     *
     * @param ignore 是否忽略
     */
    public static void setIgnoreDepartment(Boolean ignore) {
        DataScopeHelper.setIgnoreDataPermission(ignore);
    }

    /**
     * 设置忽略组织 和 部门
     *
     * @param ignore 是否忽略
     */
    public static void setIgnoreOrgAndDepartment(Boolean ignore) {
        DataScopeHelper.setIgnoreDataPermission(ignore);
        DataScopeHelper.setIgnoreOrganization(ignore);
    }

    /**
     * 设置指定组织且忽略部门
     *
     * @param organizationId 组织ID
     * @param ignore         是否忽略
     */
    public static void setOrgAndIgnoreDepartment(String organizationId, Boolean ignore) {
        DataScopeHelper.setIgnoreDataPermission(ignore);
        ORGANIZATION_ID.set(organizationId);
    }


    /**
     * 当前是否忽略组织
     *
     * @return 是否忽略
     */
    public static boolean isIgnore() {
        return Boolean.TRUE.equals(DataScopeHelper.getIgnoreOrganization());
    }

    public static void clear() {
        ORGANIZATION_ID.remove();
        DataScopeHelper.clearIgnoreStrategy();
    }

}
