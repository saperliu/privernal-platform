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

package com.privernal.dynamic.report.util;

import com.privernal.common.core.exception.PreAuthorizeException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dbs.util.DynamicTableAuthUtil;

/**
 * 报表/仪表盘运行时权限校验。
 * <p>
 * 仪表盘编码来自请求（路径/请求体），无法写成 @PreAuthorize 常量，按
 * {@code report:{dashboardCode}:{operation}} 组码后与登录用户权限集合比对，
 * 语义与 {@link DynamicTableAuthUtil} 一致。底层动态表的数据权限由
 * {@code DynamicTableAuthUtil} 校验（方案A：查看者须对数据集底层表有 list）。
 */
public class DynamicReportAuthUtil {

    /** 报表/仪表盘权限前缀，与仪表盘自动注册的菜单权限一致 */
    public static final String PERMISSION_PREFIX = "report:";

    private DynamicReportAuthUtil() {
    }

    /**
     * 校验当前登录用户对仪表盘是否具备任一操作权限，不满足直接抛权限异常
     *
     * @param dashboardCode 仪表盘编码
     * @param operations    允许的操作（view/list/add/edit/remove），命中任意一个即通过
     */
    public static void requirePermission(String dashboardCode, String... operations) {
        if (StringUtils.isEmpty(dashboardCode) || operations == null || operations.length == 0) {
            throw new PreAuthorizeException();
        }
        for (String operation : operations) {
            if (DynamicTableAuthUtil.hasAnyPermission(PERMISSION_PREFIX + dashboardCode + ":" + operation)) {
                return;
            }
        }
        throw new PreAuthorizeException();
    }

    /**
     * 当前登录用户对仪表盘是否具备任一操作权限（不抛异常，供组合鉴权使用）
     */
    public static boolean hasPermission(String dashboardCode, String... operations) {
        if (StringUtils.isEmpty(dashboardCode) || operations == null || operations.length == 0) {
            return false;
        }
        for (String operation : operations) {
            if (DynamicTableAuthUtil.hasAnyPermission(PERMISSION_PREFIX + dashboardCode + ":" + operation)) {
                return true;
            }
        }
        return false;
    }
}
