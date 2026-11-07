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

package com.privernal.apppackage.service;

import com.privernal.apppackage.dto.AppPackageDTO;
import com.privernal.dynamic.datav.domain.DynamicDatavAsset;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.service.IDynamicDatavAssetService;
import com.privernal.dynamic.datav.service.IDynamicDatavScreenService;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.service.IDynamicDashboardService;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.privernal.dynamic.report.service.IDynamicReportService;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 应用包导出（M7.5）里最不能被改坏的两件事。
 * <p>
 * 一是凭据：sourceConfig 里的密文跨环境解不开（主密钥各不相同），一旦跟着包出去，轻则新环境配不上，
 * 重则把源环境的可逆密文交给别人。二是素材的引用筛选：素材表没有 appId 锚点，整组织导出会把
 * 无关素材塞进包里，而漏掉筛选会让导入方看到「屏引用了一张不存在的图」。
 * <p>
 * 这两个方法都是私有的，反射调用而不是放宽可见性：生产代码里没有任何理由从外部调它们。
 *
 * @author saperliu
 */
@DisplayName("应用包导出测试")
class AppPackageServiceTest {

    private AppPackageService service;

    private IDynamicDatasetService datasetService;

    private IDynamicReportService reportService;

    private IDynamicDashboardService dashboardService;

    private IDynamicDatavScreenService datavScreenService;

    private IDynamicDatavAssetService datavAssetService;

    @BeforeEach
    void setUp() throws Exception {
        service = new AppPackageService();
        datasetService = mock(IDynamicDatasetService.class);
        reportService = mock(IDynamicReportService.class);
        dashboardService = mock(IDynamicDashboardService.class);
        datavScreenService = mock(IDynamicDatavScreenService.class);
        datavAssetService = mock(IDynamicDatavAssetService.class);
        setField("datasetService", datasetService);
        setField("reportService", reportService);
        setField("dashboardService", dashboardService);
        setField("datavScreenService", datavScreenService);
        setField("datavAssetService", datavAssetService);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AppPackageService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] types, Object... args) throws Exception {
        Method method = AppPackageService.class.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private JSONObject strip(JSONObject sourceConfig) throws Exception {
        return (JSONObject) invokePrivate(service, "stripCiphers", new Class<?>[] { JSONObject.class }, sourceConfig);
    }

    private void exportDatav(AppPackageDTO pkg) throws Exception {
        invokePrivate(service, "exportDatavAssets", new Class<?>[] { String.class, AppPackageDTO.class }, "app-1", pkg);
    }

    private void exportReports(AppPackageDTO pkg) throws Exception {
        invokePrivate(service, "exportReportAssets", new Class<?>[] { String.class, AppPackageDTO.class }, "app-1", pkg);
    }

    private static DynamicDatavScreen screen(String code, JSONObject schemaJson) {
        DynamicDatavScreen screen = new DynamicDatavScreen();
        screen.setScreenCode(code);
        screen.setScreenName("屏 " + code);
        screen.setLayoutMode("absolute");
        screen.setSchemaJson(schemaJson);
        return screen;
    }

    private static DynamicDatavAsset asset(String code, String fileUrl) {
        DynamicDatavAsset asset = new DynamicDatavAsset();
        asset.setAssetCode(code);
        asset.setAssetName("素材 " + code);
        asset.setCategory("背景");
        asset.setAssetType("image");
        asset.setFileUrl(fileUrl);
        asset.setFileSize(1024L);
        return asset;
    }

    @Nested
    @DisplayName("数据集凭据剥离")
    class CipherStripping {

        @Test
        @DisplayName("null 配置原样返回 null，不造空对象")
        void nullStaysNull() throws Exception {
            assertThat(strip(null)).isNull();
        }

        @Test
        @DisplayName("密文键一律不入包")
        void removesCipherKeys() throws Exception {
            JSONObject config = JSONObject.of(
                    "url", "https://api.example.com/orders",
                    "headerCiphers", JSONObject.of("authorization", "gAAAA..."),
                    "passwordCipher", "gAAAA...");
            JSONObject stripped = strip(config);
            assertThat(stripped.getString("url")).isEqualTo("https://api.example.com/orders");
            assertThat(stripped).doesNotContainKey("headerCiphers").doesNotContainKey("passwordCipher");
        }

        @Test
        @DisplayName("掩码值不跟着走，明文凭据才保留")
        void dropsMaskedValuesKeepsRealOnes() throws Exception {
            JSONObject config = JSONObject.of(
                    "headers", JSONObject.of("x-api-key", "****", "x-tenant", "acme"),
                    "password", "****");
            JSONObject stripped = strip(config);
            assertThat(stripped.getJSONObject("headers").getString("x-tenant")).isEqualTo("acme");
            assertThat(String.valueOf(stripped.getJSONObject("headers").get("x-api-key"))).isNotEqualTo("****");
            assertThat(stripped).doesNotContainKey("password");
        }

        @Test
        @DisplayName("剥离是浅拷贝，绝不改到内存里那份数据集配置")
        void doesNotMutateSource() throws Exception {
            JSONObject config = JSONObject.of("headerCiphers", JSONObject.of("a", "x"), "url", "u");
            strip(config);
            assertThat(config).containsKey("headerCiphers");
            assertThat(config.getString("url")).isEqualTo("u");
        }

        @Test
        @DisplayName("导出的数据集条目带出四列与剥离后的配置")
        void exportCarriesMultiSourceColumns() throws Exception {
            DynamicDataset dataset = new DynamicDataset();
            dataset.setDatasetCode("ds_a");
            dataset.setDatasetName("订单");
            dataset.setTableCode("t_order");
            dataset.setSourceType("http");
            dataset.setSourceConfig(JSONObject.of("url", "https://x", "headerCiphers", JSONObject.of("k", "v")));
            dataset.setRefreshConfig(JSONObject.of("ttlSec", 30));
            dataset.setComputedColumns(JSONObject.of("profit", "a-b"));
            when(datasetService.listByApp("app-1")).thenReturn(List.of(dataset));
            when(reportService.listByApp("app-1")).thenReturn(List.of());
            when(dashboardService.listByApp("app-1")).thenReturn(List.of());

            AppPackageDTO pkg = new AppPackageDTO();
            exportReports(pkg);

            assertThat(pkg.getDatasets()).hasSize(1);
            AppPackageDTO.Dataset item = pkg.getDatasets().get(0);
            assertThat(item.getSourceType()).isEqualTo("http");
            assertThat(item.getRefreshConfig().getIntValue("ttlSec")).isEqualTo(30);
            assertThat(item.getComputedColumns().getString("profit")).isEqualTo("a-b");
            assertThat(item.getSourceConfig()).doesNotContainKey("headerCiphers");
        }
    }

    @Nested
    @DisplayName("大屏与素材")
    class DatavExport {

        @Test
        @DisplayName("屏 schema 原样入包，素材只带被引用的那条")
        void assetsFilteredByReference() throws Exception {
            when(datavScreenService.listScreens("app-1")).thenReturn(List.of(
                    screen("datav_a", JSONObject.of("widgets", List.of(JSONObject.of("style",
                            JSONObject.of("image", "/statics/bg.png")))))));
            when(datavAssetService.listAssets(any(), any())).thenReturn(List.of(
                    asset("ast_used", "/statics/bg.png"),
                    asset("ast_unused", "/statics/other.png")));

            AppPackageDTO pkg = new AppPackageDTO();
            exportDatav(pkg);

            assertThat(pkg.getScreens()).hasSize(1);
            assertThat(pkg.getScreens().get(0).getSchemaJson()).contains("/statics/bg.png");
            assertThat(pkg.getDatavAssets()).extracting(AppPackageDTO.DatavAsset::getAssetCode).containsExactly("ast_used");
        }

        @Test
        @DisplayName("没有屏就不查素材表，也不留空集合以外的东西")
        void noScreensSkipsAssetScan() throws Exception {
            when(datavScreenService.listScreens("app-1")).thenReturn(List.of());

            AppPackageDTO pkg = new AppPackageDTO();
            exportDatav(pkg);

            assertThat(pkg.getScreens()).isEmpty();
            assertThat(pkg.getDatavAssets()).isEmpty();
            verify(datavAssetService, never()).listAssets(any(), any());
        }

        @Test
        @DisplayName("屏画布为空也入包，但不能把 null 拼进引用文本导致误命中")
        void nullSchemaDoesNotMatchAssets() throws Exception {
            when(datavScreenService.listScreens("app-1")).thenReturn(List.of(screen("datav_b", null)));
            when(datavAssetService.listAssets(any(), any())).thenReturn(List.of(asset("ast_x", "null")));

            AppPackageDTO pkg = new AppPackageDTO();
            exportDatav(pkg);

            assertThat(pkg.getScreens()).hasSize(1);
            assertThat(pkg.getScreens().get(0).getSchemaJson()).isNull();
            assertThat(pkg.getDatavAssets()).isEmpty();
        }
    }
}
