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

package com.privernal.dynamic.dba.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * 列表列配置（`metaData.listConfig`）解析工具。
 * <p>
 * 存在的原因：运行时列表页与高级筛选只认 {@code dynamic_form_column.show_list/show_query}
 * （读侧见 {@code dynamic-data-list.vue} 的 showList/showQuery 判断），而这两个键不在
 * {@code metaData} 里、也不是列定义建出来就自动有——建表链此前一律置 0，新表单列表恒为空。
 * 本类把配置搬进 {@code metaData}，让「只写 metaData」的生成方（AI 搭建链）也能一次成型。
 * <p>
 * 结构（数组，顺序无语义）：
 * <pre>
 * {"listConfig":[{"field":"course_name","showList":true,"showQuery":true},
 *                {"field":"hours","showList":true,"showQuery":false}]}
 * </pre>
 * {@code field} 写字段编码，比对前统一过 {@link FormTransferTable#toDbColumnName}，
 * 所以驼峰/下划线两种写法都能命中（与运行时按物理列名取值的口径一致）。
 * <p>
 * <b>未出现在 listConfig 里的列走既有默认口径</b>（业务列 showList/showQuery 双开、系统列不显示、
 * title 显示），与设计器「列表设计 · 从表单生成字段」保持一致——本类只增加显式覆盖能力，
 * 不改变人工路径的默认行为。
 */
public final class FormListConfigUtils {

    /** metaData 顶层键名 */
    public static final String LIST_CONFIG = "listConfig";

    private FormListConfigUtils() {
    }

    /** 单列的列表/筛选开关 */
    public static final class Config {
        public boolean showList;
        public boolean showQuery;

        Config(boolean showList, boolean showQuery) {
            this.showList = showList;
            this.showQuery = showQuery;
        }
    }

    /**
     * 解析 listConfig。key 为规范化后的物理列名；缺键、非数组、空数组都返回空 Map（走默认口径）。
     */
    public static Map<String, Config> resolve(Map<String, Object> metaData) {
        Map<String, Config> out = new HashMap<>();
        if (metaData == null) {
            return out;
        }
        Object raw = metaData.get(LIST_CONFIG);
        if (!(raw instanceof java.util.Collection)) {
            return out;
        }
        for (Object item : (java.util.Collection<?>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) item;
            Object field = row.get("field");
            if (field == null) {
                field = row.get("filedCode");
            }
            if (field == null || String.valueOf(field).isEmpty()) {
                continue;
            }
            String column = FormTransferTable.toDbColumnName(String.valueOf(field));
            if (column == null || column.isEmpty()) {
                continue;
            }
            out.put(column, new Config(truthy(row.get("showList")), truthy(row.get("showQuery"))));
        }
        return out;
    }

    /**
     * 收集 listConfig 里引用的字段编码（原样，未规范化），供定义期校验其是否存在。
     */
    public static java.util.List<String> referencedFields(Map<String, Object> metaData) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (metaData == null) {
            return out;
        }
        Object raw = metaData.get(LIST_CONFIG);
        if (!(raw instanceof java.util.Collection)) {
            return out;
        }
        for (Object item : (java.util.Collection<?>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Object field = ((Map<?, ?>) item).get("field");
            if (field == null) {
                field = ((Map<?, ?>) item).get("filedCode");
            }
            if (field != null && !String.valueOf(field).isEmpty()) {
                out.add(String.valueOf(field));
            }
        }
        return out;
    }

    /** 兼容 true / "true" / 1 / "1" 四种写法（平台各处 JSON 布尔落法不统一） */
    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }
}
