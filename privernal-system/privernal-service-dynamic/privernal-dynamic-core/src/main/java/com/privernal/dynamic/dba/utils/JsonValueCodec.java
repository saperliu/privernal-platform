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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 多值字段的统一值协议编解码（S1）。
 * <p>
 * 目标形态：{@code {"v":["NBTB"],"src":{"NBTB":"内部提报"}}}
 * <ul>
 *   <li>{@code v} 恒为数组（单选即长度 1），使抽取路径唯一（{@code $.v[0]}），
 *       不出现"单选写 selected、多选写 selectList"两套口径；</li>
 *   <li>{@code src} 是<b>可丢弃的 value→label 缓存</b>，不是真源：字典改名后存量行留旧标签，
 *       读取顺序固定为「现场字典/关联表翻译 → src 降级 → 原值」。</li>
 * </ul>
 * 必须长期兼容的历史形态（存量数据不能改，解析端要一直容错）：
 * {@code ["a","b"]} 裸 JSON 数组串、{@code a,b} 逗号串、{@code abc} 裸值、{@code {"a":1}} 对象快照。
 * <p>
 * 设计约束：<b>读取端先宽容、写入端再切换</b>。因此本类的解析方法无条件支持全部形态，
 * 而 {@link #wrap(Object)} 只在开关打开时参与写入链路。
 * </p>
 *
 * @author saperliu
 * @version 1.0.0
 * @see ShadowColumnSupport
 */
public final class JsonValueCodec {

    /**
     * 影子（首值）列后缀：业务列 {@code x} 的影子列为 {@code x_v0}。
     * 只用字母数字下划线且不以分隔符开头，保证能通过
     * {@code FormTransferTable} 与报表侧的标识符白名单 {@code ^[A-Za-z][A-Za-z0-9_]*$}。
     */
    public static final String SHADOW_SUFFIX = "_v0";

    /**
     * 信封判定的键
     */
    private static final String KEY_VALUES = "v";
    private static final String KEY_SOURCE = "src";

    /**
     * 多值展示分隔符（导出/打印/站内信统一用它，避免整段 JSON 吐给用户）
     */
    private static final String DISPLAY_SEPARATOR = "、";

    private JsonValueCodec() {
    }

    /**
     * 判断给定字符串是否已是统一信封形态。
     *
     * @param stored 列原始值
     * @return 是否形如 {@code {"v":[...]}}
     */
    public static boolean isEnvelope(String stored) {
        if (stored == null) {
            return false;
        }
        String trimmed = stored.trim();
        return trimmed.startsWith("{") && trimmed.contains("\"" + KEY_VALUES + "\"");
    }

    /**
     * 把写入值规范化为统一信封字符串。
     * <p>
     * 只处理"多值/标量集合"这一类：元素全为标量的集合会被包成 {@code {"v":[...]}}；
     * 含对象的集合（子表行数组）与普通 Map 保持原有 JSON 形态输出，
     * 因为子表快照的既有读取方（{@code parseRows} 等）按数组解析，改成信封会直接打断回显。
     * </p>
     *
     * @param raw 前端提交的原始值
     * @return 需要落库的字符串值；非集合值原样返回（由调用方决定如何序列化）
     */
    public static Object wrap(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Collection<?> collection) {
            if (isScalarCollection(collection)) {
                JSONObject envelope = new JSONObject();
                envelope.put(KEY_VALUES, new JSONArray(new ArrayList<>(collection)));
                return envelope.toJSONString();
            }
            // 子表行等对象数组：保持数组形态，兼容既有读取链路
            return JSON.toJSONString(collection);
        }
        if (raw instanceof Map) {
            return JSON.toJSONString(raw);
        }
        return raw;
    }

    /**
     * 解析任意历史形态的存储值为其成员列表。
     *
     * @param stored 列原始值
     * @return 成员列表（保持原顺序）；单值返回长度 1 的列表；空值返回空列表
     */
    public static List<String> values(String stored) {
        if (stored == null || stored.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String trimmed = stored.trim();
        if (!JSON.isValid(trimmed)) {
            // 逗号串与裸值：按逗号拆分，兼容历史多选写法
            return splitLegacy(trimmed);
        }
        try {
            if (trimmed.startsWith("{")) {
                JSONObject obj = JSON.parseObject(trimmed);
                JSONArray arr = obj.getJSONArray(KEY_VALUES);
                if (arr != null) {
                    return toStringList(arr);
                }
                // 非信封的对象快照：整体视为一个值
                return Collections.singletonList(trimmed);
            }
            if (trimmed.startsWith("[")) {
                return toStringList(JSON.parseArray(trimmed));
            }
        } catch (Exception e) {
            // 非法/意外结构不阻断业务，退化为原值
            return Collections.singletonList(trimmed);
        }
        return Collections.singletonList(trimmed);
    }

    /**
     * 取首值，即影子列的语义等价实现（用于内存比较、查重、唯一性判断）。
     *
     * @param stored 列原始值
     * @return 首值；无值时返回 null
     */
    public static String firstValue(String stored) {
        List<String> values = values(stored);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * 生成人类可读展示串：多值用顿号连接，并可按 labelMap 回译选项值。
     * 用于替换导出、打印、站内信三处此前的裸 {@code String.valueOf(value)}，
     * 避免把 {@code {"v":["a"],"src":{...}}} 整段吐给用户。
     *
     * @param stored    列原始值
     * @param labelMap  value→label 映射，可为 null（来自表单 schema 的 props.options 或字典）
     * @return 展示串
     */
    public static String formatForDisplay(String stored, Map<String, String> labelMap) {
        if (stored == null || stored.trim().isEmpty()) {
            return "";
        }
        List<String> values = values(stored);
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(DISPLAY_SEPARATOR);
            }
            String value = values.get(i);
            String label = labelMap == null ? null : labelMap.get(value);
            sb.append(label != null && !label.isEmpty() ? label : value);
        }
        return sb.toString();
    }

    /**
     * 从信封里取 value→label 缓存，供"现场翻译不可用时"的降级展示。
     *
     * @param stored 列原始值
     * @return 映射；不存在时返回空 Map
     */
    public static Map<String, String> cachedLabels(String stored) {
        if (stored == null || !isEnvelope(stored)) {
            return Collections.emptyMap();
        }
        try {
            JSONObject src = JSON.parseObject(stored.trim()).getJSONObject(KEY_SOURCE);
            if (src == null) {
                return Collections.emptyMap();
            }
            Map<String, String> labels = new java.util.LinkedHashMap<>();
            for (String key : src.keySet()) {
                labels.put(key, src.getString(key));
            }
            return labels;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * 业务列对应的影子列名。
     *
     * @param column 业务列名（已是数据库列名形态）
     * @return 影子列名；超长时返回 null（由调用方决定跳过，不能静默截断成另一个列名）
     */
    public static String shadowColumn(String column) {
        if (column == null || column.isEmpty()) {
            return null;
        }
        // MySQL 列名上限 64：截断会让影子列指向不存在的语义，直接放弃更安全
        if ((column + SHADOW_SUFFIX).length() > 64) {
            return null;
        }
        return column + SHADOW_SUFFIX;
    }

    /**
     * 是否为影子列（写入链路据此剔除用户提交的影子列键：生成列不可赋值）。
     *
     * @param column 列名
     * @return 是否影子列
     */
    public static boolean isShadowColumn(String column) {
        return column != null && column.length() > SHADOW_SUFFIX.length()
                && column.endsWith(SHADOW_SUFFIX);
    }

    private static boolean isScalarCollection(Collection<?> collection) {
        if (collection.isEmpty()) {
            // 空数组按多值处理，落库成 {"v":[]}
            return true;
        }
        for (Object item : collection) {
            if (item instanceof Map || item instanceof Collection) {
                return false;
            }
        }
        return true;
    }

    private static List<String> toStringList(JSONArray array) {
        List<String> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (Object item : array) {
            if (item == null) {
                continue;
            }
            if (item instanceof String text) {
                list.add(text);
            } else if (item instanceof Map || item instanceof Collection) {
                // 对象元素不参与"首值/成员"语义，序列化回字符串保持可比较
                list.add(JSON.toJSONString(item));
            } else {
                list.add(String.valueOf(item));
            }
        }
        return list;
    }

    private static List<String> splitLegacy(String trimmed) {
        List<String> list = new ArrayList<>();
        // 分隔符集合与既有链路保持一致（英文逗号/分号/中文逗号/中文分号），
        // 少认一种就会把"a；b"当成单个值，通知收件人或筛选项会静默错一个
        for (String part : trimmed.split("[,;，；]")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                list.add(value);
            }
        }
        return list.isEmpty() ? Collections.singletonList(trimmed) : list;
    }
}
