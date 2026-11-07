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

package com.privernal.flow.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.dromara.warm.flow.core.entity.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * warm-flow 节点扩展属性解析工具
 * <p>
 * 设计器把节点扩展配置序列化为 {@code [{"code":"xxx","value":"yyy"}]} 存入 flow_node.ext，
 * 抄送节点标记（nodeKind=cc）、票签数量（voteType/voteCount）、超时设置（wfTimeoutHours/wfTimeoutAction）
 * 等运行时语义均从该字段解析。
 * </p>
 *
 * @author saperliu
 */
public final class FlowNodeExtUtil {

    /** 抄送节点标记值：ext.nodeKind = cc */
    public static final String NODE_KIND_CC = "cc";

    /** ext 键：节点类别（cc=抄送） */
    public static final String KEY_NODE_KIND = "nodeKind";

    /** ext 键：票签方式（count=绝对数量） */
    public static final String KEY_VOTE_TYPE = "voteType";

    /** ext 键：通过票数（绝对数量票签） */
    public static final String KEY_VOTE_COUNT = "voteCount";

    /** ext 键：驳回票数（绝对数量票签） */
    public static final String KEY_VOTE_REJECT_COUNT = "voteRejectCount";

    /** ext 键：超时时间（小时） */
    public static final String KEY_TIMEOUT_HOURS = "wfTimeoutHours";

    /** ext 键：超时处理动作 */
    public static final String KEY_TIMEOUT_ACTION = "wfTimeoutAction";

    /** 超时动作：自动通过并跳转到下一节点 */
    public static final String TIMEOUT_AUTO_PASS = "AUTO_PASS";

    /** 超时动作：自动驳回到上一节点 */
    public static final String TIMEOUT_AUTO_REJECT = "AUTO_REJECT";

    /** 超时动作：仅提醒办理人，不自动处理 */
    public static final String TIMEOUT_NOTIFY = "NOTIFY";

    /** ext 键：找不到审批人时的处理策略 */
    public static final String KEY_NO_FOUND_USER = "wfsNoFoundUser";

    /** 找不到审批人：自动进入下一节点 */
    public static final String NO_FOUND_NEXT = "NEXT";

    /** 找不到审批人：交给管理员（组织主管），也是未配置时的默认行为 */
    public static final String NO_FOUND_MANAGE = "MANAGE";

    private FlowNodeExtUtil() {
    }

    /**
     * 解析节点 ext 字符串为键值 Map
     * 兼容设计器数组格式与直接对象格式，解析失败返回空 Map
     */
    public static Map<String, String> parseExt(String ext) {
        Map<String, String> map = new HashMap<>();
        if (ext == null || ext.isBlank()) {
            return map;
        }
        try {
            String trimmed = ext.trim();
            if (trimmed.startsWith("[")) {
                JSONArray array = JSONArray.parseArray(trimmed);
                for (int i = 0; i < array.size(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    if (item != null && item.getString("code") != null) {
                        map.put(item.getString("code"), item.getString("value"));
                    }
                }
            } else if (trimmed.startsWith("{")) {
                JSONObject obj = JSONObject.parseObject(trimmed);
                for (String key : obj.keySet()) {
                    Object value = obj.get(key);
                    if (value != null) {
                        map.put(key, String.valueOf(value));
                    }
                }
            }
        } catch (Exception ignored) {
            // ext 非法时不阻断流程，按无扩展配置处理
        }
        return map;
    }

    /**
     * 节点是否为抄送节点
     */
    public static boolean isCcNode(Node node) {
        return node != null && NODE_KIND_CC.equals(parseExt(node.getExt()).get(KEY_NODE_KIND));
    }

    /**
     * 节点是否配置为「找不到审批人时自动进入下一节点」
     */
    public static boolean isNoHandlerSkip(Node node) {
        return node != null
                && NO_FOUND_NEXT.equals(parseExt(node.getExt()).get(KEY_NO_FOUND_USER));
    }

    /**
     * 解析正整数 ext 值，缺失或非法返回 0
     */
    public static int parseIntValue(Map<String, String> ext, String key) {
        if (ext == null) {
            return 0;
        }
        try {
            String value = ext.get(key);
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 解析超时小时数（大于 0 视为有效配置）
     */
    public static int resolveTimeoutHours(Map<String, String> ext) {
        int hours = parseIntValue(ext, KEY_TIMEOUT_HOURS);
        return hours > 0 ? hours : 0;
    }
}
