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

package com.privernal.dynamic.datav.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.dbs.util.DynamicTableAuthUtil;
import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.service.DynamicReportQueryService;
import com.privernal.dynamic.report.service.DynamicReportTableResolver;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大屏运行态取数服务。
 * <p>
 * 只负责「画布里的组件配置 → 报表聚合内核的一次调用」，不生成任何 SQL：
 * 维度/度量/过滤的字段校验、标识符白名单、值占位绑定、超时与行数上限全部由
 * {@link DynamicReportQueryService} 承担，大屏这层再实现一套就会有两份会漂移的注入闸门。
 * <p>
 * 鉴权三段：菜单权限（Controller 上的 datav:screen:query）→ 数据集底层表的
 * {@code dynamic:{table}:list} 按钮权限 → 行级范围由数据权限切面按查看者自动追加。
 * 缺第二段时，拿到某个大屏编码的人就能对任意数据集取数。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DynamicDatavDataService {

    /** 一次批量取数的组件上限：40 组件的屏首屏分两批拉，避免一次请求把连接占满 */
    public static final int MAX_BATCH_WIDGETS = 30;

    /** 慢查询阈值：内核超时是 10s，但那已经算故障；1s 就是「该建索引或该收敛维度」的信号 */
    private static final long SLOW_MILLIS = 1000L;

    /** 运行时过滤允许的操作符：单一真源在 {@link ComputeSpecs}，前端绑定面板的下拉选项与它同集合 */
    private static final Set<String> FILTER_OPS = ComputeSpecs.FILTER_OPS;

    @Autowired
    private DynamicReportQueryService reportQueryService;

    @Autowired
    private DatavResultCache resultCache;

    @Autowired
    private IDynamicDatasetService datasetService;

    @Autowired
    private DynamicReportTableResolver tableResolver;

    /** 并发闸门：给真正打库那段套上限，防止轮播突发把 Servlet 线程与连接池抽干（M6.6） */
    @Autowired
    private DatavQueryLimiter queryLimiter;

    /** 数据集熔断：坏数据集连续失败后短路冷却，不再被每块屏的每个组件反复打库（M6.6） */
    @Autowired
    private DatavQueryBreaker queryBreaker;

    /**
     * 单个组件取数
     *
     * @param database   组织业务库名（调用方从登录上下文取）
     * @param screen     已鉴权的大屏（含 schemaJson）
     * @param widgetId   组件 id
     * @param params     大屏运行时参数，可为空
     * @param drillLevel 下钻层级，0 或 null 表示不下钻；可下钻的字段只来自该组件 schema 里声明的下钻链
     */
    public Map<String, Object> queryWidget(String database, DynamicDatavScreen screen,
                                           String widgetId, JSONObject params, int drillLevel) {
        WidgetBinding binding = requireBinding(screen, widgetId);
        return runBinding(screen.getScreenCode(), database, binding, params,
                drillLevel, resultCache.newBatchScope());
    }

    /**
     * 整屏批量取数：逐组件串行执行且各自兜错，一个组件失败不会让整屏空白。
     * <p>
     * 串行而非并发是有意的：报表内核的行级数据权限靠当前线程的登录上下文拼装，
     * 丢进线程池会让切面拿不到用户，静默变成「不过滤」。
     *
     * @param drills widgetId → 下钻层级，只带当前处于下钻态的组件
     * @return widgetId → {success, data|error}
     */
    public Map<String, Object> queryScreen(String database, DynamicDatavScreen screen, JSONObject params,
                                           Map<String, Object> drills) {
        List<WidgetBinding> bindings = resolveBindings(screen.getSchemaJson());
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Map<String, Object>> batchScope = resultCache.newBatchScope();
        int count = 0;
        for (WidgetBinding binding : bindings) {
            if (count++ >= MAX_BATCH_WIDGETS) {
                items.put(binding.widgetId, envelope(false, null,
                        "单屏一次最多取 " + MAX_BATCH_WIDGETS + " 个组件，其余请分批按需拉取"));
                continue;
            }
            // 批次内去重：一块屏 30 个组件常常只有几种查询，同参的第二次不必再打库
            try {
                items.put(binding.widgetId, envelope(true, runBinding(screen.getScreenCode(), database, binding,
                        params, drillLevelOf(drills, binding.widgetId()), batchScope), null));
            } catch (ServiceException e) {
                // 鉴权失败（PreAuthorizeException 是 ServiceException 子类）与配置错误都只回该组件，
                // 既不能整屏 500，也不能把别人的可见性探出来——消息里只有组件与数据集层面的原因
                log.warn("大屏 {} 组件 {} 取数失败：{}", screen.getScreenCode(), binding.widgetId, e.getMessage());
                items.put(binding.widgetId, envelope(false, null, e.getMessage()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("screenCode", screen.getScreenCode());
        result.put("total", bindings.size());
        result.put("queried", Math.min(count, MAX_BATCH_WIDGETS));
        result.put("items", items);
        return result;
    }

    /**
     * 免登外链取数（/pub/datav/**）：与 {@link #queryScreen} 的差别全部来自「没有登录用户」——
     * <ul>
     *   <li>不进结果缓存：缓存键含用户/部门维度（identityScope 刻意不兜底），免登线程要么炸要么
     *       退化成跨用户共享键；外链场景宁可每轮直查（熔断与闸门照常生效，这才是公网流量最需要的）</li>
     *   <li>跳过按钮权限（dynamic:{table}:list）：分享由持有 datav:screen:edit 的人创建，
     *       访问者的权限就是「这条链接本身」；数据范围由 OrganizationContextHolder 的组织谓词限定
     *       （分享所属组织，部门行级范围不生效——与 OpenApi 机器调用同口径）</li>
     *   <li>绑定/下钻/参数过滤与登录态完全同一套静态纯函数：行为不会分叉</li>
     * </ul>
     * 调用方（分享控制器）必须已通过 resolveShare 校验并设置好组织上下文。
     */
    public Map<String, Object> queryScreenAsGuest(String database, DynamicDatavScreen screen, JSONObject params,
                                                  Map<String, Object> drills) {
        List<WidgetBinding> bindings = resolveBindings(screen.getSchemaJson());
        Map<String, Object> items = new LinkedHashMap<>();
        int count = 0;
        for (WidgetBinding binding : bindings) {
            if (count++ >= MAX_BATCH_WIDGETS) {
                items.put(binding.widgetId, envelope(false, null,
                        "单屏一次最多取 " + MAX_BATCH_WIDGETS + " 个组件，其余请分批按需拉取"));
                continue;
            }
            try {
                items.put(binding.widgetId, envelope(true, runBindingAsGuest(screen.getScreenCode(), database,
                        binding, params, drillLevelOf(drills, binding.widgetId())), null));
            } catch (ServiceException e) {
                log.warn("外链大屏 {} 组件 {} 取数失败：{}", screen.getScreenCode(), binding.widgetId, e.getMessage());
                items.put(binding.widgetId, envelope(false, null, e.getMessage()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("screenCode", screen.getScreenCode());
        result.put("total", bindings.size());
        result.put("queried", Math.min(count, MAX_BATCH_WIDGETS));
        result.put("items", items);
        return result;
    }

    /** runBinding 的免登变体：同样的绑定语义，无缓存、无按钮权限、保留熔断与闸门 */
    private Map<String, Object> runBindingAsGuest(String screenCode, String database, WidgetBinding binding,
                                                    JSONObject params, int drillLevel) {
        DynamicDataset dataset = datasetService.findByCode(binding.datasetCode);
        if (dataset == null) {
            throw new ServiceException("组件 " + binding.widgetId + " 的数据集不存在：" + binding.datasetCode);
        }
        JSONObject config = applyDrillLevel(binding.reportConfig == null
                ? new JSONObject() : binding.reportConfig, binding.drillFields(), drillLevel);
        JSONArray runtimeFilters = buildRuntimeFilters(binding.paramBindings, params);
        long startedAt = System.currentTimeMillis();
        Map<String, Object> result = DatavQueryPipeline.execute(queryLimiter, queryBreaker, binding.datasetCode,
                () -> reportQueryService.preview(database, dataset, config, runtimeFilters));
        long costMillis = System.currentTimeMillis() - startedAt;
        if (costMillis >= SLOW_MILLIS) {
            log.warn("外链大屏取数偏慢：screen={} widget={} dataset={} cost={}ms",
                    screenCode, binding.widgetId, binding.datasetCode, costMillis);
        }
        return result;
    }

    /**
     * 取组件绑定：找不到即报错，不允许「静默返回空数据」——那会让画布上的错别字组件 id
     * 表现为图表永远空白，排查成本远高于一次明确报错。
     */
    private WidgetBinding requireBinding(DynamicDatavScreen screen, String widgetId) {
        for (WidgetBinding binding : resolveBindings(screen.getSchemaJson())) {
            if (binding.widgetId.equals(widgetId)) {
                return binding;
            }
        }
        throw new ServiceException("大屏 " + screen.getScreenCode() + " 上没有取数组件 " + widgetId
                + "（组件未绑定数据集、已停用，或 id 不属于当前画布）");
    }

    private Map<String, Object> runBinding(String screenCode, String database, WidgetBinding binding,
                                             JSONObject params, int drillLevel,
                                             Map<String, Map<String, Object>> batchScope) {
        DynamicDataset dataset = datasetService.findByCode(binding.datasetCode);
        if (dataset == null) {
            throw new ServiceException("组件 " + binding.widgetId + " 的数据集不存在：" + binding.datasetCode);
        }
        requireTablePermission(dataset);

        JSONObject config = applyDrillLevel(binding.reportConfig == null
                ? new JSONObject() : binding.reportConfig, binding.drillFields(), drillLevel);
        JSONArray runtimeFilters = buildRuntimeFilters(binding.paramBindings, params);

        // 鉴权在上面、缓存在下面：命中缓存也不能跳过按钮权限，否则缓存就成了越权读取的旁路
        String cacheKey = resultCache.buildKey(database, binding.datasetCode, config, runtimeFilters);
        Map<String, Object> hit = resultCache.batchGet(batchScope, cacheKey);
        if (hit == null) {
            hit = resultCache.load(cacheKey);
        }
        if (hit != null) {
            resultCache.batchPut(batchScope, cacheKey, hit);
            return hit;
        }

        long startedAt = System.currentTimeMillis();
        Map<String, Object> result;
        try {
            // 顺序（闸门在外、熔断在内）收在 DatavQueryPipeline 里，理由见该类注释
            result = DatavQueryPipeline.execute(queryLimiter, queryBreaker, binding.datasetCode,
                    () -> reportQueryService.preview(database, dataset, config, runtimeFilters));
        } catch (ServiceException e) {
            // 源站抖动时宁可回上一次的快照并标 stale，也不要让整块屏变成空白
            Map<String, Object> stale = resultCache.loadStale(cacheKey);
            if (stale != null) {
                log.warn("大屏取数失败，已回退到上次快照：screen={} widget={} dataset={} reason={}",
                        screenCode, binding.widgetId, binding.datasetCode, e.getMessage());
                Map<String, Object> degraded = new LinkedHashMap<>(stale);
                degraded.put("stale", true);
                resultCache.batchPut(batchScope, cacheKey, degraded);
                return degraded;
            }
            throw e;
        }
        resultCache.batchPut(batchScope, cacheKey, result);
        long costMillis = System.currentTimeMillis() - startedAt;
        if (costMillis >= SLOW_MILLIS) {
            // 投放屏每 30s 刷一次，一条 1s 的查询就是全天常驻负载；内核只在超时时限后报错过，
            // 这里补一条「慢但没失败」的痕迹，否则运维只能看到大屏卡顿而查不到是哪块屏
            log.warn("大屏取数偏慢：screen={} widget={} dataset={} cost={}ms rows={}",
                    screenCode, binding.widgetId, binding.datasetCode, costMillis, rowCountOf(result));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private int rowCountOf(Map<String, Object> result) {
        Object rows = result.get("rows");
        return rows instanceof List ? ((List<Object>) rows).size() : 0;
    }

    /**
     * 与报表数据接口同一口径：查看者必须对数据集底层动态表有 list 按钮权限。
     * 表名解析不到时跳过按钮校验，交给聚合查询报「表不存在」，既不误放行也不误拒绝。
     */
    private void requireTablePermission(DynamicDataset dataset) {
        String physicalTable = tableResolver.resolvePhysicalTableCode(dataset.getTableCode());
        if (physicalTable == null) {
            return;
        }
        DynamicTableAuthUtil.requirePermission(physicalTable, "list");
    }

    private Map<String, Object> envelope(boolean success, Map<String, Object> data, String error) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("success", success);
        if (data != null) {
            item.put("data", data);
        }
        if (error != null) {
            item.put("error", error);
        }
        return item;
    }

    /**
     * 画布 JSON → 需要取数的组件清单（保持画布顺序，前端首屏才能按视觉优先级填充）。
     * 纯函数：不涉及数据库与登录上下文，可直接单测。
     */
    public static List<WidgetBinding> resolveBindings(JSONObject schema) {
        List<WidgetBinding> bindings = new ArrayList<>();
        if (schema == null) {
            return bindings;
        }
        JSONArray widgets = schema.getJSONArray("widgets");
        if (widgets == null) {
            return bindings;
        }
        for (int i = 0; i < widgets.size(); i++) {
            JSONObject widget = widgets.getJSONObject(i);
            if (widget == null) {
                continue;
            }
            String widgetId = widget.getString("id");
            JSONObject data = widget.getJSONObject("data");
            if (StringUtils.isBlank(widgetId) || data == null) {
                continue;
            }
            if (!"dataset".equals(data.getString("mode"))) {
                continue;
            }
            String datasetCode = StringUtils.trimToEmpty(data.getString("datasetCode"));
            if (StringUtils.isBlank(datasetCode)) {
                log.warn("组件 {} 声明 dataset 模式但没有数据集编码，已跳过取数", widgetId);
                continue;
            }
            bindings.add(new WidgetBinding(widgetId, StringUtils.trimToEmpty(widget.getString("type")),
                    datasetCode, data.getJSONObject("reportConfig"), data.getJSONArray("paramBindings"),
                    data.getJSONArray("drillFields")));
        }
        return bindings;
    }

    /**
     * 批量请求里的下钻层级：只认数字与数字字符串，其余（缺键、null、脏值）一律按 0 层处理。
     * <p>
     * 刻意不因脏输入报错——投放页每 30s 自动刷新，一次前端状态残留不该让整块屏少一个组件。
     */
    static int drillLevelOf(Map<String, Object> drills, String widgetId) {
        Object raw = drills == null ? null : drills.get(widgetId);
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(raw.toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 按层级把顶层维度换成下钻链里的字段。
     * <p>
     * <b>可下钻的字段只来自组件保存时写进 schema 的 {@code data.drillFields}，请求方只能给一个下标。</b>
     * 这是取数契约「运行态不接受任意配置」的一部分：一旦允许请求带字段名，
     * {@code datav:screen:query} 就成了数据集权限的旁路，任何人都能对着别人的表聚合。
     * <p>
     * 只换 {@code field}，不带 granularity/lookup/bucket：那三个都是原字段的属性，
     * 跟着换过去会出现「按月的班级」这种没人配过的口径。
     * 换维度必须发生在算缓存键之前，否则两个下钻层级会共用同一份缓存。
     *
     * @param drillFields 下钻链，第 0 项即组件配置的顶层维度（仅用于面包屑标签）
     * @param level       0 表示不下钻，原样返回（不复制、不改写，避免无谓的缓存键变化）
     */
    static JSONObject applyDrillLevel(JSONObject config, JSONArray drillFields, int level) {
        if (level <= 0) {
            return config;
        }
        if (drillFields == null || drillFields.isEmpty()) {
            throw new ServiceException("该组件未声明下钻链，却收到下钻层级 " + level);
        }
        if (level >= drillFields.size()) {
            throw new ServiceException("下钻层级超出该组件的下钻链（共 " + drillFields.size() + " 层）");
        }
        JSONArray dimensions = config.getJSONArray("dimensions");
        if (dimensions == null || dimensions.isEmpty()) {
            throw new ServiceException("该组件没有维度，无法下钻到第 " + level + " 层");
        }
        String field = StringUtils.trimToEmpty(drillFields.getString(level));
        if (StringUtils.isBlank(field)) {
            throw new ServiceException("该组件下钻链第 " + level + " 层字段为空");
        }
        // 浅拷贝 + 整体替换 dimensions：不能就地改，那份 config 就是 schema 里的对象本身
        JSONObject drilled = new JSONObject();
        drilled.putAll(config);
        JSONArray next = new JSONArray(dimensions.size());
        JSONObject dim = new JSONObject();
        dim.put("field", field);
        next.add(dim);
        for (int i = 1; i < dimensions.size(); i++) {
            next.add(dimensions.get(i));
        }
        drilled.put("dimensions", next);
        return drilled;
    }

    /**
     * 大屏参数 → 报表运行时过滤条件。
     * <p>
     * 参数缺失默认是**错误**而不是「不加这个过滤」：设计者把某个字段配成条件，通常就是
     * 组织/租户范围，缺值时放行等于把全表聚合出去。确实要可选的条件请显式写 {@code required:false}。
     *
     * @return 可能为空数组；无绑定时返回空数组（内核按空过滤处理）
     */
    public static JSONArray buildRuntimeFilters(JSONArray paramBindings, JSONObject params) {
        JSONArray filters = new JSONArray();
        if (paramBindings == null || paramBindings.isEmpty()) {
            return filters;
        }
        for (int i = 0; i < paramBindings.size(); i++) {
            JSONObject binding = paramBindings.getJSONObject(i);
            if (binding == null) {
                continue;
            }
            String param = StringUtils.trimToEmpty(binding.getString("param"));
            String field = StringUtils.trimToEmpty(binding.getString("field"));
            if (StringUtils.isBlank(param) || StringUtils.isBlank(field)) {
                throw new ServiceException("参数绑定缺少 param 或 field：" + binding);
            }
            String op = StringUtils.isBlank(binding.getString("op")) ? "eq" : binding.getString("op").toLowerCase();
            if (!FILTER_OPS.contains(op)) {
                throw new ServiceException("不支持的过滤操作符：" + op + "（可用 eq/ne/like/gt/ge/lt/le）");
            }
            Object value = params == null ? null : params.get(param);
            if (isEmptyValue(value)) {
                if (Boolean.FALSE.equals(binding.getBoolean("required"))) {
                    continue;
                }
                throw new ServiceException("缺少大屏参数：" + param + "（条件字段 " + field + "）");
            }
            if (value instanceof JSONObject || value instanceof JSONArray) {
                throw new ServiceException("大屏参数 " + param + " 必须是单值，不支持对象或数组");
            }
            JSONObject filter = new JSONObject();
            filter.put("field", field);
            filter.put("op", op);
            filter.put("value", value);
            filters.add(filter);
        }
        return filters;
    }

    private static boolean isEmptyValue(Object value) {
        return value == null || (value instanceof String text && StringUtils.isBlank(text));
    }

    /**
     * 一个需要取数的组件：只保留后端用得上的字段，样式与布局留在前端。
     */
    public record WidgetBinding(String widgetId, String widgetType, String datasetCode,
                                JSONObject reportConfig, JSONArray paramBindings, JSONArray drillFields) {
    }
}
