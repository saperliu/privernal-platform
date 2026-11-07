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
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 大屏画布 JSON 的保存校验测试
 * <p>
 * 校验只在服务层这一处：编辑器提交与 API 直调必须撞同一堵墙，
 * 所以这里直接测静态校验函数，不依赖 Spring 与数据库。
 *
 * @author saperliu
 */
@DisplayName("大屏画布保存校验测试")
class DynamicDatavScreenServiceImplTest {

    private static JSONObject schemaWithWidgets(int count) {
        JSONObject schema = new JSONObject();
        schema.put("schemaVersion", 1);
        schema.put("canvas", new JSONObject());
        JSONArray widgets = new JSONArray();
        for (int i = 0; i < count; i++) {
            JSONObject widget = new JSONObject();
            widget.put("id", "w" + i);
            widget.put("type", "charts.bar");
            widgets.add(widget);
        }
        schema.put("widgets", widgets);
        return schema;
    }

    @Test
    @DisplayName("空画布与缺关键段一律拒绝")
    void rejectsBrokenSchema() {
        assertThatThrownBy(() -> DynamicDatavScreenServiceImpl.validateSchema(null))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> DynamicDatavScreenServiceImpl.validateSchema(new JSONObject()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能为空");

        JSONObject noVersion = new JSONObject();
        noVersion.put("canvas", new JSONObject());
        assertThatThrownBy(() -> DynamicDatavScreenServiceImpl.validateSchema(noVersion))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("schemaVersion");

        JSONObject noCanvas = new JSONObject();
        noCanvas.put("schemaVersion", 1);
        assertThatThrownBy(() -> DynamicDatavScreenServiceImpl.validateSchema(noCanvas))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("canvas");
    }

    @Test
    @DisplayName("组件数超限保存时就拒绝，而不是留到预览时空白")
    void rejectsTooManyWidgets() {
        assertThatCode(() -> DynamicDatavScreenServiceImpl.validateSchema(schemaWithWidgets(120)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> DynamicDatavScreenServiceImpl.validateSchema(schemaWithWidgets(121)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("121");
    }

    @Test
    @DisplayName("巨型 JSON 被字符上限拒绝，错误里带实际体积")
    void rejectsOversizedSchema() {
        JSONObject schema = schemaWithWidgets(1);
        schema.put("filler", "x".repeat(2 * 1024 * 1024 + 10));

        assertThatThrownBy(() -> DynamicDatavScreenServiceImpl.validateSchema(schema))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("画布内容过大");
    }

    @Test
    @DisplayName("组件数按 widgets 数组计，缺失算 0")
    void countsWidgets() {
        assertThat(DynamicDatavScreenServiceImpl.countWidgets(schemaWithWidgets(3))).isEqualTo(3);
        assertThat(DynamicDatavScreenServiceImpl.countWidgets(new JSONObject())).isZero();
        assertThat(DynamicDatavScreenServiceImpl.countWidgets(null)).isZero();
    }
}
