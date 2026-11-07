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

package com.privernal.dynamic.advanced.engine;

import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 表单元数据的字段清单解析（原 PrintTemplateService 静态语义，打印模板 M0 下线时迁出）。
 * <p>
 * 现存调用方：免登外链填报页的元数据裁剪（PublicFormController §5.3-6）。
 */
public final class FormSchemaFields {

    private FormSchemaFields() {
    }

    /** 表单的输入字段清单（metaData schemas 顶层 input 组件，不含子表） */
    public static List<JSONObject> inputChildren(DynamicForm form) {
        List<JSONObject> result = new ArrayList<>();
        JSONObject metaData = form.getMetaData();
        if (metaData == null) {
            return result;
        }
        JSONArray schemas = metaData.getJSONArray("schemas");
        if (schemas == null || schemas.isEmpty()) {
            return result;
        }
        JSONArray children = schemas.getJSONObject(0).getJSONArray("children");
        if (children == null) {
            return result;
        }
        for (int i = 0; i < children.size(); i++) {
            JSONObject child = children.getJSONObject(i);
            if (child != null && Boolean.TRUE.equals(child.getBoolean("input"))
                    && !"sub-table".equals(child.getString("type"))) {
                result.add(child);
            }
        }
        return result;
    }

    /** 免登表单元数据裁剪（§5.3-6）：只回传可填写字段的编码/名称/类型/必填/选项，不吐 metaData 全量 */
    public static JSONArray trimFillableFields(DynamicForm form) {
        JSONArray result = new JSONArray();
        for (JSONObject child : inputChildren(form)) {
            JSONObject field = new JSONObject();
            field.put("field", child.getString("field"));
            field.put("label", child.getString("label"));
            field.put("type", child.getString("type"));
            JSONObject props = child.getJSONObject("props");
            if (props != null) {
                field.put("required", props.getBoolean("required"));
                if (props.containsKey("options")) {
                    // 选项类字段只回传选项值/文案对，不回传组件其余配置
                    field.put("options", props.get("options"));
                }
            }
            result.add(field);
        }
        return result;
    }
}
