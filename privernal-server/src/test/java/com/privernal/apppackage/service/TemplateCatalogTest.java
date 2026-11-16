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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 官方模板目录（R2-14 ③）：发布里带出的每一套模板都必须能加载、能解析、checksum 完整——
 * 这条测试是发版质量闸：谁往 apptemplates/ 塞了坏包，CI 当场红。
 *
 * @author saperliu
 */
@DisplayName("官方模板目录测试")
class TemplateCatalogTest {

    private final TemplateCatalog catalog = new TemplateCatalog();

    @Test
    @DisplayName("清单里登记的每套模板都能加载且包结构合法（发版质量闸）")
    void everyRegisteredTemplateLoads() {
        for (TemplateCatalog.TemplateSummary summary : catalog.list()) {
            AppPackageDTO pkg = catalog.load(summary.code());
            assertThat(pkg.getApp().getCode())
                    .as("模板 %s 的应用编码", summary.code())
                    .isNotBlank();
            assertThat(pkg.getTemplate())
                    .as("模板 %s 必须携带模板元数据", summary.code())
                    .isNotNull();
            assertThat(pkg.getTemplate().getCode()).isEqualTo(summary.code());
            assertThat(pkg.getChecksum())
                    .as("模板 %s 必须带 checksum（发版产物缺失校验和=构建错误）", summary.code())
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("未登记的模板编码一律拒绝，不提供任意 classpath 读取")
    void unregisteredCodeIsRejected() {
        assertThatThrownBy(() -> catalog.load("../../application.yaml"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.load("no-such-template"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.load(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("manifest 统计数与包体一致（防清单与包体漂移）")
    void manifestCountsMatchPackageBody() {
        for (TemplateCatalog.TemplateSummary summary : catalog.list()) {
            AppPackageDTO pkg = catalog.load(summary.code());
            assertThat(pkg.getForms().size())
                    .as("模板 %s 表单数：manifest=%s 包体=%s", summary.code(), summary.formCount(), pkg.getForms().size())
                    .isEqualTo(summary.formCount());
            assertThat(pkg.getFlows().size())
                    .as("模板 %s 流程数：manifest=%s 包体=%s", summary.code(), summary.flowCount(), pkg.getFlows().size())
                    .isEqualTo(summary.flowCount());
            assertThat(pkg.getSpecVersion()).isEqualTo(summary.specVersion());
        }
    }
}
