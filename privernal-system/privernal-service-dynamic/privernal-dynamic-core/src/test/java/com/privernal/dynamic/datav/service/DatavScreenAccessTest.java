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
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 大屏按屏授权判定（M7.4）。
 * <p>
 * 这一层要防的是「模块权限等于全能」：以前 detail 与取数只校验 {@code datav:screen:query}，
 * 于是同组织里任何能进大屏模块的人，只要拿到 screenCode 就能取走任意一块屏的数据——
 * 菜单在侧栏里藏起来了，接口其实全开。这里逐条钉住「谁的哪块屏」。
 * <p>
 * 匹配语义必须与 {@code PreAuthorizeAspect.hasPermissions} 一致（{@code *:*:*} 通配 + 以用户持有的权限串为 pattern
 * 做 simpleMatch），否则「持有 datav:screen:* 的角色」在注解上过得去、在这里被拒，同一个人两种结果。
 * <p>
 * 口径是 fail-closed：不给「未归属应用的屏退回通用权限」留后门。代价由 FailClosed 那组用例钉住——
 * 没登记过菜单的屏只有超管进得去，这必须是有意识的选择而不是无人知晓的坑。
 *
 * @author saperliu
 */
@DisplayName("大屏按屏授权判定测试")
class DatavScreenAccessTest {

    private static final String CODE_A = "datav_aaa";

    private static final String CODE_B = "datav_bbb";

    @Test
    @DisplayName("按屏权限码与自动菜单落库的那条逐字一致")
    void permissionCodeMatchesAutoMenu() {
        assertThat(DatavScreenAccess.viewPermission(CODE_A)).isEqualTo("datav:screen:view:datav_aaa");
        assertThat(DatavScreenAccess.viewPermission("  " + CODE_A + "  ")).isEqualTo("datav:screen:view:datav_aaa");
    }

    @Test
    @DisplayName("拿不到权限集合就按无权限处理，不默认放行")
    void failClosedWithoutPermissions() {
        assertThat(DatavScreenAccess.canView(null, CODE_A)).isFalse();
        assertThat(DatavScreenAccess.canView(List.of(), CODE_A)).isFalse();
        assertThat(DatavScreenAccess.canView(List.of("  "), CODE_A)).isFalse();
    }

    @Test
    @DisplayName("超管全权限通配一切")
    void superRolePasses() {
        assertThat(DatavScreenAccess.canView(List.of("*:*:*"), CODE_A)).isTrue();
    }

    @Nested
    @DisplayName("只认这一块屏的授权")
    class PerScreenGrant {

        @Test
        @DisplayName("持有本屏的 view 码才放行")
        void grantedOnlyByOwnScreenCode() {
            assertThat(DatavScreenAccess.canView(List.of("datav:screen:view:" + CODE_A), CODE_A)).isTrue();
        }

        @Test
        @DisplayName("持有别的屏的 view 码不放行——这才是这次要堵的口子")
        void otherScreenCodeDoesNotLeak() {
            assertThat(DatavScreenAccess.canView(List.of("datav:screen:view:" + CODE_B), CODE_A)).isFalse();
        }

        @Test
        @DisplayName("只有模块通用查询权限时，取数与详情都要被拒")
        void generalQueryIsNotEnough() {
            assertThat(DatavScreenAccess.canView(List.of("datav:screen:query"), CODE_A)).isFalse();
            assertThat(DatavScreenAccess.canView(List.of("datav:screen:list", "datav:screen:query"), CODE_A)).isFalse();
        }

        @Test
        @DisplayName("持有 datav:screen:* 的角色按前缀通配放行，与注解口径一致")
        void prefixWildcardStillWorks() {
            assertThat(DatavScreenAccess.canView(List.of("datav:screen:*"), CODE_A)).isTrue();
        }

        @Test
        @DisplayName("写入类权限不等于读取权限")
        void writePermissionsDoNotGrantRead() {
            assertThat(DatavScreenAccess.canView(List.of("datav:screen:add", "datav:screen:edit"), CODE_A)).isFalse();
        }
    }

    @Nested
    @DisplayName("统一报错口径")
    class RequireViewable {

        @Test
        @DisplayName("屏不存在与被拒用同一句话，避免用报错差异枚举别人的屏")
        void sameMessageForMissingAndDenied() {
            assertThatThrownBy(() -> DatavScreenAccess.requireViewable(null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("大屏不存在或无权访问");

            DynamicDatavScreen screen = new DynamicDatavScreen();
            screen.setScreenCode(CODE_A);
            screen.setAppId("app-1");
            // 测试线程没有登录态，拿不到权限集合：必须走 fail-closed，而不是默认放行
            assertThatThrownBy(() -> DatavScreenAccess.requireViewable(screen))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("大屏不存在或无权访问");
        }

        @Test
        @DisplayName("未归属应用、没登记过菜单的屏同样按屏判——不靠通用权限开后门")
        void orphanScreenAlsoRequiresPerScreenGrant() {
            DynamicDatavScreen orphan = new DynamicDatavScreen();
            orphan.setScreenCode(CODE_A);
            assertThatThrownBy(() -> DatavScreenAccess.requireViewable(orphan))
                    .isInstanceOf(ServiceException.class);
            assertThat(DatavScreenAccess.canView(List.of("datav:screen:query"), CODE_A)).isFalse();
        }
    }

    @Nested
    @DisplayName("列表页可见性")
    class ListVisibility {

        private DynamicDatavScreen screen(String code, String createdBy) {
            DynamicDatavScreen screen = new DynamicDatavScreen();
            screen.setScreenCode(code);
            screen.setCreatedBy(createdBy);
            return screen;
        }

        @Test
        @DisplayName("没有这块屏的授权就不出现在列表里")
        void hidesUngrantedScreens() {
            assertThat(DatavScreenAccess.canSeeInList(List.of("datav:screen:list", "datav:screen:query"), "u-1", screen(CODE_B, "u-2")))
                    .isFalse();
        }

        @Test
        @DisplayName("授过权的屏可见，超管全可见")
        void showsGrantedAndSuperScreens() {
            assertThat(DatavScreenAccess.canSeeInList(List.of("datav:screen:view:" + CODE_A), "u-1", screen(CODE_A, "u-2")))
                    .isTrue();
            assertThat(DatavScreenAccess.canSeeInList(List.of("*:*:*"), "u-1", screen(CODE_B, "u-2")))
                    .isTrue();
        }

        @Test
        @DisplayName("自己创建的屏必须自己看得见——菜单只在归属应用时登记，且只授 SuperRole")
        void creatorAlwaysSeesOwnScreen() {
            assertThat(DatavScreenAccess.canSeeInList(List.of("datav:screen:list"), "u-1", screen(CODE_A, "u-1")))
                    .isTrue();
            // 未归属应用、也还没登记菜单的新屏同理，否则建完就从列表消失
            assertThat(DatavScreenAccess.canSeeInList(List.of(), "u-1", screen(CODE_A, "u-1")))
                    .isTrue();
        }

        @Test
        @DisplayName("取不到登录用户时不靠创建人兜底放行")
        void noCurrentUserNoCreatorShortcut() {
            assertThat(DatavScreenAccess.canSeeInList(List.of("datav:screen:list"), null, screen(CODE_A, "u-1")))
                    .isFalse();
            assertThat(DatavScreenAccess.canSeeInList(List.of("datav:screen:list"), "  ", screen(CODE_A, "  ")))
                    .isFalse();
        }

        @Test
        @DisplayName("空对象按不可见处理，不抛异常打断整张列表")
        void nullScreenIsInvisible() {
            assertThat(DatavScreenAccess.canSeeInList(List.of("*:*:*"), "u-1", null)).isFalse();
        }
    }
}
