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

package com.privernal.dynamic.advanced.service;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.domain.DynamicFormIoConfig;
import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.dynamic.advanced.engine.ActionConfig;
import com.privernal.dynamic.advanced.mapper.DynamicFormIoConfigMapper;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageRuleMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字段引用完整性守卫（T3）：表单列结构变更保存成功后，对本次删除/改名的列
 * 做一次其余配置引用扫描，命中即在保存结果里返回「受影响配置清单」（阻断静默漂移）。
 * 运行时软失败已各链路落地（条件 IS_EMPTY 语义 / 界面黄条）。
 * 打印模板扫描随旧打印实现 M0 下线移除；新单据打印模板（dynamic_doc_template）对
 * 删列的悬空引用明确不做守卫（对照联动 §8.2 先例），渲染期 warnings 兜底（plan-sheet-print §4.1）。
 * 挂点：DynamicFormServiceImpl 删除级联前（列清单 = 物理表有而新 schema 无的列）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColumnReferenceGuard {

    private final DynamicFormIoConfigMapper ioConfigMapper;
    private final DynamicLinkageRuleMapper linkageRuleMapper;

    /** 本次保存扫描到的受影响清单（ThreadLocal：保存请求内暂存，响应组装后由 DynamicFormServiceImpl 清除） */
    private static final ThreadLocal<List<String>> LAST_SCAN = new ThreadLocal<>();

    /** 扫描并暂存（AutoCreateTableServiceImpl 删列分支调用，不阻断保存） */
    public static void recordRemoved(String formId, java.util.Set<String> removedCols) {
        LAST_SCAN.set(scanRemovedReferencesStatic(formId, removedCols));
    }

    /** 供静态内部调用：避免 Bean 注入循环，用 Spring 上下文延迟取 bean */
    private static List<String> scanRemovedReferencesStatic(String formId, Set<String> removedCols) {
        ColumnReferenceGuard guard = AutowireCapableBeanFactoryHolder.get();
        if (guard == null) {
            return List.of();
        }
        return guard.scan(formId, removedCols);
    }

    /** 实际扫描（Bean 方法） */
    public List<String> scan(String formId, Set<String> removedCols) {
        List<String> hits = new ArrayList<>();
        if (removedCols == null || removedCols.isEmpty()) {
            return hits;
        }
        scanIoConfig(formId, removedCols, hits);
        scanLinkageRules(formId, removedCols, hits);
        return hits;
    }

    /** 保存响应组装方（DynamicFormServiceImpl）读取并清除暂存结果 */
    public static List<String> drainLastScan() {
        List<String> result = LAST_SCAN.get();
        LAST_SCAN.remove();
        return result == null ? List.of() : result;
    }

    /**
     * 扫描表单四类配置对「已删列」的引用。
     *
     * @param formId      表单 id
     * @param removedCols 本次保存中物理删除的列编码集合（snake_case 落库列名）
     * @return 受影响配置描述清单（空=无引用，安全）
     */
    public List<String> scanRemovedReferences(String formId, Set<String> removedCols) {
        return scan(formId, removedCols);
    }

    private void scanIoConfig(String formId, Set<String> removed, List<String> hits) {
        DynamicFormIoConfig config = ioConfigMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<DynamicFormIoConfig>lambdaQuery()
                        .eq(DynamicFormIoConfig::getFormId, formId));
        if (config == null || StringUtils.isEmpty(config.getExportColumns())) {
            return;
        }
        try {
            JSONArray codes = JSON.parseArray(config.getExportColumns());
            for (int i = 0; i < codes.size(); i++) {
                if (removed.contains(codes.getString(i))) {
                    hits.add("导入导出配置 export_columns 引用已删列：" + codes.getString(i));
                }
            }
        } catch (Exception e) {
            log.warn("守卫扫描 export_columns 失败 form={}", formId, e);
        }
    }

    private void scanLinkageRules(String formId, Set<String> removed, List<String> hits) {
        List<DynamicLinkageRule> rules = linkageRuleMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<DynamicLinkageRule>lambdaQuery()
                        .eq(DynamicLinkageRule::getFormId, formId));
        for (DynamicLinkageRule rule : rules) {
            Set<String> used = new HashSet<>();
            collectConditionFields(parseQuiet(rule.getConditionTree()), used);
            JSONObject eventConfig = parseQuiet(rule.getEventConfig());
            if (eventConfig != null && eventConfig.getString("fieldCode") != null) {
                used.add(eventConfig.getString("fieldCode"));
            }
            collectActionFields(rule.getActions(), used);
            for (String field : used) {
                if (removed.contains(field) || removed.contains(snake(field))) {
                    hits.add("联动规则 " + rule.getRuleCode() + " 引用已删列：" + field);
                }
            }
        }
    }

    private void collectActionFields(String actionsJson, Set<String> used) {
        List<ActionConfig> actions = parseActions(actionsJson);
        for (ActionConfig action : actions) {
            JSONObject config = action.getConfig();
            if (config == null) {
                continue;
            }
            collectConditionFields(config.getJSONObject("conditionTree"), used);
            JSONArray set = config.getJSONArray("set");
            if (set != null) {
                for (int i = 0; i < set.size(); i++) {
                    JSONObject item = set.getJSONObject(i);
                    if (item != null && item.getString("field") != null) {
                        used.add(item.getString("field"));
                    }
                }
            }
            JSONArray fields = config.getJSONArray("fields");
            if (fields != null) {
                for (int i = 0; i < fields.size(); i++) {
                    JSONObject item = fields.getJSONObject(i);
                    if (item != null && item.getString("field") != null) {
                        used.add(item.getString("field"));
                    }
                }
            }
        }
    }

    private void collectConditionFields(JSONObject node, Set<String> used) {
        if (node == null) {
            return;
        }
        if (node.getString("field") != null) {
            used.add(node.getString("field"));
        }
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectConditionFields(children.getJSONObject(i), used);
            }
        }
    }

    private List<ActionConfig> parseActions(String actions) {
        try {
            List<ActionConfig> list = JSON.parseArray(actions, ActionConfig.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private JSONObject parseQuiet(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String snake(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
