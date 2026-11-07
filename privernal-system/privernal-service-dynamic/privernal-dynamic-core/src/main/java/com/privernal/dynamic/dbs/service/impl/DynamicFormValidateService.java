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

package com.privernal.dynamic.dbs.service.impl;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dba.utils.FormDataTitleUtils;
import com.privernal.dynamic.dba.utils.FormListConfigUtils;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.service.DynamicTableCodePrefixService;
import com.privernal.dynamic.dto.DynamicFormDTO;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单定义纯校验服务（validateDefinition）：
 * 把保存链里"建表之后才爆"的校验全部前置成只读检查，AI/用户在 save 之前先 validate。
 * <p>
 * <b>零副作用</b>：不建表、不写 dynamic_form、不登记关系、不动 metaData 入参
 * （规范化在副本上做）。覆盖：
 * ① 表单编码（装饰后）唯一性；② appId 存在性（保存链目前在建表之后才校验，P19）；
 * ③ 必填三件套 field/label/id；④ 字段规范化撞名；⑤ 保留列名冲突（SYSTEM_FIELD_CODES
 * ——保存链路并不校验它，validate 是新增校验不是搬运）；⑥ TEXT 列配 unique/dbIndex
 * 的 1170 风险（skill §10-P13：整次保存失败）。
 */
@Slf4j
@Service
public class DynamicFormValidateService {

    @Autowired
    private DynamicTableCodePrefixService tableCodePrefixService;

    /**
     * 编码查重走 ignoreDepartment 短路 Mapper：管理端与 /openapi 机器通道（无会话用户）
     * 同一套口径——表单编码含组织前缀、全局唯一，不需要组织附加条件；普通 Mapper 的
     * 基础方法在无会话线程会被数据范围切面改写为 WHERE 1=4（实测踩坑）。
     */
    @Autowired
    private com.privernal.dynamic.dbs.mapper.AiBuildQueryMapper aiBuildQueryMapper;

    @Autowired
    private com.privernal.system.feign.RemoteUserService remoteUserService;

    /**
     * 结构化校验：返回错误清单（空数组 = 通过）。每条 {code, message, field}。
     */
    public List<Map<String, Object>> validateDefinition(DynamicFormDTO form) {
        List<Map<String, Object>> errors = new ArrayList<>();
        if (form == null) {
            return errors;
        }
        checkTableCode(form, errors);
        checkAppId(form, errors);
        checkFields(form, errors);
        String customViewMessage = customViewError(form);
        if (customViewMessage != null) {
            int splitAt = customViewMessage.indexOf('：');
            String code = splitAt > 0 ? customViewMessage.substring(0, splitAt) : "CUSTOM_VIEW_INVALID";
            errors.add(error(code, customViewMessage, null));
        }
        return errors;
    }

    /**
     * 自定义页面（metaData.customView，在线 Vue 代码页）结构兜底校验。
     * <p>
     * 只做入库前防垃圾数据检查（来源/大小/必要块），不做语法编译——编译校验由前端设计页签
     * 用 vue/compiler-sfc 完成，运行时编译失败也只表现为页面错误卡片。错误非空时返回首条
     * 消息（与 backScriptError 的调用约定一致），通过返回 null：
     * <ul>
     * <li>CUSTOM_VIEW_SOURCE_REQUIRED：enabled 且 source 为空</li>
     * <li>CUSTOM_VIEW_SOURCE_TOO_LARGE：单页源码超过 128KB（与前端 compile.ts 上限同值）</li>
     * <li>CUSTOM_VIEW_NO_TEMPLATE：enabled 且源码缺少 template 块</li>
     * <li>CUSTOM_VIEW_TYPE_INVALID：customView/list/edit/source 形状不对（非对象/非字符串）</li>
     * </ul>
     */
    public String customViewError(DynamicFormDTO form) {
        if (form == null || form.getMetaData() == null) {
            return null;
        }
        // HTTP 入参经 Jackson 反序列化，嵌套层是 LinkedHashMap（collectInputFields 同款防御），
        // 统一走 Map 接口读取，不假设 fastjson2 类型
        Object customViewObj = form.getMetaData().get("customView");
        if (!(customViewObj instanceof Map<?, ?> customView)) {
            return customViewObj == null ? null : "CUSTOM_VIEW_TYPE_INVALID：metaData.customView 必须是对象";
        }
        for (String key : List.of("list", "edit")) {
            Object pageObj = customView.get(key);
            if (pageObj == null) {
                continue;
            }
            if (!(pageObj instanceof Map<?, ?> page)) {
                return "CUSTOM_VIEW_TYPE_INVALID：metaData.customView." + key + " 必须是对象";
            }
            Object enabledObj = page.get("enabled");
            boolean enabled = Boolean.TRUE.equals(enabledObj) || "true".equalsIgnoreCase(String.valueOf(enabledObj));
            if (!enabled) {
                continue;
            }
            Object sourceObj = page.get("source");
            if (!(sourceObj instanceof String source) || source.trim().isEmpty()) {
                return "CUSTOM_VIEW_SOURCE_REQUIRED：自定义" + ("list".equals(key) ? "列表" : "编辑")
                        + "页已启用但未填写代码";
            }
            if (source.length() > 128 * 1024) {
                return "CUSTOM_VIEW_SOURCE_TOO_LARGE：自定义页面源码超过 128KB 上限";
            }
            if (!source.contains("<template")) {
                return "CUSTOM_VIEW_NO_TEMPLATE：自定义页面源码缺少 <template> 块";
            }
        }
        return null;
    }

    /** 表单编码：必填 + 装饰后唯一（exclude 自身 id 支持编辑态校验） */
    private void checkTableCode(DynamicFormDTO form, List<Map<String, Object>> errors) {
        if (StringUtils.isEmpty(form.getTableCode())) {
            errors.add(error("TABLE_CODE_REQUIRED", "tableCode 不能为空", null));
            return;
        }
        String decorated = tableCodePrefixService.decorate(form.getTableCode());
        Long count = aiBuildQueryMapper.countFormByTableCode(decorated,
                StringUtils.isEmpty(form.getId()) ? null : form.getId());
        if (count != null && count > 0) {
            errors.add(error("TABLE_CODE_DUPLICATED", "表单编码已存在：" + decorated, decorated));
        }
    }

    /** appId 存在性：保存链在 insert+建物理表+登记关系之后才校验（错一次留半成品表），此处前置 */
    private void checkAppId(DynamicFormDTO form, List<Map<String, Object>> errors) {
        if (StringUtils.isEmpty(form.getAppId())) {
            errors.add(error("APP_ID_REQUIRED", "appId 不能为空", null));
            return;
        }
        try {
            if (form.getAppId() != null && remoteUserService.findAppInfoById(form.getAppId()) == null) {
                errors.add(error("APP_NOT_EXISTS", "应用不存在：" + form.getAppId(), null));
            }
        } catch (Exception e) {
            // 远程服务不可用不阻塞校验流程，但要如实标注未验证
            errors.add(error("APP_CHECK_SKIPPED", "应用存在性校验未完成（服务不可达）：" + e.getMessage(), null));
        }
    }

    /** 字段级检查：必填三件套 / 规范化撞名 / 保留列名 / TEXT+索引 1170 风险 */
    private void checkFields(DynamicFormDTO form, List<Map<String, Object>> errors) {
        JSONObject metaData = form.getMetaData();
        if (metaData == null) {
            errors.add(error("META_DATA_MISSING", "metaData 缺失", null));
            return;
        }
        List<FieldDef> fields = new ArrayList<>();
        collectInputFields(metaData, "main", fields);
        // 规范化撞名 / 保留列名：按物理表分组（main + 每个子表）
        Map<String, Map<String, List<FieldDef>>> byTable = new LinkedHashMap<>();
        for (FieldDef field : fields) {
            byTable.computeIfAbsent(field.tableKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(field.column, k -> new ArrayList<>()).add(field);
        }
        for (Map.Entry<String, Map<String, List<FieldDef>>> tableEntry : byTable.entrySet()) {
            String tableKey = tableEntry.getKey();
            for (Map.Entry<String, List<FieldDef>> columnEntry : tableEntry.getValue().entrySet()) {
                List<FieldDef> same = columnEntry.getValue();
                if (same.size() > 1) {
                    errors.add(error("FIELD_COLLISION", "字段规范化后撞名：" + tableKey + "."
                            + columnEntry.getKey() + " ← " + joinRawFields(same), null));
                }
                FieldDef first = same.get(0);
                if (DynamicFormColumn.isSystemField(columnEntry.getKey())) {
                    errors.add(error("SYSTEM_FIELD_CONFLICT", "字段与系统保留列名冲突："
                            + tableKey + "." + columnEntry.getKey() + " ← " + first.rawField, first.rawField));
                }
            }
        }
        for (FieldDef field : fields) {
            if (StringUtils.isEmpty(field.rawField)) {
                errors.add(error("FIELD_REQUIRED", "输入组件缺少 field 编码（label="
                        + field.label + "）", field.label));
            }
            if (StringUtils.isEmpty(field.label)) {
                errors.add(error("LABEL_REQUIRED", "输入组件缺少 label（field=" + field.rawField + "）", field.rawField));
            }
            if (StringUtils.isEmpty(field.componentId)) {
                errors.add(error("ID_REQUIRED", "输入组件缺少 id（field=" + field.rawField + "）", field.rawField));
            }
            if (field.textType && (field.unique || field.dbIndex)) {
                errors.add(error("TEXT_INDEX_UNSUPPORTED", "列 " + field.column + "（组件 "
                        + field.componentType + "）落 TEXT 物理类型，配 unique/dbIndex 建索引会报 1170"
                        + "并导致整次保存失败（skill §10-P13）", field.rawField));
            }
        }
        checkTitleAndListConfig(metaData, fields, errors);
    }

    /**
     * 数据标题（{@code schemas[0].props.titleFields}）与列表列配置（{@code metaData.listConfig}）
     * 引用的字段必须存在于主表输入字段。
     * <p>
     * 这两处都是「配错不报错」的静默点：标题引用不存在的字段时 {@code FormDataTitleUtils} 取不到值、
     * 返回 null 并保留原标题，表现为「配了标题字段但列表还是老样子」；listConfig 引用不存在的字段
     * 时列配置根本不命中，默认口径照旧生效。所以在这里拦成明确错误。
     */
    private void checkTitleAndListConfig(JSONObject metaData, List<FieldDef> fields,
                                         List<Map<String, Object>> errors) {
        Set<String> mainFields = new HashSet<>();
        for (FieldDef field : fields) {
            if (!"main".equals(field.tableKey)) {
                continue;
            }
            if (StringUtils.isNotEmpty(field.rawField)) {
                mainFields.add(field.rawField);
            }
            if (StringUtils.isNotEmpty(field.column)) {
                mainFields.add(field.column);
            }
        }
        for (String referenced : FormDataTitleUtils.configuredTitleFields(metaData)) {
            if (!referencesKnownField(mainFields, referenced)) {
                errors.add(error("TITLE_FIELD_UNKNOWN", "数据标题引用了不存在的字段：" + referenced
                        + "（titleFields 只能写主表 input 组件的 field 编码，子表行内字段不能作标题）", referenced));
            }
        }
        for (String referenced : FormListConfigUtils.referencedFields(metaData)) {
            if (!referencesKnownField(mainFields, referenced)) {
                errors.add(error("LIST_CONFIG_FIELD_UNKNOWN", "listConfig 引用了不存在的字段：" + referenced
                        + "（列表列配置按规范化列名命中，写错的字段会被静默忽略）", referenced));
            }
        }
    }

    /** 驼峰与下划线两种写法都认——运行时按规范化后的物理列名取值 */
    private static boolean referencesKnownField(Set<String> known, String referenced) {
        if (StringUtils.isEmpty(referenced)) {
            return false;
        }
        String text = referenced.trim();
        return known.contains(text) || known.contains(FormTransferTable.toDbColumnName(text));
    }

    /**
     * 递归收集 input=true 组件（与 FormTransferTable.getChildList 同构的遍历口径）。
     * 子表行内字段按 "sub:{子表field}" 分组独立成表校验。
     * <p>
     * ⚠️ 遍历必须走 Map/Collection 接口而非 fastjson2 具体类型：HTTP 入参经 Jackson
     * 反序列化，根节点是 JSONObject 但嵌套层是 LinkedHashMap/ArrayList（实测踩坑）。
     */
    private void collectInputFields(Object node, String tableKey, List<FieldDef> out) {
        if (node instanceof Map<?, ?> map) {
            Object type = map.get("type");
            boolean input = Boolean.TRUE.equals(map.get("input"))
                    || "true".equals(String.valueOf(map.get("input")));
            if ("sub-table".equals(type)) {
                String subKey = "sub:" + map.get("field");
                Object children = map.get("children");
                if (children instanceof Collection<?> col) {
                    for (Object child : col) {
                        collectInputFields(child, subKey, out);
                    }
                }
                return;
            }
            if (input) {
                FieldDef def = new FieldDef();
                def.tableKey = tableKey;
                def.rawField = text(map.get("field"));
                def.label = text(map.get("label"));
                def.componentId = text(map.get("id"));
                def.componentType = text(type);
                def.column = def.rawField == null ? null : FormTransferTable.toDbColumnName(def.rawField);
                if (def.column != null) {
                    String columnType = FormTransferTable.formCompToColumnType(
                            def.componentType == null ? "text" : def.componentType);
                    def.textType = columnType != null && columnType.toLowerCase().contains("text");
                }
                def.unique = Boolean.TRUE.equals(map.get("unique"))
                        || "true".equals(String.valueOf(map.get("unique")));
                def.dbIndex = Boolean.TRUE.equals(map.get("dbIndex"))
                        || "true".equals(String.valueOf(map.get("dbIndex")));
                out.add(def);
            }
            for (Object value : map.values()) {
                collectInputFields(value, tableKey, out);
            }
        } else if (node instanceof Collection<?> col) {
            for (Object item : col) {
                collectInputFields(item, tableKey, out);
            }
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> error(String code, String message, String field) {
        Map<String, Object> row = new HashMap<>();
        row.put("code", code);
        row.put("message", message);
        row.put("field", field);
        return row;
    }

    private static String joinRawFields(List<FieldDef> fields) {
        List<String> names = new ArrayList<>();
        for (FieldDef field : fields) {
            names.add(String.valueOf(field.rawField));
        }
        return String.join(" / ", names);
    }

    /** 字段中间结构（校验期专用） */
    private static final class FieldDef {
        String tableKey;
        String rawField;
        String column;
        String label;
        String componentId;
        String componentType;
        boolean textType;
        boolean unique;
        boolean dbIndex;
    }
}
