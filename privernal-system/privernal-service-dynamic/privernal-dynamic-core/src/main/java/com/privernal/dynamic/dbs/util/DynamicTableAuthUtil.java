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

package com.privernal.dynamic.dbs.util;

import com.privernal.common.core.exception.PreAuthorizeException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.system.dto.UserInfo;
import org.springframework.util.PatternMatchUtils;

import java.util.Set;

/**
 * 动态表运行时数据的按钮权限校验。
 * <p>
 * 表编码来自请求（路径变量或 body），无法写成 {@code @PreAuthorize(hasPermi = "dynamic:xxx:list")} 的常量，
 * 故在此按 {@code dynamic:{tableCode}:{operation}} 组码后与登录用户权限集合比对；
 * 通配与 {@code *:*:*} 的判定语义与 PreAuthorizeAspect 保持一致。
 */
public class DynamicTableAuthUtil {

    /** 所有权限标识 */
    private static final String ALL_PERMISSION = "*:*:*";

    /** 动态表按钮权限前缀，与 DynamicFormServiceImpl 生成的菜单权限一致 */
    private static final String PERMISSION_PREFIX = "dynamic:";

    private DynamicTableAuthUtil() {
    }

    /**
     * 校验当前登录用户对该表是否具备任一操作权限，不满足直接抛权限异常
     *
     * @param tableCode  动态表编码
     * @param operations 允许的操作（list/add/edit/remove/import/export），命中任意一个即通过
     */
    public static void requirePermission(String tableCode, String... operations) {
        if (!hasPermission(tableCode, operations)) {
            throw new PreAuthorizeException();
        }
    }

    /**
     * 当前登录用户对该表是否具备任一操作权限（不抛异常，供组合鉴权使用）
     */
    public static boolean hasPermission(String tableCode, String... operations) {
        if (StringUtils.isEmpty(tableCode) || operations == null || operations.length == 0) {
            return false;
        }
        for (String operation : operations) {
            if (hasAnyPermission(PERMISSION_PREFIX + tableCode + ":" + operation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前登录用户是否持有任一完整权限码（超管 *:*:* 与通配符按 PreAuthorizeAspect 语义展开）
     */
    public static boolean hasAnyPermission(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        UserInfo userInfo = SecurityUtils.getUserInfo();
        Set<String> owned = userInfo == null ? null : userInfo.getPermissions();
        if (owned == null || owned.isEmpty()) {
            return false;
        }
        for (String required : permissions) {
            if (StringUtils.isEmpty(required)) {
                continue;
            }
            boolean matched = owned.stream().filter(StringUtils::hasText)
                    .anyMatch(permitted -> ALL_PERMISSION.equals(permitted)
                            || PatternMatchUtils.simpleMatch(permitted, required));
            if (matched) {
                return true;
            }
        }
        return false;
    }
}
