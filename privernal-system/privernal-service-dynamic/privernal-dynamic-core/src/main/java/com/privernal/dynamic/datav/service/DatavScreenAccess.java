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

package com.privernal.dynamic.datav.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.system.dto.UserInfo;
import org.springframework.util.PatternMatchUtils;

import java.util.Collection;

/**
 * 大屏的「谁能看哪块屏」判定（M7.4）。
 *
 * 口径来自大屏自动登记的那条应用菜单：menu_code = screenCode，它的 permission 是
 * {@code datav:screen:view:<screenCode>}，管理员在角色授权里勾哪几条菜单，就等于授了哪几块屏。
 * 之前 detail 与取数只校验通用的 {@code datav:screen:query}，结果是同组织里任何能进大屏模块的人
 * 都能取任意一块屏的数据——菜单只是入口看不见，接口其实全开，这跟没授权一样。
 *
 * 匹配规则刻意与 {@code PreAuthorizeAspect.hasPermissions} 保持一致（{@code *:*:*} 通配 +
 * 以用户持有的权限串作为 pattern 的 simpleMatch），否则「持有 datav:screen:* 的角色」在注解上
 * 过得去、在这里被拒，同一个人两种结果比不校验更糟。
 *
 * @author saperliu
 */
public final class DatavScreenAccess {

    /** 超管全权限 */
    private static final String ALL_PERMISSION = "*:*:*";

    private static final String VIEW_PREFIX = "datav:screen:view:";

    private DatavScreenAccess() {
    }

    /** 某块屏的查看权限码，与自动菜单落库的那条逐字一致 */
    public static String viewPermission(String screenCode) {
        return VIEW_PREFIX + StringUtils.trimToEmpty(screenCode);
    }

    /**
     * @param permissions 登录用户的权限集合，非 web 线程或未登录时为 null
     * @param screenCode  大屏编码
     * @return 允许查看
     */
    public static boolean canView(Collection<String> permissions, String screenCode) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        String required = viewPermission(screenCode);
        return permissions.stream()
                .filter(StringUtils::isNotEmpty)
                .anyMatch(held -> ALL_PERMISSION.equals(held) || PatternMatchUtils.simpleMatch(held, required));
    }

    /**
     * 列表页可见性 = 按屏查看权 ∪ 自己创建的屏。
     * <p>
     * 「∪ 自己创建的」不是便利，而是不加就会坏事：屏归属应用时才会自动登记菜单，而登记只授给创建组织与
     * SuperRole；非超管的设计者第一次保存后，若列表只认按屏权限，他会看见自己刚建的屏凭空消失。
     * 自己读自己创建的屏也不构成越权——他能建就说明持有模块级 add/edit。
     */
    public static boolean canSeeInList(Collection<String> permissions, String currentUserId, DynamicDatavScreen screen) {
        if (screen == null) {
            return false;
        }
        if (canView(permissions, screen.getScreenCode())) {
            return true;
        }
        return StringUtils.isNotEmpty(currentUserId) && StringUtils.equals(currentUserId, screen.getCreatedBy());
    }

    /**
     * 不可见就抛异常，错误文案与「屏不存在」同一句。
     * <p>
     * 能区分「这块屏存在，只是你看不了」和「没这块屏」，就等于允许拿别人的 screenCode 逐条枚举
     * 组织里的大屏——取数接口原本就是这么防的，这里保持一致。
     * <p>
     * 这里不留「未归属应用的屏退回通用查询权限」的后门（刻意 fail-closed）：那样的后门等于
     * 「有模块权限就能读任意一块没归应用的屏」，正是本次要堵的东西本身。代价是**没登记过菜单的屏
     * 只有超管能打开**——要给别人看，先把它归到某个应用下（保存时会自动登记菜单），或在菜单管理里
     * 手工补一条 menu_code = screenCode、permission = {@code datav:screen:view:<screenCode>} 的菜单再授权。
     */
    public static void requireViewable(DynamicDatavScreen screen) {
        if (screen == null) {
            throw new ServiceException("大屏不存在或无权访问");
        }
        if (!canView(loginPermissions(), screen.getScreenCode())) {
            throw new ServiceException("大屏不存在或无权访问");
        }
    }

    /**
     * 登录态里的权限集合；非 web 线程或未登录时按「没有权限」返回 null，不默认放行。
     * <p>
     * 权限集合是登录时组装进 token 会话的：改完屏菜单的 permission 必须让人重新登录才会生效，
     * 这一步拿不到新权限是预期行为，不是 bug。
     */
    public static Collection<String> loginPermissions() {
        try {
            UserInfo userInfo = SecurityUtils.getUserInfo();
            return userInfo == null ? null : userInfo.getPermissions();
        } catch (Exception e) {
            return null;
        }
    }
}
