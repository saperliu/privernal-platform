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

import com.privernal.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 数据标题（公共列 title）生成工具。设计器「表单属性 - 数据标题」多选本表单字段后，
 * 配置落在 schemas[0].props.titleFields（字段编码数组）；保存数据时按所选顺序取值拼接：
 * 选项类字段（下拉/单选/多选）翻译成选项文案，其余取原值，多个值之间以空格分隔。
 * </p>
 *
 * @author saperliu
 * @date 2026-10-01
 */
public class FormDataTitleUtils {

    /**
     * 表单属性中「数据标题」字段列表的键
     */
    public static final String TITLE_FIELDS = "titleFields";

    /**
     * title 列为 varchar(255)，超长截断
     */
    private static final int MAX_LENGTH = 255;

    private FormDataTitleUtils() {
    }

    /**
     * 按表单配置拼接数据标题
     *
     * @param metaData 表单元数据（dynamic_form.meta_data）
     * @param data     待保存的数据，键为字段编码
     * @return 拼接后的标题；未配置数据标题字段或一个值都取不到时返回 null，调用方据此保留原有标题
     */
    public static String buildTitle(JSONObject metaData, Map<String, Object> data) {
        List<String> fields = resolveTitleFields(metaData);
        if (fields.isEmpty() || data == null || data.isEmpty()) {
            return null;
        }
        Map<String, JSONArray> optionsByField = collectFieldOptions(metaData);
        List<String> parts = new ArrayList<>();
        for (String field : fields) {
            String text = toDisplayText(pickValue(data, field), optionsByField.get(normalize(field)));
            if (StringUtils.isNotEmpty(text)) {
                parts.add(text);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String title = String.join(" ", parts);
        return title.length() > MAX_LENGTH ? title.substring(0, MAX_LENGTH) : title;
    }

    /**
     * 读取表单配置的数据标题字段编码（数组或逗号分隔字符串，兼容旧版 componentProps）。
     * 未配置时默认取表单的第一个输入字段（按设计器摆放顺序，跳过子表行内字段）。
     */
    public static List<String> resolveTitleFields(JSONObject metaData) {
        List<String> configured = configuredTitleFields(metaData);
        return configured.isEmpty() ? defaultTitleFields(metaData) : configured;
    }

    /**
     * 只取「显式配置」的数据标题字段，未配置返回空列表。
     * 定义期校验（skill §3.2）要用这个口径：resolveTitleFields 的兜底值会把
     * 「配了但不存在」和「没配」混成一回事，前者必须报错而不是静默回落。
     */
    public static List<String> configuredTitleFields(JSONObject metaData) {
        JSONObject props = rootProps(metaData);
        if (props != null) {
            Object configured = props.get(TITLE_FIELDS);
            List<String> fields = new ArrayList<>();
            if (configured instanceof Collection) {
                for (Object item : (Collection<?>) configured) {
                    addIfPresent(fields, item);
                }
            } else if (configured instanceof Object[]) {
                for (Object item : (Object[]) configured) {
                    addIfPresent(fields, item);
                }
            } else if (configured instanceof String) {
                String text = ((String) configured).trim();
                if (text.startsWith("[") && text.endsWith("]")) {
                    // 多选值被序列化成 JSON 文本落库的场景
                    JSONArray array = safeParseArray(text);
                    if (array != null) {
                        for (Object item : array) {
                            addIfPresent(fields, item);
                        }
                    }
                } else {
                    for (String item : text.split(",")) {
                        addIfPresent(fields, item);
                    }
                }
            }
            if (!fields.isEmpty()) {
                return fields;
            }
        }
        return new ArrayList<>();
    }

    /**
     * 未配置数据标题时的兜底：取表单第一个输入字段的编码（子表行内字段不做标题来源）
     */
    private static List<String> defaultTitleFields(JSONObject metaData) {
        List<String> out = new ArrayList<>();
        JSONArray schemas = metaData == null ? null : metaData.getJSONArray("schemas");
        if (schemas != null) {
            for (Object schema : schemas) {
                if (findFirstInputField(schema, out)) {
                    break;
                }
            }
        }
        return out;
    }

    /**
     * 深度优先找第一个输入字段，找到（返回 true）即停止
     */
    private static boolean findFirstInputField(Object node, List<String> out) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            // 子表行内字段值不在主表数据顶层，不能作为标题来源
            if ("sub-table".equals(obj.getString("type"))) {
                return false;
            }
            if (Boolean.TRUE.equals(obj.getBoolean("input")) && obj.getString("field") != null) {
                out.add(String.valueOf(obj.get("field")));
                return true;
            }
            for (Object value : obj.values()) {
                if (findFirstInputField(value, out)) {
                    return true;
                }
            }
        } else if (node instanceof Collection) {
            for (Object item : (Collection<?>) node) {
                if (findFirstInputField(item, out)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 递归收集输入型字段的选项配置（field -&gt; props.options），用于把存储值翻译成显示文案。
     * 键统一按数据库列名规则归一，避免设计器原始编码与规范化后的列名不一致。
     */
    public static Map<String, JSONArray> collectFieldOptions(JSONObject metaData) {
        Map<String, JSONArray> optionsByField = new HashMap<>();
        JSONArray schemas = metaData == null ? null : metaData.getJSONArray("schemas");
        if (schemas == null) {
            return optionsByField;
        }
        for (Object schema : schemas) {
            collectOptions(schema, optionsByField);
        }
        return optionsByField;
    }

    private static void collectOptions(Object node, Map<String, JSONArray> optionsByField) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Object field = obj.get("field");
            if (field != null && Boolean.TRUE.equals(obj.getBoolean("input"))) {
                JSONObject props = obj.getJSONObject("props");
                if (props == null) {
                    props = obj.getJSONObject("componentProps");
                }
                JSONArray options = props == null ? null : toArray(props.get("options"));
                if (options != null && !options.isEmpty()) {
                    optionsByField.put(normalize(String.valueOf(field)), options);
                }
            }
            for (Object value : obj.values()) {
                collectOptions(value, optionsByField);
            }
        } else if (node instanceof Collection) {
            for (Object item : (Collection<?>) node) {
                collectOptions(item, optionsByField);
            }
        }
    }

    /**
     * 取字段值：先按配置原样取，再按列名规则取（数据键与建表列名同源）
     */
    private static Object pickValue(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (value == null) {
            value = data.get(normalize(field));
        }
        return value;
    }

    /**
     * 值转显示文案：选项类字段按 options 的 value 匹配 label，多值逐个翻译后以逗号连接
     */
    private static String toDisplayText(Object value, JSONArray options) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection) {
            return joinItems((Collection<?>) value, options);
        }
        if (value instanceof Object[]) {
            return joinItems(java.util.Arrays.asList((Object[]) value), options);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            JSONArray array = safeParseArray(text);
            if (array != null && !array.isEmpty()) {
                List<Object> items = new ArrayList<>(array);
                return joinItems(items, options);
            }
        }
        return labelOf(text, options);
    }

    private static String joinItems(Collection<?> items, JSONArray options) {
        List<String> texts = new ArrayList<>();
        for (Object item : items) {
            String text = labelOf(item == null ? null : String.valueOf(item).trim(), options);
            if (StringUtils.isNotEmpty(text)) {
                texts.add(text);
            }
        }
        return texts.isEmpty() ? null : String.join(",", texts);
    }

    /**
     * 选项值翻译，未命中选项（含无选项的普通字段）时原样返回
     */
    private static String labelOf(String value, JSONArray options) {
        if (StringUtils.isEmpty(value) || options == null) {
            return value;
        }
        for (Object item : options) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject option = (JSONObject) item;
            Object optionValue = option.get("value");
            if (optionValue != null && value.equals(String.valueOf(optionValue).trim())) {
                String label = option.getString("label");
                return StringUtils.isNotEmpty(label) ? label : value;
            }
        }
        return value;
    }

    private static JSONObject rootProps(JSONObject metaData) {
        JSONArray schemas = metaData == null ? null : metaData.getJSONArray("schemas");
        if (schemas == null || schemas.isEmpty()) {
            return null;
        }
        JSONObject root = schemas.getJSONObject(0);
        if (root == null) {
            return null;
        }
        JSONObject props = root.getJSONObject("props");
        // 旧版 epic-designer 的属性键
        return props != null ? props : root.getJSONObject("componentProps");
    }

    private static JSONArray toArray(Object value) {
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }
        if (value instanceof Collection) {
            JSONArray array = new JSONArray();
            array.addAll((Collection<?>) value);
            return array;
        }
        return null;
    }

    private static JSONArray safeParseArray(String text) {
        try {
            return JSON.parseArray(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static void addIfPresent(List<String> fields, Object item) {
        if (item == null) {
            return;
        }
        String field = String.valueOf(item).trim();
        if (StringUtils.isNotEmpty(field) && !fields.contains(field)) {
            fields.add(field);
        }
    }

    /**
     * 与建表、数据写入同一套列名转换，保证 metaData 字段、列名、数据键三者可对上
     */
    private static String normalize(String field) {
        return FormTransferTable.toDbColumnName(field);
    }
}
