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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.domain.DynamicDocTemplate;
import com.privernal.dynamic.advanced.engine.ConditionEvaluator;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.engine.SensitiveDataMask;
import com.privernal.dynamic.advanced.mapper.DynamicDocTemplateMapper;
import com.privernal.dynamic.advanced.mapper.DynamicFormIoConfigMapper;
import com.privernal.dynamic.dba.utils.JsonValueCodec;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.mapper.DynamicFormColumnMapper;
import com.privernal.dynamic.dbs.service.DynamicSubTableService;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.dynamic.dbs.util.DynamicTableAuthUtil;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.WorkflowUserFilterDTO;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.common.web.domain.CommonResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 单据打印服务端取数通道（plan-sheet-print §3.3，M3）。
 * <p>
 * 存在理由（四条，修 §1.2 存量缺陷）：
 * 1. 返回即可打印的可读值——字典/选项、人员/部门翻译在服务端完成，关联/人员批量回查禁 N+1；
 * 2. 主表行 + selectByParentId 语义的子表行一起返回；子表刻意不加行级数据范围
 *    （既有产品口径：子表 department_id 是保存时编辑人的部门，按行过滤会回显缺行 + 重插重复）；
 * 3. 脱敏在服务端强制（方法内局部变量，修旧实现"挂 @Service 实例字段并发串配置"的缺陷）；
 * 4. 鉴权与行级数据范围在此收口——不复用 /back/report/data/query（聚合语义、1000 行上限），
 *    也不直接用 /dbs/open/dynamic/data/list（返回原始编码值）；
 *    数据读取走 DynamicTableDataMapper（@PrivernalDataScope），不开任何 ignore 开关，
 *    猜到 dataId 也打不出别人范围内的行（修 §1.2 缺陷 1）。
 * 上限：单次 2000 行、硬上限 10000 行，超限进 warnings 而非静默截断（§3.3-5）。
 * 批量语义：dataIds 勾选 N 行返回 docs[] 每行一份，上限按整批合计计（§3.3-7）。
 */
@Slf4j
@Service
public class DocPrintDataService {

    /** 单次建议上限（明细行合计，§3.3-5） */
    static final int ROW_LIMIT = 2000;
    /** 硬上限：超过即截断（带 warning，不静默） */
    static final int HARD_ROW_LIMIT = 10000;
    /** 单批数据行（主表 docs）上限 */
    static final int DOC_LIMIT = 50;

    /** 关联显示值批量回查的单批 IN 上限（与报表侧 LOOKUP_BATCH_LIMIT 同口径） */
    private static final int RELATION_BATCH_LIMIT = 500;
    /** 表名/列名白名单：标识符经 ${} 拼进 SQL，必须先校验防注入（与 DynamicReportQueryService 同口径） */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    /** 过滤操作符白名单（§3.3-6，与报表侧 FILTER_OPS 同集合；值一律 #{} 绑定） */
    private static final Map<String, String> FILTER_OPS = Map.of(
            "eq", "=", "ne", "<>", "like", "LIKE", "gt", ">", "ge", ">=", "lt", "<", "le", "<=");
    /** 允许作为过滤列的公共列；业务列另从 dynamic_form_column 取 */
    private static final Set<String> COMMON_FILTER_COLUMNS = Set.of("id", "created_time", "updated_time", "status");

    private static final Set<String> USER_COMPONENTS = Set.of("user-select", "user-multi-select");
    private static final Set<String> DEPT_COMPONENTS = Set.of("dept-select", "dept-multi-select");
    private static final Set<String> OPTION_COMPONENTS = Set.of("select", "radio", "checkbox", "cascader");
    private static final Set<String> RELATION_COMPONENTS = Set.of("relation-attr", "relation-backfill");
    private static final Map<String, String> DATA_STATUS_LABELS = Map.of(
            "0", "草稿", "1", "启用", "2", "进行中", "3", "禁用", "9", "作废");

    @Autowired
    private DynamicDocTemplateMapper docTemplateMapper;

    @Autowired
    private LinkageSupport linkageSupport;

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private DynamicSubTableService dynamicSubTableService;

    @Autowired
    private DynamicFormColumnMapper formColumnMapper;

    @Autowired
    private DynamicFormIoConfigMapper ioConfigMapper;

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private com.privernal.dynamic.report.service.DynamicReportTableResolver reportTableResolver;

    /** 批量回查通道（DynamicReportQueryMapper.lookupDisplay）：首参是表名，行级范围仍由切面按查看者追加 */
    @Autowired
    private com.privernal.dynamic.report.service.DynamicReportExecutor reportExecutor;

    /** 可用字段树（设计态字段侧栏 / 运行态字段过滤用） */
    public List<Map<String, Object>> fieldTree(DynamicDocTemplate template) {
        DynamicForm form = requireForm(template);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JSONObject child : com.privernal.dynamic.advanced.engine.FormSchemaFields.inputChildren(form)) {
            result.add(fieldNode(child, "main"));
        }
        JSONObject metaData = form.getMetaData();
        JSONArray schemas = metaData == null ? null : metaData.getJSONArray("schemas");
        JSONArray children = schemas == null || schemas.isEmpty() ? null : schemas.getJSONObject(0).getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                if (child == null || !"sub-table".equals(child.getString("type"))) {
                    continue;
                }
                result.add(fieldNode(child, "detail-group"));
                JSONArray subChildren = child.getJSONArray("children");
                if (subChildren != null) {
                    for (int j = 0; j < subChildren.size(); j++) {
                        JSONObject sub = subChildren.getJSONObject(j);
                        if (sub != null && StringUtils.isNotEmpty(sub.getString("field"))) {
                            result.add(fieldNode(sub, "detail"));
                        }
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Object> fieldNode(JSONObject child, String kind) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("field", child.getString("field"));
        node.put("label", child.getString("label"));
        node.put("dataType", dataTypeOf(child.getString("type")));
        node.put("kind", kind);
        return node;
    }

    private String dataTypeOf(String componentType) {
        if (componentType == null) {
            return "string";
        }
        if (USER_COMPONENTS.contains(componentType)) {
            return "user";
        }
        if (DEPT_COMPONENTS.contains(componentType)) {
            return "dept";
        }
        if (OPTION_COMPONENTS.contains(componentType)) {
            return "option";
        }
        switch (componentType) {
            case "input-number":
            case "number":
                return "number";
            case "date-picker":
            case "date":
            case "time-picker":
                return "date";
            case "upload-file":
            case "upload-image":
                return "file";
            default:
                return "string";
        }
    }

    /**
     * 取数主入口：模板 + 勾选数据行（或过滤条件）→ 每行一份 docs（可读值、已脱敏、行范围已收口）。
     * <p>
     * 两趟处理：趟 1 只取原始行并归一键，趟 2 才翻译脱敏——关联显示值需要先看全批的 id 集合，
     * 才能按被关联表一次 IN 回查（§3.3-1 禁 N+1）。
     *
     * @param templateCode 模板稳定编码（status='1'）
     * @param dataIds      全量勾选 id（禁止前端只传当前页缓存过滤后的子集）
     * @param filters      过滤条件 [{field,op,value}]；为 null 时回落模板 params_json 的 default
     */
    public Map<String, Object> printData(String templateCode, List<String> dataIds, JSONArray filters) {
        if (StringUtils.isEmpty(templateCode)) {
            throw new ServiceException("templateCode 不能为空");
        }
        DynamicDocTemplate template = docTemplateMapper.selectOne(Wrappers.<DynamicDocTemplate>lambdaQuery()
                .eq(DynamicDocTemplate::getTemplateCode, templateCode)
                .eq(DynamicDocTemplate::getStatus, "1")
                .last("limit 1"));
        if (template == null) {
            throw new ServiceException("打印模板不存在或已停用：" + templateCode);
        }
        DynamicForm form = requireForm(template);

        List<String> requestedIds = dataIds == null ? new ArrayList<>() : new ArrayList<>(dataIds);
        JSONArray effective = filters != null ? filters : defaultFiltersFromParams(template.getParamsJson());
        boolean hasFilter = effective != null && !effective.isEmpty();
        if (!requestedIds.isEmpty() && requestedIds.size() > DOC_LIMIT) {
            throw new ServiceException("单次最多打印 " + DOC_LIMIT + " 条单据（当前 " + requestedIds.size() + " 条）");
        }
        if (requestedIds.isEmpty() && !hasFilter) {
            throw new ServiceException("dataIds 与过滤条件至少给一个");
        }
        // 参数过滤对两种入口都生效：勾选模式下收窄成"既勾了、又匹配参数"的行，
        // 否则运行态参数栏改了也看不出变化（§8-③ 临时改参数重新取数）
        if (hasFilter) {
            requestedIds = queryIdsByFilter(form, effective, requestedIds.isEmpty() ? null : requestedIds);
        }

        // 运行态按表功能权限（tableCode 来自请求侧数据，注解表达不了 → 判定工具类，§3.6）；
        // 按钮权限 dynamic:data:print 在控制器 @PreAuthorize
        DynamicTableAuthUtil.requirePermission(form.getTableCode(), "list");

        String database = linkageSupport.resolveDatabase(form.getOrganizationId());
        String physicalTable = form.getTableCode();

        // 脱敏配置：方法内局部变量（修旧实现实例字段并发串配置）
        Set<String> sensitiveColumns = loadSensitiveColumns(form.getId());
        String sensitivePolicy = loadSensitivePolicy(form.getId());

        // 字段组件信息（翻译用）：field → 组件 schema
        Map<String, JSONObject> componentByField = componentByField(form);
        List<DynamicSubTableService.SubTableDef> subTableDefs =
                dynamicSubTableService.resolveSubTables(physicalTable, form.getMetaData());
        Map<String, Map<String, JSONObject>> subComponentsByField = new LinkedHashMap<>();
        for (DynamicSubTableService.SubTableDef def : subTableDefs) {
            subComponentsByField.put(def.getField(), subComponentsOf(form, def.getField()));
        }

        List<String> warnings = new ArrayList<>();
        if (hasFilter && requestedIds.isEmpty()) {
            warnings.add("按过滤条件未匹配到数据行（行级数据范围同样生效）");
        }

        // ---- 趟 1：取原始行（主表 + 子表），只归一键不翻译 ----
        List<String> keptIds = new ArrayList<>();
        List<Map<String, Object>> mainRows = new ArrayList<>();
        List<Map<String, List<Map<String, Object>>>> detailRows = new ArrayList<>();
        int totalRows = 0;
        boolean truncated = false;
        for (String dataId : requestedIds) {
            // 行级数据范围在此收口：findById 走 @PrivernalDataScope mapper，不开 ignore——
            // 猜到 dataId 也打不出范围外的行（null 即范围外/不存在，软跳过进 warnings）
            Map<String, Object> row = dynamicTableService.findById(database, physicalTable, dataId);
            if (row == null || row.isEmpty()) {
                warnings.add("数据不存在或不在你的数据范围内，已跳过：" + dataId);
                continue;
            }
            Map<String, List<Map<String, Object>>> subs = new LinkedHashMap<>();
            for (DynamicSubTableService.SubTableDef def : subTableDefs) {
                // 子表行不套行级范围（既有产品口径，§3.3-2）
                List<Map<String, Object>> subRows = findDetailRows(database, def.getChildTable(), dataId, row, def.getField());
                if (totalRows + subRows.size() > HARD_ROW_LIMIT) {
                    int allowed = Math.max(0, HARD_ROW_LIMIT - totalRows);
                    if (allowed < subRows.size()) {
                        warnings.add("明细行合计超过硬上限 " + HARD_ROW_LIMIT + " 行，已截断（明细 " + def.getField() + "）；请缩小打印范围");
                        subRows = subRows.subList(0, allowed);
                        truncated = true;
                    }
                }
                totalRows += subRows.size();
                List<Map<String, Object>> normalized = new ArrayList<>(subRows.size());
                for (Map<String, Object> subRow : subRows) {
                    normalized.add(normalizeKeys(subRow));
                }
                subs.put(def.getField(), normalized);
                if (truncated) {
                    break;
                }
            }
            keptIds.add(dataId);
            mainRows.add(normalizeKeys(row));
            detailRows.add(subs);
            if (truncated) {
                break;
            }
        }

        // ---- 趟 1.5：整批预取翻译上下文——人员/部门各一次远程批量查、每张被关联表一次 IN，
        // 替代原先"每行各调一次"的放大（跨服务调用比 DB 单查更贵）----
        TranslateContext ctx = loadTranslateContext(database, mainRows, componentByField, detailRows, subComponentsByField);

        // ---- 趟 2：翻译 + 脱敏（全部走内存预取结果，不再回库）----
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int i = 0; i < keptIds.size(); i++) {
            Map<String, Object> main = translateRow(mainRows.get(i), componentByField, sensitiveColumns,
                    sensitivePolicy, ctx, warnings);
            Map<String, Object> details = new LinkedHashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : detailRows.get(i).entrySet()) {
                Map<String, JSONObject> subComponents = subComponentsByField.getOrDefault(entry.getKey(), Map.of());
                List<Map<String, Object>> translated = new ArrayList<>(entry.getValue().size());
                for (Map<String, Object> subRow : entry.getValue()) {
                    translated.add(translateRow(subRow, subComponents, sensitiveColumns, sensitivePolicy,
                            ctx, warnings));
                }
                details.put(entry.getKey(), translated);
            }
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", keptIds.get(i));
            doc.put("main", main);
            doc.put("details", details);
            docs.add(doc);
        }
        if (totalRows > ROW_LIMIT && totalRows <= HARD_ROW_LIMIT) {
            warnings.add("明细行合计 " + totalRows + " 行，超过单次建议上限 " + ROW_LIMIT + " 行，打印耗时可能明显变长");
        }

        // 摘要放首位：@Log 的 operation_result 会截断，前 900 字符必须留下
        // "打了哪个模板、几份、多少明细行、几条告警"（§3.6 留痕口径）
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("templateCode", templateCode);
        summary.put("requested", requestedIds.size());
        summary.put("printed", docs.size());
        summary.put("detailRows", totalRows);
        summary.put("warnings", warnings.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("docs", docs);
        result.put("warnings", warnings);
        result.put("templateName", template.getName());
        return result;
    }

    private DynamicForm requireForm(DynamicDocTemplate template) {
        if (StringUtils.isEmpty(template.getFormId())) {
            throw new ServiceException("模板未关联表单，无法取数");
        }
        DynamicForm form = linkageSupport.loadForm(template.getFormId());
        if (form == null) {
            throw new ServiceException("模板关联表单不存在");
        }
        return form;
    }

    /** 组件 schema 索引：field → child（props/componentProps 双兜） */
    private Map<String, JSONObject> componentByField(DynamicForm form) {
        Map<String, JSONObject> map = new HashMap<>();
        JSONObject metaData = form.getMetaData();
        JSONArray schemas = metaData == null ? null : metaData.getJSONArray("schemas");
        JSONArray children = schemas == null || schemas.isEmpty() ? null : schemas.getJSONObject(0).getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                if (child != null && StringUtils.isNotEmpty(child.getString("field"))) {
                    map.put(child.getString("field"), child);
                }
            }
        }
        return map;
    }

    private JSONArray subChildrenOf(DynamicForm form, String subField) {
        JSONObject metaData = form.getMetaData();
        JSONArray schemas = metaData == null ? null : metaData.getJSONArray("schemas");
        JSONArray children = schemas == null || schemas.isEmpty() ? null : schemas.getJSONObject(0).getJSONArray("children");
        if (children == null) {
            return null;
        }
        for (int i = 0; i < children.size(); i++) {
            JSONObject child = children.getJSONObject(i);
            if (child != null && subField.equals(child.getString("field"))) {
                return child.getJSONArray("children");
            }
        }
        return null;
    }

    /**
     * 行翻译：组件类型翻译（选项/人员/部门/开关/状态/多值信封/关联）→ 敏感脱敏。
     * 入参 row 必须已过 {@link #normalizeKeys}；人员/部门/关联显示值一律从 {@link TranslateContext}
     * 取（整批预取在此之上完成），本方法内不得发起任何远程或数据库调用。
     */
    private Map<String, Object> translateRow(Map<String, Object> normalized, Map<String, JSONObject> componentByField,
                                             Set<String> sensitiveColumns, String sensitivePolicy,
                                             TranslateContext ctx, List<String> warnings) {
        // 逐字段翻译 + 脱敏
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JSONObject> entry : componentByField.entrySet()) {
            String field = entry.getKey();
            JSONObject comp = entry.getValue();
            if ("sub-table".equals(comp.getString("type"))) {
                continue;
            }
            Object value = ConditionEvaluator.lookup(normalized, field);
            if (value == null) {
                out.put(field, "");
                continue;
            }
            String translated = translateValue(String.valueOf(value), comp, ctx);
            // 敏感列：HIDE 不出值，MASK 服务端脱敏后输出（§3.3-3：前端只做展示，不作为脱敏点）
            String realKey = ConditionEvaluator.lookupKey(normalized, field);
            if (realKey != null && sensitiveColumns.contains(realKey)) {
                if ("HIDE".equals(sensitivePolicy)) {
                    continue;
                }
                translated = SensitiveDataMask.mask(translated);
            }
            out.put(field, translated);
        }
        // 公共列保留（id/created_by/created_time 等可作为占位符）
        for (String common : Arrays.asList("id", "created_time", "updated_time")) {
            Object v = normalized.get(common);
            if (v != null) {
                out.putIfAbsent(common, String.valueOf(v));
            }
        }
        if (normalized.containsKey("status")) {
            out.put("status", DATA_STATUS_LABELS.getOrDefault(String.valueOf(normalized.get("status")),
                    String.valueOf(normalized.get("status"))));
        }
        return out;
    }

    private String translateValue(String raw, JSONObject comp, TranslateContext ctx) {
        String type = comp.getString("type");
        if (OPTION_COMPONENTS.contains(type)) {
            JSONObject props = compProps(comp);
            Map<String, String> optionMap = new HashMap<>();
            JSONArray options = props == null ? null : props.getJSONArray("options");
            if (options != null) {
                for (int i = 0; i < options.size(); i++) {
                    JSONObject opt = options.getJSONObject(i);
                    if (opt != null && opt.get("value") != null) {
                        optionMap.put(String.valueOf(opt.get("value")),
                                opt.getString("label") == null ? String.valueOf(opt.get("value")) : opt.getString("label"));
                    }
                }
            }
            // 多值信封先转可读串再按选项回译
            String display = displayOf(raw);
            List<String> parts = splitMulti(display);
            List<String> labels = new ArrayList<>();
            for (String part : parts) {
                labels.add(optionMap.getOrDefault(part, part));
            }
            return String.join("、", labels);
        }
        if (USER_COMPONENTS.contains(type)) {
            List<String> labels = new ArrayList<>();
            for (String id : splitMulti(displayOf(raw))) {
                labels.add(ctx.userNames.getOrDefault(id, id));
            }
            return String.join("、", labels);
        }
        if (DEPT_COMPONENTS.contains(type)) {
            List<String> labels = new ArrayList<>();
            for (String id : splitMulti(displayOf(raw))) {
                labels.add(ctx.deptNames.getOrDefault(id, id));
            }
            return String.join("、", labels);
        }
        if ("switch".equals(type)) {
            JSONObject props = compProps(comp);
            String checked = props == null ? null : props.getString("checkedValue");
            String unchecked = props == null ? null : props.getString("uncheckedValue");
            if ("true".equals(raw) || "1".equals(raw)) {
                return StringUtils.isEmpty(checked) ? "是" : checked;
            }
            if ("false".equals(raw) || "0".equals(raw)) {
                return StringUtils.isEmpty(unchecked) ? "否" : unchecked;
            }
            return raw;
        }
        if (RELATION_COMPONENTS.contains(type)) {
            // 关联字段：值=被关联表记录 id，显示值来自趟 1.5 的整批 IN 回查；翻不出来保留原编码
            String key = relationKeyOf(comp);
            if (key != null) {
                Map<String, String> titles = ctx.relationTitles.getOrDefault(key, Map.of());
                List<String> labels = new ArrayList<>();
                for (String id : splitMulti(displayOf(raw))) {
                    labels.add(titles.getOrDefault(id, id));
                }
                return String.join("、", labels);
            }
            return displayOf(raw);
        }
        return displayOf(raw);
    }

    /** 多值/信封形态转顿号串（JsonValueCodec.formatForDisplay，§3.3-1） */
    private String displayOf(String raw) {
        if (raw == null) {
            return "";
        }
        if (JsonValueCodec.isEnvelope(raw) || (raw.trim().startsWith("[") && JSON.isValid(raw))) {
            String formatted = JsonValueCodec.formatForDisplay(raw, null);
            return formatted.isEmpty() ? raw : formatted;
        }
        return raw;
    }

    private List<String> splitMulti(Object value) {
        if (value == null) {
            return List.of();
        }
        String raw = String.valueOf(value);
        List<String> result = new ArrayList<>();
        if (JsonValueCodec.isEnvelope(raw) || (raw.trim().startsWith("[") && JSON.isValid(raw))) {
            String formatted = JsonValueCodec.formatForDisplay(raw, null);
            if (!formatted.isEmpty()) {
                raw = formatted;
            }
        }
        for (String part : raw.split("[,;，；]")) {
            if (StringUtils.isNotBlank(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private JSONObject compProps(JSONObject comp) {
        JSONObject props = comp.getJSONObject("props");
        return props != null ? props : comp.getJSONObject("componentProps");
    }

    private JSONObject relationOf(JSONObject comp) {
        JSONObject relation = comp.getJSONObject("relation");
        if (relation != null) {
            return relation;
        }
        JSONObject props = compProps(comp);
        return props == null ? null : props.getJSONObject("relation");
    }

    /**
     * 一趟取数攒出来的翻译上下文。刻意做成调用内的局部对象而非 @Service 实例字段——
     * 旧实现把同类状态挂单例上、靠 finally 重置，并发打印不同表单会串配置（§1.2 缺陷 2）。
     */
    private static final class TranslateContext {
        final Map<String, String> userNames = new HashMap<>();
        final Map<String, String> deptNames = new HashMap<>();
        /** 关系键（formCode#displayField）→ 记录 id → 显示值 */
        final Map<String, Map<String, String>> relationTitles = new HashMap<>();
    }

    /** 整批预取人员昵称 / 部门名 / 关联显示值：各一次批量调用，替代原先按行放大的远程与 DB 往返 */
    private TranslateContext loadTranslateContext(String database,
                                                  List<Map<String, Object>> mainRows,
                                                  Map<String, JSONObject> mainComponents,
                                                  List<Map<String, List<Map<String, Object>>>> detailRows,
                                                  Map<String, Map<String, JSONObject>> subComponentsByField) {
        Set<String> userIds = new LinkedHashSet<>();
        Set<String> deptIds = new LinkedHashSet<>();
        Map<String, Set<String>> relationIds = new LinkedHashMap<>();
        collectTranslateKeys(mainRows, mainComponents, userIds, deptIds, relationIds);
        for (Map<String, List<Map<String, Object>>> subs : detailRows) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : subs.entrySet()) {
                collectTranslateKeys(entry.getValue(), subComponentsByField.getOrDefault(entry.getKey(), Map.of()),
                        userIds, deptIds, relationIds);
            }
        }
        TranslateContext ctx = new TranslateContext();
        ctx.userNames.putAll(findUserNames(userIds));
        ctx.deptNames.putAll(findDeptNames(deptIds));
        for (Map.Entry<String, Set<String>> entry : relationIds.entrySet()) {
            Map<String, String> found = batchRelationTitles(database, entry.getKey(), entry.getValue());
            if (!found.isEmpty()) {
                ctx.relationTitles.put(entry.getKey(), found);
            }
        }
        return ctx;
    }

    private void collectTranslateKeys(List<Map<String, Object>> rows, Map<String, JSONObject> components,
                                      Set<String> userIds, Set<String> deptIds,
                                      Map<String, Set<String>> relationIds) {
        if (rows.isEmpty() || components.isEmpty()) {
            return;
        }
        for (Map.Entry<String, JSONObject> entry : components.entrySet()) {
            JSONObject comp = entry.getValue();
            String type = comp.getString("type");
            boolean isUser = USER_COMPONENTS.contains(type);
            boolean isDept = DEPT_COMPONENTS.contains(type);
            boolean isRelation = RELATION_COMPONENTS.contains(type);
            if (!isUser && !isDept && !isRelation) {
                continue;
            }
            String relationKey = isRelation ? relationKeyOf(comp) : null;
            if (isRelation && relationKey == null) {
                continue;
            }
            for (Map<String, Object> row : rows) {
                Object value = ConditionEvaluator.lookup(row, entry.getKey());
                if (value == null) {
                    continue;
                }
                List<String> ids = splitMulti(String.valueOf(value));
                if (isUser) {
                    userIds.addAll(ids);
                } else if (isDept) {
                    deptIds.addAll(ids);
                } else {
                    relationIds.computeIfAbsent(relationKey, k -> new LinkedHashSet<>()).addAll(ids);
                }
            }
        }
    }

    /** 关联缓存键：逻辑 formCode + '#' + displayField；配置不全或显示列名非法时返回 null（不翻译） */
    private String relationKeyOf(JSONObject comp) {
        JSONObject relation = relationOf(comp);
        if (relation == null) {
            return null;
        }
        String formCode = relation.getString("formCode");
        String displayField = relation.getString("displayField");
        if (StringUtils.isEmpty(formCode) || StringUtils.isEmpty(displayField)) {
            return null;
        }
        if (!IDENTIFIER_PATTERN.matcher(displayField).matches()) {
            log.warn("打印关联字段显示列名非法，跳过翻译：{}", displayField);
            return null;
        }
        return formCode + "#" + displayField;
    }

    /**
     * 关联显示值批量回查：一批 IN 查询替代逐条 findById（§3.3-1 禁 N+1）。
     * 走 DynamicReportQueryMapper.lookupDisplay——首参是被关联表名，行级范围仍由切面按查看者追加，
     * 翻不出来的编码保持原值，不泄露显示列（与报表侧 queryDisplayValues 同一条路与同口径校验）。
     */
    private Map<String, String> batchRelationTitles(String database, String key, Set<String> ids) {
        Map<String, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        int split = key.indexOf('#');
        String formCode = key.substring(0, split);
        String displayField = key.substring(split + 1);
        String physical = resolvePhysicalByLogical(formCode);
        if (physical == null || !IDENTIFIER_PATTERN.matcher(physical).matches()) {
            log.warn("打印关联表解析失败或表名非法，保留编码返回：{}", formCode);
            return result;
        }
        List<String> idList = new ArrayList<>(ids);
        for (int from = 0; from < idList.size(); from += RELATION_BATCH_LIMIT) {
            List<String> batch = idList.subList(from, Math.min(from + RELATION_BATCH_LIMIT, idList.size()));
            List<String> placeholders = new ArrayList<>(batch.size());
            Map<String, Object> paramMap = new HashMap<>();
            for (int i = 0; i < batch.size(); i++) {
                String name = "c" + i;
                placeholders.add("#{paramMap." + name + "}");
                paramMap.put(name, batch.get(i));
            }
            String sql = "SELECT id AS k, " + displayField + " AS v FROM " + physical
                    + " WHERE deleted_flag = 'N' AND id IN (" + String.join(", ", placeholders) + ")";
            try {
                for (Map<String, Object> row : reportExecutor.lookupDisplay(database, physical, sql, paramMap)) {
                    Object k = row.get("k");
                    Object v = row.get("v");
                    if (k != null && v != null) {
                        result.put(String.valueOf(k), String.valueOf(v));
                    }
                }
            } catch (Exception e) {
                log.warn("打印关联字段批量回查失败 {}（{} 个编码）", physical, batch.size(), e);
            }
        }
        return result;
    }

    /**
     * 子表行读取：独立子表行是后引入的落库形态，历史数据只有主表里的 JSON 快照列，
     * 不回退解析快照的话这类表打印永远取不到明细——与 CommonController.readBusinessRow
     * 的既有读取口径保持一致（§3.3-2）。
     */
    private List<Map<String, Object>> findDetailRows(String database, String childTable, String dataId,
                                                      Map<String, Object> mainRow, String subField) {
        List<Map<String, Object>> rows = dynamicSubTableService.findRows(database, childTable, dataId);
        if (rows != null && !rows.isEmpty()) {
            return rows;
        }
        Object snapshot = mainRow.get(subField);
        if (snapshot == null) {
            snapshot = mainRow.get(FormTransferTable.toDbColumnName(subField));
        }
        if (snapshot == null) {
            return List.of();
        }
        try {
            Object parsed = snapshot instanceof List ? snapshot : JSON.parse(String.valueOf(snapshot));
            if (parsed instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parsedRows = (List<Map<String, Object>>) parsed;
                return parsedRows;
            }
        } catch (Exception e) {
            log.warn("打印子表 JSON 快照解析失败 {} {}", childTable, dataId);
        }
        return List.of();
    }

    /** 键归一：下划线列 → 驼峰别名（模板占位符用的是设计器里的驼峰字段编码） */
    private Map<String, Object> normalizeKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>(row);
        Map<String, Object> aliases = new HashMap<>();
        row.forEach((key, value) -> {
            if (key != null && key.indexOf('_') >= 0) {
                aliases.putIfAbsent(camelCase(key), value);
            }
        });
        aliases.forEach(normalized::putIfAbsent);
        return normalized;
    }

    /** 子表组件索引：field → child（与主表同构，作用域在该子表 schema 内） */
    private Map<String, JSONObject> subComponentsOf(DynamicForm form, String subField) {
        Map<String, JSONObject> map = new HashMap<>();
        JSONArray subChildren = subChildrenOf(form, subField);
        if (subChildren != null) {
            for (int i = 0; i < subChildren.size(); i++) {
                JSONObject sub = subChildren.getJSONObject(i);
                if (sub != null && StringUtils.isNotEmpty(sub.getString("field"))) {
                    map.put(sub.getString("field"), sub);
                }
            }
        }
        return map;
    }

    /**
     * 过滤条件取数（§3.3-6）：列必须命中该表单 dynamic_form_column 的业务列或少量公共列，
     * 操作符走 FILTER_OPS 白名单，**值一律 #{paramMap.x} 绑定**。
     * 刻意不走 findByPage/selectList：那条路的条件是 {@code ${fieldValue}} 字符串拼接
     * （FormTransferTable 的既有注入面），文档明令不得复制该口径。
     * 首参表名让 @PrivernalDataScope 按查看者追加行范围，与 findById 同口径；上限 DOC_LIMIT 份。
     */
    /**
     * 过滤条件取数（§3.3-6）：列必须命中该表单 dynamic_form_column 的业务列或少量公共列，
     * 操作符走 FILTER_OPS 白名单，**值一律 #{paramMap.x} 绑定**。
     * 刻意不走 findByPage/selectList：那条路的条件是 {@code ${fieldValue}} 字符串拼接
     * （FormTransferTable 的既有注入面），文档明令不得复制该口径。
     * 首参表名让 @PrivernalDataScope 按查看者追加行范围，与 findById 同口径；上限 DOC_LIMIT 份。
     *
     * @param scopeIds 勾选模式下传入的 id 集合：过滤结果收窄到"既在勾选内、又匹配参数"的行
     */
    private List<String> queryIdsByFilter(DynamicForm form, JSONArray filters, List<String> scopeIds) {
        String physical = form.getTableCode();
        if (StringUtils.isEmpty(physical) || !IDENTIFIER_PATTERN.matcher(physical).matches()) {
            throw new ServiceException("表单物理表名非法，无法按条件取数");
        }
        String database = linkageSupport.resolveDatabase(form.getOrganizationId());
        Set<String> allowed = allowedFilterColumns(form);
        List<String> whereParts = new ArrayList<>();
        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < filters.size(); i++) {
            JSONObject filter = filters.getJSONObject(i);
            if (filter == null) {
                continue;
            }
            String field = filter.getString("field");
            Object value = filter.get("value");
            if (StringUtils.isBlank(field) || value == null || StringUtils.isEmpty(String.valueOf(value))) {
                continue;
            }
            if (!IDENTIFIER_PATTERN.matcher(field).matches()) {
                throw new ServiceException("非法的过滤字段名：" + field);
            }
            String column = resolveFilterColumn(field, allowed);
            if (column == null) {
                throw new ServiceException("过滤字段不在该表单的可过滤列内：" + field);
            }
            String op = StringUtils.isEmpty(filter.getString("op")) ? "eq" : filter.getString("op").toLowerCase();
            String sqlOp = FILTER_OPS.get(op);
            if (sqlOp == null) {
                throw new ServiceException("不支持的过滤操作符：" + op);
            }
            String paramName = "p" + i;
            paramMap.put(paramName, "LIKE".equals(sqlOp) ? "%" + value + "%" : value);
            whereParts.add("`" + column + "` " + sqlOp + " #{paramMap." + paramName + "}");
        }
        if (whereParts.isEmpty()) {
            // 参数值全空按"不过滤"处理：勾选模式回落到原勾选集合，避免清空输入框就把打印打挂
            return scopeIds == null ? new ArrayList<>() : new ArrayList<>(scopeIds);
        }
        if (scopeIds != null && !scopeIds.isEmpty()) {
            List<String> idPlaceholders = new ArrayList<>(scopeIds.size());
            for (int i = 0; i < scopeIds.size(); i++) {
                String name = "d" + i;
                paramMap.put(name, scopeIds.get(i));
                idPlaceholders.add("#{paramMap." + name + "}");
            }
            whereParts.add(0, "`id` IN (" + String.join(", ", idPlaceholders) + ")");
        }
        String sql = "SELECT id FROM " + physical + " WHERE deleted_flag = 'N' AND "
                + String.join(" AND ", whereParts) + " ORDER BY `created_time` DESC LIMIT " + DOC_LIMIT;
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> row : reportExecutor.lookupDisplay(database, physical, sql, paramMap)) {
            Object id = row.get("id");
            if (id != null) {
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }

    /** 可过滤列白名单：该表单的业务列（dynamic_form_column）+ 少量公共列 */
    private Set<String> allowedFilterColumns(DynamicForm form) {
        Set<String> allowed = new HashSet<>(COMMON_FILTER_COLUMNS);
        for (DynamicFormColumn column : formColumnMapper.selectList(
                Wrappers.<DynamicFormColumn>lambdaQuery().eq(DynamicFormColumn::getFormId, form.getId()))) {
            if (StringUtils.isNotEmpty(column.getFiledCode())) {
                allowed.add(column.getFiledCode());
            }
        }
        return allowed;
    }

    /** 参数里的字段编码可能是驼峰（设计器口径），物理列是下划线：两种写法都认，最终以白名单内的列为准 */
    private String resolveFilterColumn(String field, Set<String> allowed) {
        if (allowed.contains(field)) {
            return field;
        }
        String snake = snakeCase(field);
        return allowed.contains(snake) ? snake : null;
    }

    /**
     * 模板 params_json 的默认值回落（§3.3 参数定义 [{field,op,label,default}]）：
     * 运行态未显式传过滤条件时按默认参数取数，让 params_json 不再是落库后无人消费的死列。
     */
    private JSONArray defaultFiltersFromParams(String paramsJson) {
        if (StringUtils.isEmpty(paramsJson)) {
            return null;
        }
        JSONArray params;
        try {
            params = JSON.parseArray(paramsJson);
        } catch (Exception e) {
            log.warn("模板 params_json 解析失败，忽略默认参数：{}", paramsJson, e);
            return null;
        }
        if (params == null || params.isEmpty()) {
            return null;
        }
        JSONArray filters = new JSONArray();
        for (int i = 0; i < params.size(); i++) {
            JSONObject param = params.getJSONObject(i);
            if (param == null || param.get("default") == null || StringUtils.isEmpty(String.valueOf(param.get("default")))) {
                continue;
            }
            JSONObject filter = new JSONObject();
            filter.put("field", param.getString("field"));
            filter.put("op", param.getString("op"));
            filter.put("value", param.get("default"));
            filters.add(filter);
        }
        return filters;
    }

    private String snakeCase(String field) {
        StringBuilder sb = new StringBuilder(field.length() + 4);
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String resolvePhysicalByLogical(String logicalCode) {
        DynamicForm form = reportTableResolver.resolveForm(logicalCode);
        return form == null ? null : form.getTableCode();
    }

    private Map<String, String> findUserNames(Set<String> userIds) {
        Map<String, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        try {
            WorkflowUserFilterDTO filter = new WorkflowUserFilterDTO();
            filter.setUserList(new ArrayList<>(userIds));
            CommonResult<List<SystemUserDTO>> result = remoteUserService.findUserListByIds(filter, "");
            if (result.getCode() == CommonResult.SUCCESS && result.getData() != null) {
                for (SystemUserDTO user : result.getData()) {
                    names.put(user.getId(), user.getNickName());
                }
            }
        } catch (Exception e) {
            log.warn("打印批量查询用户昵称失败 userIds {}", userIds, e);
        }
        return names;
    }

    private Map<String, String> findDeptNames(Set<String> deptIds) {
        Map<String, String> names = new HashMap<>();
        if (deptIds.isEmpty()) {
            return names;
        }
        try {
            WorkflowUserFilterDTO filter = new WorkflowUserFilterDTO();
            filter.setDeptList(new ArrayList<>(deptIds));
            CommonResult<List<SystemDepartmentDTO>> result = remoteUserService.findDeptListByIds(filter, "");
            if (result.getCode() == CommonResult.SUCCESS && result.getData() != null) {
                for (SystemDepartmentDTO dept : result.getData()) {
                    names.put(dept.getId(), dept.getDeptName());
                }
            }
        } catch (Exception e) {
            log.warn("打印批量查询部门名称失败 deptIds {}", deptIds, e);
        }
        return names;
    }

    private Set<String> loadSensitiveColumns(String formId) {
        Set<String> result = new HashSet<>();
        for (DynamicFormColumn column : formColumnMapper.selectList(
                Wrappers.<DynamicFormColumn>lambdaQuery().eq(DynamicFormColumn::getFormId, formId))) {
            if (SensitiveDataMask.needMask(column.getSensitive())
                    && StringUtils.isNotEmpty(column.getFiledCode())) {
                result.add(column.getFiledCode());
            }
        }
        return result;
    }

    private String loadSensitivePolicy(String formId) {
        com.privernal.dynamic.advanced.domain.DynamicFormIoConfig config = ioConfigMapper.selectOne(
                Wrappers.<com.privernal.dynamic.advanced.domain.DynamicFormIoConfig>lambdaQuery()
                        .eq(com.privernal.dynamic.advanced.domain.DynamicFormIoConfig::getFormId, formId));
        return config == null || StringUtils.isEmpty(config.getSensitivePolicy()) ? "MASK" : config.getSensitivePolicy();
    }

    private String camelCase(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }
}
