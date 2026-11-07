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

package com.privernal.dynamic.datav.service.impl;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.dynamic.datav.domain.DynamicDatavAsset;
import com.privernal.dynamic.datav.service.IDynamicDatavAssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 大屏素材登记校验与引用保护测试
 * <p>
 * 引用保护靠「URL LIKE 扫 schema_json」，LIKE 转义因此是唯一容易悄悄写错的地方
 * （URL 里带 % _ \ 时，转义错了要么删不掉、要么误判成没引用直接放删），这里逐字符钉住。
 *
 * @author saperliu
 */
@DisplayName("大屏素材校验与引用保护测试")
class DynamicDatavAssetServiceImplTest {

    @Nested
    @DisplayName("登记校验")
    class Register {

        private DynamicDatavAsset asset(String url, String type) {
            DynamicDatavAsset asset = new DynamicDatavAsset();
            asset.setFileUrl(url);
            asset.setAssetType(type);
            return asset;
        }

        @Test
        @DisplayName("本地静态路径与 http(s) 绝对地址可登记")
        void acceptsRelativeAndHttpUrls() {
            assertThatCode(() -> DynamicDatavAssetServiceImpl.requireValid(asset("/statics/datav/bg.png", "image")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> DynamicDatavAssetServiceImpl.requireValid(asset("https://oss.example.com/a.mp4", "video")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> DynamicDatavAssetServiceImpl.requireValid(asset("http://intranet-host/x.png", "image")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("javascript:/data: 与其他协议一律拒：schema 会原样进 iframe/img src")
        void rejectsNonHttpProtocols() {
            for (String url : new String[]{"javascript:alert(1)", "data:text/html;base64,xx", "ftp://a/b.png", "file:///etc/passwd", "", null}) {
                assertThatThrownBy(() -> DynamicDatavAssetServiceImpl.requireValid(asset(url, "image")))
                        .as(String.valueOf(url))
                        .isInstanceOf(ServiceException.class)
                        .hasMessageContaining("素材地址");
            }
        }

        @Test
        @DisplayName("类型白名单 image/video，其他值拒绝")
        void rejectsUnknownType() {
            assertThatThrownBy(() -> DynamicDatavAssetServiceImpl.requireValid(asset("/statics/a.png", "html")))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("image / video");
        }

        @Test
        @DisplayName("规范化：缺省类型 image、分类通用、名称回落原始文件名并截断")
        void normalizesDefaultsAndTruncates() {
            DynamicDatavAsset asset = asset("/statics/a.png", "");
            asset.setAssetName(IntStream.range(0, 300).mapToObj(i -> "x").collect(Collectors.joining()));
            DynamicDatavAssetServiceImpl.normalize(asset);
            assertThat(asset.getAssetType()).isEqualTo("image");
            assertThat(asset.getCategory()).isEqualTo("通用");
            assertThat(asset.getAssetName()).hasSize(200);
            assertThat(asset.getFileSize()).isZero();

            DynamicDatavAsset named = asset("/statics/a.png", "image");
            named.setOriginalName("车间监控大屏背景.png");
            DynamicDatavAssetServiceImpl.normalize(named);
            assertThat(named.getAssetName()).isEqualTo("车间监控大屏背景.png");
        }
    }

    @Nested
    @DisplayName("LIKE 转义（引用保护的地基）")
    class LikeEscape {

        @Test
        @DisplayName("URL 自带的 % _ \\ 全部转义，否则会被当成通配符")
        void escapesAllWildcards() {
            assertThat(IDynamicDatavAssetService.escapeLike("/statics/a%b.png")).isEqualTo("/statics/a\\%b.png");
            assertThat(IDynamicDatavAssetService.escapeLike("/statics/a_b.png")).isEqualTo("/statics/a\\_b.png");
            assertThat(IDynamicDatavAssetService.escapeLike("C:\\datav\\bg.png")).isEqualTo("C:\\\\datav\\\\bg.png");
        }

        @Test
        @DisplayName("普通 URL 原样通过，null 归空串")
        void passesPlainValueThrough() {
            assertThat(IDynamicDatavAssetService.escapeLike("/statics/datav/bg.png")).isEqualTo("/statics/datav/bg.png");
            assertThat(IDynamicDatavAssetService.escapeLike(null)).isEmpty();
        }

        @Test
        @DisplayName("混合转义顺序正确：先反斜杠再通配符，避免二次转义")
        void escapesBackslashFirst() {
            assertThat(IDynamicDatavAssetService.escapeLike("a\\%b")).isEqualTo("a\\\\\\%b");
        }
    }

    @Nested
    @DisplayName("引用保护提示")
    class ReferenceMessage {

        @Test
        @DisplayName("三块以内逐名列出")
        void listsUpToThreeNames() {
            List<String> names = Arrays.asList("运营大屏", "车间大屏", "园区大屏");
            assertThat(DynamicDatavAssetServiceImpl.referenceDeniedMessage(names))
                    .contains("运营大屏").contains("车间大屏").contains("园区大屏")
                    .doesNotContain("等");
        }

        @Test
        @DisplayName("超过三块只列前三并给出总数，错误文案不能变成大屏清单")
        void summarizesBeyondThree() {
            List<String> names = Arrays.asList("A屏", "B屏", "C屏", "D屏", "E屏");
            String message = DynamicDatavAssetServiceImpl.referenceDeniedMessage(names);
            assertThat(message).contains("A屏").contains("C屏").doesNotContain("D屏");
            assertThat(message).contains("等 5 块大屏");
        }

        @Test
        @DisplayName("空引用列表也给出可读文案（防御：调用方漏判时仍能自洽）")
        void handlesEmptyList() {
            assertThat(DynamicDatavAssetServiceImpl.referenceDeniedMessage(Collections.emptyList()))
                    .contains("引用");
        }
    }
}
