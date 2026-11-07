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

package com.privernal.apppackage.service;

import com.privernal.apppackage.dto.AppPackageDTO;
import com.privernal.apppackage.dto.AppPackageImportResultDTO;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.dto.DynamicFormDTO;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;
import com.privernal.flow.dto.FlowFormBtnPermDTO;
import com.privernal.flow.dto.FlowFormFieldPermDTO;
import com.privernal.flow.dto.FlowFormVersionDTO;
import com.privernal.flow.filter.FlowDefinitionFilterDTO;
import com.privernal.flow.service.IFlowDefinitionService;
import com.privernal.flow.service.IFlowFormVersionService;
import com.privernal.system.domain.SystemAppInfo;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.filter.SystemAppInfoFilterDTO;
import com.privernal.system.service.ISystemAppInfoService;
import com.privernal.common.security.service.SecurityUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 应用打包：把一个低代码应用（表单 + 列表配置 + 流程定义 + 节点权限）导出为可移植的应用包，
 * 并在另一个环境（独立部署的目标环境）导入重建。
 *
 * 导入全程复用平台的既有业务链路（saveSystemAppInfo / insertDynamicForm / saveDefJson /
 * initDraft+saveDraft+publish），而不是直接写表：菜单、按钮权限、物理表、组织授权、角色授权
 * 都由这些链路按目标环境自行生成，应用包因此不需要携带任何环境主键。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class AppPackageService {

    private static final String TYPE_APP = "app";
    private static final String TYPE_FORM = "form";
    private static final String TYPE_FLOW = "flow";
    private static final String TYPE_FLOW_FORM = "flowForm";
    private static final String TYPE_DATASET = "dataset";
    private static final String TYPE_REPORT = "report";
    private static final String TYPE_DASHBOARD = "dashboard";
    private static final String TYPE_SCHEDULE = "schedule";
    private static final String TYPE_DOC_TEMPLATE = "docTemplate";
    private static final String TYPE_DATAV_SCREEN = "datavScreen";
    private static final String TYPE_DATAV_ASSET = "datavAsset";

    /** warm-flow 发布状态：1 已发布 */
    private static final Integer PUBLISHED = 1;
    /** flow_form_version 发布状态：0 草稿 / 1 已发布 */
    private static final Integer VERSION_DRAFT = 0;
    private static final Integer VERSION_PUBLISHED = 1;
    private static final String CURRENT_FLAG_YES = "Y";

    /** 动态表单启用流程标记 */
    private static final Long WORKFLOW_ON = 1L;

    @Resource
    private ISystemAppInfoService systemAppInfoService;
    @Resource
    private IDynamicFormService dynamicFormService;
    @Resource
    private IDynamicFormColumnService dynamicFormColumnService;
    @Resource
    private IFlowDefinitionService flowDefinitionService;
    @Resource
    private IFlowFormVersionService flowFormVersionService;
    @Resource
    private com.privernal.dynamic.report.service.IDynamicDatasetService datasetService;
    @Resource
    private com.privernal.dynamic.report.service.IDynamicReportService reportService;
    @Resource
    private com.privernal.dynamic.report.service.IDynamicDashboardService dashboardService;
    @Resource
    private com.privernal.job.api.ScheduleTaskClient scheduleTaskClient;
    @Resource
    private com.privernal.job.service.ScheduleTaskService scheduleTaskService;
    @Resource
    private com.privernal.job.mapper.ScheduleTaskMapper scheduleTaskMapper;
    @Resource
    private com.privernal.dynamic.advanced.mapper.DynamicDocTemplateMapper docTemplateMapper;
    @Resource
    private com.privernal.dynamic.report.service.DynamicReportTableResolver reportTableResolver;
    @Resource
    private com.privernal.dynamic.datav.service.IDynamicDatavScreenService datavScreenService;
    @Resource
    private com.privernal.dynamic.datav.service.IDynamicDatavAssetService datavAssetService;


    /**
     * 导出应用包
     */
    public AppPackageDTO exportApp(String appId) {
        SystemAppInfo appInfo = systemAppInfoService.selectSystemAppInfoById(appId);
        Assert.notNull(appInfo, "应用不存在：" + appId);

        AppPackageDTO pkg = new AppPackageDTO();
        pkg.setExportedAt(LocalDateTime.now().toString());
        pkg.getApp().setCode(appInfo.getCode());
        pkg.getApp().setAppName(appInfo.getAppName());
        pkg.getApp().setIcon(appInfo.getIcon());
        pkg.getApp().setType(appInfo.getType());
        pkg.getApp().setPcPath(appInfo.getPcPath());
        pkg.getApp().setAppPath(appInfo.getAppPath());
        pkg.getApp().setStatus(appInfo.getStatus());
        pkg.getApp().setSort(appInfo.getSort());

        DynamicFormFilterDTO formFilter = new DynamicFormFilterDTO();
        formFilter.setAppId(appId);
        List<DynamicFormDTO> forms = dynamicFormService.selectDynamicFormList(formFilter);
        // 导出逐项兜底（§9.1 第6条）：一个坏资产降级为跳过+日志，不再让整包导出失败
        for (DynamicFormDTO form : forms) {
            try {
                pkg.getForms().add(exportForm(form));
            } catch (Exception e) {
                log.warn("导出表单失败，已跳过 tableCode={}", form.getTableCode(), e);
            }
            if (WORKFLOW_ON.equals(form.getIsWorkflow()) && StringUtils.hasText(form.getWorkflowCode())) {
                try {
                    AppPackageDTO.Flow flow = exportFlow(form.getWorkflowCode(), form.getTableCode(), form.getTableName());
                    if (flow != null) {
                        pkg.getFlows().add(flow);
                    }
                } catch (Exception e) {
                    log.warn("导出流程失败，已跳过 flowCode={}", form.getWorkflowCode(), e);
                }
            }
        }
        try {
            exportReportAssets(appId, pkg);
        } catch (Exception e) {
            log.warn("导出报表资产失败，已跳过该段 appId={}", appId, e);
        }
        try {
            exportDatavAssets(appId, pkg);
        } catch (Exception e) {
            log.warn("导出大屏资产失败，已跳过该段 appId={}", appId, e);
        }
        exportScheduleTasks(appId, pkg);
        try {
            exportDocTemplates(appId, pkg, forms);
        } catch (Exception e) {
            log.warn("导出单据打印模板失败，已跳过该段 appId={}", appId, e);
        }
        return pkg;
    }

    /**
     * 导出单据打印模板（1.3 起，plan-sheet-print §3.7）：按 app_id 锚定，
     * 历史/手工建的模板可能没写 app_id，兜底按表单归属（form_id ∈ 应用表单）一并带走。
     * 判重键 template_code（稳定编码）；content_html 原样入包（HTML 与组织无关）。
     */
    private void exportDocTemplates(String appId, AppPackageDTO pkg, List<DynamicFormDTO> forms) {
        List<String> formIds = forms.stream().map(DynamicFormDTO::getId)
                .filter(StringUtils::hasText).collect(java.util.stream.Collectors.toList());
        List<com.privernal.dynamic.advanced.domain.DynamicDocTemplate> templates = docTemplateMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.privernal.dynamic.advanced.domain.DynamicDocTemplate>()
                        .eq(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getAppId, appId)
                        .or(formIds != null && !formIds.isEmpty(),
                                w -> w.in(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getFormId, formIds)));
        Map<String, String> formIdToCode = forms.stream().collect(
                java.util.stream.Collectors.toMap(DynamicFormDTO::getId, DynamicFormDTO::getTableCode, (a, b) -> a));
        for (com.privernal.dynamic.advanced.domain.DynamicDocTemplate tpl : templates) {
            try {
                AppPackageDTO.DocTemplate item = new AppPackageDTO.DocTemplate();
                item.setTemplateCode(tpl.getTemplateCode());
                item.setName(tpl.getName());
                String formTableCode = formIdToCode.get(tpl.getFormId());
                if (formTableCode == null) {
                    com.privernal.dynamic.dbs.domain.DynamicForm form = dynamicFormService.selectDynamicFormById(tpl.getFormId());
                    formTableCode = form == null ? null : form.getTableCode();
                }
                item.setFormTableCode(formTableCode);
                item.setSourceType(tpl.getSourceType());
                item.setSourceCode(tpl.getSourceCode());
                item.setTemplateType(tpl.getTemplateType());
                item.setStatus(tpl.getStatus());
                item.setContentHtml(tpl.getContentHtml());
                item.setBindingJson(tpl.getBindingJson());
                item.setParamsJson(tpl.getParamsJson());
                item.setPrintConfig(tpl.getPrintConfig());
                item.setContentSheet(tpl.getContentSheet());
                item.setIsDefault(tpl.getIsDefault());
                item.setSort(tpl.getSort());
                pkg.getDocTemplates().add(item);
            } catch (Exception e) {
                log.warn("导出单个单据模板失败，已跳过 templateCode={}", tpl.getTemplateCode(), e);
            }
        }
    }

    /**
 * 导出设计器定时任务（1.2 起）：DESIGNER/DESIGNER_DATA 来源随应用包迁移。
     * 只带配置面——调度状态列（id/status/next_fire_time/owner/lease/running/计数/version）全部剥离，
     * next_fire_time 由导入端注册时刻重算。
     */
    private void exportScheduleTasks(String appId, AppPackageDTO pkg) {
        for (String source : new String[]{"DESIGNER", "DESIGNER_DATA"}) {
            try {
                com.privernal.job.filter.ScheduleTaskFilterDTO filter = new com.privernal.job.filter.ScheduleTaskFilterDTO();
                filter.setAppId(appId);
                filter.setSource(source);
                filter.setPageSize(1000);
                for (com.privernal.job.domain.SystemScheduleTask task : scheduleTaskMapper.selectScheduleTaskList(filter)) {
                    AppPackageDTO.ScheduleTaskPackage item = new AppPackageDTO.ScheduleTaskPackage();
                    item.setTaskCode(task.getTaskCode());
                    item.setTitle(task.getTitle());
                    item.setSource(task.getSource());
                    item.setBizKey(task.getBizKey());
                    item.setTriggerType(task.getTriggerType());
                    item.setCronExpr(task.getCronExpr());
                    item.setIntervalSeconds(task.getIntervalSeconds());
                    item.setJitterSeconds(task.getJitterSeconds());
                    item.setMisfirePolicy(task.getMisfirePolicy());
                    item.setMisfireWindowSeconds(task.getMisfireWindowSeconds());
                    item.setConcurrentPolicy(task.getConcurrentPolicy());
                    item.setTimeoutSeconds(task.getTimeoutSeconds());
                    item.setMaxRetry(task.getMaxRetry());
                    item.setActionType(task.getActionType());
                    item.setActionConfig(task.getActionConfig());
                    item.setBizContext(task.getBizContext());
                    item.setFireTime(task.getNextFireTime());
                    item.setRemark(task.getRemark());
                    pkg.getScheduleTasks().add(item);
                }
            } catch (Exception e) {
                log.warn("导出定时任务失败，已跳过该段 appId={} source={}", appId, source, e);
            }
        }
    }

    /**
     * 导出报表资产（数据集/图表/仪表盘，1.1 起）。只存逻辑 tableCode 与 code，主键不入包。
     */
    private void exportReportAssets(String appId, AppPackageDTO pkg) {
        for (com.privernal.dynamic.report.domain.DynamicDataset dataset : datasetService.listByApp(appId)) {
            AppPackageDTO.Dataset item = new AppPackageDTO.Dataset();
            item.setDatasetCode(dataset.getDatasetCode());
            item.setDatasetName(dataset.getDatasetName());
            item.setTableCode(dataset.getTableCode());
            item.setColumnSnapshot(dataset.getColumnSnapshot());
            item.setDefaultFilter(dataset.getDefaultFilter());
            // 多源四列（1.4）：sourceConfig 剥离凭据密文——主密钥跨环境不同，密文在新环境
            // 既解不开也不该离开源环境；凭据键缺失即「未配凭据」，导入后重配即可
            item.setSourceType(dataset.getSourceType());
            item.setSourceConfig(stripCiphers(dataset.getSourceConfig()));
            item.setRefreshConfig(dataset.getRefreshConfig());
            item.setComputedColumns(dataset.getComputedColumns());
            pkg.getDatasets().add(item);
        }
        for (com.privernal.dynamic.report.domain.DynamicReport report : reportService.listByApp(appId)) {
            AppPackageDTO.ReportAsset item = new AppPackageDTO.ReportAsset();
            item.setReportCode(report.getReportCode());
            item.setReportName(report.getReportName());
            item.setReportType(report.getReportType());
            item.setDatasetCode(report.getDatasetCode());
            item.setConfig(report.getConfig());
            pkg.getReports().add(item);
        }
        for (com.privernal.dynamic.report.domain.DynamicDashboard dashboard : dashboardService.listByApp(appId)) {
            AppPackageDTO.Dashboard item = new AppPackageDTO.Dashboard();
            item.setDashboardCode(dashboard.getDashboardCode());
            item.setDashboardName(dashboard.getDashboardName());
            item.setConfig(dashboard.getConfig());
            com.privernal.dynamic.report.service.IDynamicDashboardService.DashboardDetail detail =
                    dashboardService.findDetail(dashboard.getDashboardCode());
            if (detail != null) {
                for (com.privernal.dynamic.report.domain.DynamicDashboardItem card : detail.getItems()) {
                    AppPackageDTO.DashboardItem cardDto = new AppPackageDTO.DashboardItem();
                    cardDto.setReportCode(card.getReportCode());
                    cardDto.setTitle(card.getTitle());
                    cardDto.setSpan(card.getSpan());
                    cardDto.setOrderNum(card.getOrderNum());
                    cardDto.setConfig(card.getConfig());
                    item.getItems().add(cardDto);
                }
            }
            pkg.getDashboards().add(item);
        }
    }

    /**
     * 导出大屏资产（1.4 起）：屏 schema 原样入包（版本/菜单/快照由导入链路在目标环境重建）；
     * 素材只带「被导出屏引用的」行——素材表没有 appId 锚点，按引用筛选而不是整组织导出。
     */
    private void exportDatavAssets(String appId, AppPackageDTO pkg) {
        List<com.privernal.dynamic.datav.domain.DynamicDatavScreen> screens = datavScreenService.listScreens(appId);
        StringBuilder schemaText = new StringBuilder();
        for (com.privernal.dynamic.datav.domain.DynamicDatavScreen screen : screens) {
            AppPackageDTO.DatavScreen item = new AppPackageDTO.DatavScreen();
            item.setScreenCode(screen.getScreenCode());
            item.setScreenName(screen.getScreenName());
            item.setLayoutMode(screen.getLayoutMode());
            item.setSchemaJson(screen.getSchemaJson() == null ? null : screen.getSchemaJson().toJSONString());
            pkg.getScreens().add(item);
            if (item.getSchemaJson() != null) {
                schemaText.append(item.getSchemaJson()).append('\n');
            }
        }
        if (schemaText.length() == 0) {
            return;
        }
        for (com.privernal.dynamic.datav.domain.DynamicDatavAsset asset : datavAssetService.listAssets(null, null)) {
            if (asset.getFileUrl() == null || !schemaText.toString().contains(asset.getFileUrl())) {
                continue;
            }
            AppPackageDTO.DatavAsset item = new AppPackageDTO.DatavAsset();
            item.setAssetCode(asset.getAssetCode());
            item.setAssetName(asset.getAssetName());
            item.setCategory(asset.getCategory());
            item.setAssetType(asset.getAssetType());
            item.setFileUrl(asset.getFileUrl());
            item.setOriginalName(asset.getOriginalName());
            item.setFileSize(asset.getFileSize());
            pkg.getDatavAssets().add(item);
        }
    }

    /** 剥离 sourceConfig 里的凭据密文键（headerCiphers/passwordCipher 与掩码值），返回浅拷贝不动原对象 */
    private JSONObject stripCiphers(JSONObject sourceConfig) {
        if (sourceConfig == null) {
            return null;
        }
        JSONObject portable = new JSONObject(sourceConfig);
        portable.remove("headerCiphers");
        portable.remove("passwordCipher");
        JSONObject headers = portable.getJSONObject("headers");
        if (headers != null) {
            // 掩码值（********）在源环境也只是「没改凭据」的占位，带着走没有意义
            JSONObject cleaned = new JSONObject(headers);
            cleaned.replaceAll((key, value) -> String.valueOf(value).contains("****") ? null : value);
            portable.put("headers", cleaned);
        }
        if (String.valueOf(portable.get("password")).contains("****")) {
            portable.remove("password");
        }
        return portable;
    }

    private AppPackageDTO.Form exportForm(DynamicFormDTO form) {
        AppPackageDTO.Form item = new AppPackageDTO.Form();
        item.setTableCode(form.getTableCode());
        item.setTableName(form.getTableName());
        item.setIsWorkflow(form.getIsWorkflow());
        item.setWorkflowCode(form.getWorkflowCode());
        item.setMetaData(form.getMetaData());
        for (DynamicFormColumn column : dynamicFormColumnService.selectDynamicFormColumnByFormId(form.getId())) {
            AppPackageDTO.Column col = new AppPackageDTO.Column();
            col.setFiledCode(column.getFiledCode());
            col.setFiledName(column.getFiledName());
            col.setFiledType(column.getFiledType());
            col.setLength(column.getLength());
            col.setScale(column.getScale());
            col.setSort(column.getSort());
            col.setDefaultValue(column.getDefaultValue());
            col.setQueryDefaultValue(column.getQueryDefaultValue());
            col.setOrderDir(column.getOrderDir());
            col.setShowList(column.getShowList());
            col.setShowQuery(column.getShowQuery());
            col.setSensitive(column.getSensitive());
            col.setUniqueFlag(column.getUniqueFlag());
            item.getColumns().add(col);
        }
        return item;
    }

    private AppPackageDTO.Flow exportFlow(String flowCode, String tableCode, String formName) {
        FlowDefinitionFilterDTO defFilter = new FlowDefinitionFilterDTO();
        defFilter.setFlowCode(flowCode);
        defFilter.setPageNum(1);
        defFilter.setPageSize(100);
        Definition definition = pickDefinition(flowDefinitionService.pageDefinitions(defFilter).getRecords());
        if (definition == null) {
            log.warn("流程编码 {} 在源环境没有可用定义，应用包不携带流程", flowCode);
            return null;
        }

        AppPackageDTO.Flow flow = new AppPackageDTO.Flow();
        flow.setFlowCode(flowCode);
        flow.setFlowName(definition.getFlowName());
        flow.setDefinition(toPortableDefJson(flowDefinitionService.exportJson(definition.getId())));

        FlowFormVersionDTO published = pickCurrentPublished(
                flowFormVersionService.selectVersionList(flowCode, tableCode, VERSION_PUBLISHED));
        if (published != null) {
            flow.getFormVersions().add(toPackageFormVersion(published, tableCode, formName));
        }
        return flow;
    }

    /**
     * 导出物去掉源环境主键与版本槽位：warm-flow 的 saveDef 见到非空 id 会按主键覆盖该定义并清空
     * 其全部节点与连线，跨环境 id 相同即会毁掉目标环境的既有流程。
     */
    private JSONObject toPortableDefJson(String exported) {
        Assert.hasText(exported, "流程定义导出内容为空");
        JSONObject defJson = JSON.parseObject(exported);
        defJson.remove("id");
        defJson.remove("version");
        defJson.remove("isPublish");
        return defJson;
    }

    private AppPackageDTO.FormVersion toPackageFormVersion(FlowFormVersionDTO detail, String tableCode, String formName) {
        AppPackageDTO.FormVersion version = new AppPackageDTO.FormVersion();
        version.setTableCode(tableCode);
        version.setFormName(StringUtils.hasText(detail.getFormName()) ? detail.getFormName() : formName);
        if (detail.getFieldPermList() != null) {
            for (FlowFormFieldPermDTO field : detail.getFieldPermList()) {
                AppPackageDTO.FieldPerm item = new AppPackageDTO.FieldPerm();
                item.setNodeCode(field.getNodeCode());
                item.setFieldCode(field.getFieldCode());
                item.setFieldName(field.getFieldName());
                item.setIsShow(field.getIsShow());
                item.setIsEdit(field.getIsEdit());
                item.setIsRequired(field.getIsRequired());
                item.setDefaultType(field.getDefaultType());
                item.setDefaultValue(field.getDefaultValue());
                item.setDictType(field.getDictType());
                item.setSort(field.getSort());
                version.getFields().add(item);
            }
        }
        if (detail.getBtnPermList() != null) {
            for (FlowFormBtnPermDTO btn : detail.getBtnPermList()) {
                AppPackageDTO.BtnPerm item = new AppPackageDTO.BtnPerm();
                item.setNodeCode(btn.getNodeCode());
                item.setBtnCode(btn.getBtnCode());
                item.setBtnName(btn.getBtnName());
                item.setAliasName(btn.getAliasName());
                item.setIsShow(btn.getIsShow());
                item.setShowDialog(btn.getShowDialog());
                item.setColor(btn.getColor());
                item.setSort(btn.getSort());
                version.getButtons().add(item);
            }
        }
        return version;
    }

    /**
     * 导入应用包。
     *
     * 逐项独立执行且不整体回滚：动态表单落库会执行建表/补列的 DDL，DDL 本身不可回滚，
     * 事务包裹只会掩盖已经发生的改动。每项的成败都记入返回报告。
     */
    public AppPackageImportResultDTO importApp(AppPackageDTO pkg, boolean overwrite) {
        Assert.notNull(pkg, "应用包内容为空");
        Assert.isTrue(AppPackageDTO.SUPPORTED_VERSIONS.contains(pkg.getSpecVersion()),
                "不支持的应用包结构版本：" + pkg.getSpecVersion() + "（支持 " + AppPackageDTO.SUPPORTED_VERSIONS + "）");
        Assert.hasText(pkg.getApp().getCode(), "应用包缺少应用编码");

        AppPackageImportResultDTO result = new AppPackageImportResultDTO();
        String appId = importAppInfo(pkg.getApp(), result);

        for (AppPackageDTO.Form form : pkg.getForms()) {
            importForm(appId, form, overwrite, result);
        }
        for (AppPackageDTO.Flow flow : pkg.getFlows()) {
            importFlow(flow, overwrite, result);
        }
        // 报表资产（1.1 起；1.0 包为空列表）：数据集 → 图表 → 仪表盘，依赖顺序不可换
        for (AppPackageDTO.Dataset dataset : pkg.getDatasets()) {
            importDataset(appId, dataset, overwrite, result);
        }
        for (AppPackageDTO.ReportAsset report : pkg.getReports()) {
            importReportAsset(appId, report, overwrite, result);
        }
        for (AppPackageDTO.Dashboard dashboard : pkg.getDashboards()) {
            importDashboard(appId, dashboard, overwrite, result);
        }
        // 大屏与素材（1.4 起）：屏在数据集之后导入（绑定引用数据集编码，纯字符串引用，
        // 数据集缺失只影响取数不影响建屏）；素材行幂等登记，file_url 可达性由部署侧保证
        for (AppPackageDTO.DatavScreen screen : pkg.getScreens()) {
            importDatavScreen(appId, screen, overwrite, result);
        }
        for (AppPackageDTO.DatavAsset asset : pkg.getDatavAssets()) {
            importDatavAsset(appId, asset, overwrite, result);
        }
        // 设计器定时任务（1.2 起）：判重按 (appId, task_code)；next_fire_time 由注册时刻重算
        for (AppPackageDTO.ScheduleTaskPackage task : pkg.getScheduleTasks()) {
            importScheduleTask(appId, task, overwrite, result);
        }
        // 单据打印模板（1.3 起）：判重按 template_code；form_id 按表单编码重挂，app_id 保留（B3）
        for (AppPackageDTO.DocTemplate tpl : pkg.getDocTemplates()) {
            importDocTemplate(appId, tpl, overwrite, result);
        }
        return result;
    }

    /**
     * 导入单据打印模板（plan-sheet-print §3.7）：
     * 判重键 template_code（稳定编码，跨环境不变）；content_html 原样落库；
     * form_id 按表单编码重挂新环境；app_id 必须置为导入应用（丢 appId 是已知复发缺陷 B3）。
     * 幂等：重导同码模板 = 覆盖更新（overwrite 开启时），不产生重复行。
     */
    private void importDocTemplate(String appId, AppPackageDTO.DocTemplate tpl, boolean overwrite,
                                   AppPackageImportResultDTO result) {
        String code = tpl.getTemplateCode();
        if (!StringUtils.hasText(code)) {
            result.add(TYPE_DOC_TEMPLATE, tpl.getName(), AppPackageImportResultDTO.FAILED, "模板缺少稳定编码 templateCode");
            return;
        }
        try {
            // P2-3 同口径（task-upgrade，2026-10-06）：formTableCode 是源环境物理表名，
            // 严格解析到目标环境表单；解析不出维持"跳过 + 导入报告"
            com.privernal.dynamic.dbs.domain.DynamicForm form = reportTableResolver.resolveFormStrict(tpl.getFormTableCode());
            if (form == null) {
                result.add(TYPE_DOC_TEMPLATE, code, AppPackageImportResultDTO.SKIPPED,
                        "模板关联表单在目标环境不存在：" + tpl.getFormTableCode());
                return;
            }
            com.privernal.dynamic.advanced.domain.DynamicDocTemplate existing = docTemplateMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.privernal.dynamic.advanced.domain.DynamicDocTemplate>()
                            .eq(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getTemplateCode, code)
                            .orderByAsc(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getCreatedTime)
                            .last("limit 1"));
            boolean creating = existing == null;
            if (!creating && !overwrite) {
                result.add(TYPE_DOC_TEMPLATE, code, AppPackageImportResultDTO.SKIPPED, "模板已存在，未开启覆盖");
                return;
            }
            com.privernal.dynamic.advanced.domain.DynamicDocTemplate entity = creating
                    ? new com.privernal.dynamic.advanced.domain.DynamicDocTemplate()
                    : existing;
            entity.setTemplateCode(code);
            entity.setName(tpl.getName());
            entity.setAppId(appId);
            entity.setFormId(form.getId());
            entity.setSourceType(StringUtils.hasText(tpl.getSourceType()) ? tpl.getSourceType() : "FORM");
            entity.setSourceCode(tpl.getSourceCode());
            entity.setTemplateType(StringUtils.hasText(tpl.getTemplateType()) ? tpl.getTemplateType() : "DOC");
            entity.setStatus(StringUtils.hasText(tpl.getStatus()) ? tpl.getStatus() : "1");
            entity.setContentHtml(tpl.getContentHtml());
            entity.setContentSheet(tpl.getContentSheet());
            entity.setBindingJson(tpl.getBindingJson());
            entity.setParamsJson(tpl.getParamsJson());
            entity.setPrintConfig(tpl.getPrintConfig());
            entity.setIsDefault(StringUtils.hasText(tpl.getIsDefault()) ? tpl.getIsDefault() : "N");
            entity.setSort(tpl.getSort() == null ? 0 : tpl.getSort());
            if (creating) {
                entity.setOrganizationId(form.getOrganizationId());
                entity.setDepartmentId(form.getDepartmentId());
                docTemplateMapper.insert(entity);
                result.add(TYPE_DOC_TEMPLATE, code, AppPackageImportResultDTO.CREATED, "已导入单据打印模板");
            } else {
                docTemplateMapper.updateById(entity);
                result.add(TYPE_DOC_TEMPLATE, code, AppPackageImportResultDTO.UPDATED, "已覆盖单据打印模板");
            }
            if ("Y".equals(entity.getIsDefault())) {
                // 默认模板唯一：单条条件 update 清掉同表单其余 Y（与 DocTemplateService 同口径，竞态关在一条语句里）
                docTemplateMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.privernal.dynamic.advanced.domain.DynamicDocTemplate>()
                                .eq(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getFormId, form.getId())
                                .eq(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getIsDefault, "Y")
                                .ne(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getId, entity.getId())
                                .set(com.privernal.dynamic.advanced.domain.DynamicDocTemplate::getIsDefault, "N"));
            }
        } catch (Exception e) {
            log.warn("导入单据打印模板失败 templateCode={}", code, e);
            result.add(TYPE_DOC_TEMPLATE, code, AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    /**
     * 导入定时任务（task-upgrade P1-2）：判重按 (app_id, task_code)、尊重 overwrite、
     * 透传包内全部调度属性（jitter/misfire/并发/超时/max_retry），next_fire_time 按目标环境重算，
     * 一次性任务原时刻缺失或已过期则跳过（绝不"导入即执行"）。落地在
     * {@code ScheduleTaskService.importFromPackage}，本方法只负责组装候选行与写报告。
     */
    private void importScheduleTask(String appId, AppPackageDTO.ScheduleTaskPackage task, boolean overwrite,
                                    AppPackageImportResultDTO result) {
        String code = task.getTaskCode() == null ? task.getBizKey() : task.getTaskCode();
        try {
            if (task.getActionConfig() != null && task.getActionConfig().contains("tableName")) {
                String tableName = JSON.parseObject(task.getActionConfig()).getString("tableName");
                if (StringUtils.hasText(tableName)) {
                    // P2-3 已实施（task-upgrade，2026-10-06）：action_config 存的是源环境物理表名（§8.3），
                    // 用严格解析（原样精确 → 剥源组织前缀 → 目标前缀拼接；不做后缀歧义匹配）定位目标环境表单，
                    // 命中后把 tableName 改写为目标物理名——否则任务到点执行仍会去查源环境的表；
                    // 解析不出（目标环境确无该表/已改名）维持"跳过 + 导入报告"
                    com.privernal.dynamic.dbs.domain.DynamicForm resolved = reportTableResolver.resolveFormStrict(tableName);
                    if (resolved == null) {
                        result.add(TYPE_SCHEDULE, code, AppPackageImportResultDTO.SKIPPED,
                                "任务引用的表单在目标环境不存在：" + tableName);
                        return;
                    }
                    if (!resolved.getTableCode().equals(tableName)) {
                        JSONObject actionConfig = JSON.parseObject(task.getActionConfig());
                        actionConfig.put("tableName", resolved.getTableCode());
                        task.setActionConfig(actionConfig.toJSONString());
                        log.info("导入定时任务已重挂表名：{} -> {}（code={}）", tableName, resolved.getTableCode(), code);
                    }
                }
            }
            com.privernal.job.domain.SystemScheduleTask template = new com.privernal.job.domain.SystemScheduleTask();
            template.setAppId(appId);
            template.setOrganizationId(SecurityUtils.getOrganizationId());
            template.setTaskCode(task.getTaskCode());
            template.setTitle(task.getTitle());
            template.setSource(task.getSource());
            template.setBizKey(task.getBizKey());
            template.setTriggerType(task.getTriggerType());
            template.setCronExpr(task.getCronExpr());
            template.setIntervalSeconds(task.getIntervalSeconds());
            template.setJitterSeconds(task.getJitterSeconds());
            template.setMisfirePolicy(task.getMisfirePolicy());
            template.setMisfireWindowSeconds(task.getMisfireWindowSeconds());
            template.setConcurrentPolicy(task.getConcurrentPolicy());
            template.setTimeoutSeconds(task.getTimeoutSeconds());
            template.setMaxRetry(task.getMaxRetry());
            template.setActionType(task.getActionType());
            template.setActionConfig(task.getActionConfig());
            template.setBizContext(task.getBizContext());
            template.setRemark(task.getRemark());
            template.setNextFireTime(task.getFireTime());
            String outcome = scheduleTaskService.importFromPackage(template, overwrite);
            if (outcome.startsWith("CREATED")) {
                result.add(TYPE_SCHEDULE, code, AppPackageImportResultDTO.CREATED,
                        "已导入定时任务（触发时间按目标环境重算）");
            } else if (outcome.startsWith("UPDATED") || outcome.startsWith("REVIVED")) {
                result.add(TYPE_SCHEDULE, code, AppPackageImportResultDTO.UPDATED, "已覆盖同码定时任务");
            } else if (outcome.startsWith("SKIPPED")) {
                result.add(TYPE_SCHEDULE, code, AppPackageImportResultDTO.SKIPPED, outcome.substring("SKIPPED:".length()));
            } else {
                result.add(TYPE_SCHEDULE, code, AppPackageImportResultDTO.FAILED,
                        outcome.substring("REJECTED:".length()));
            }
        } catch (Exception e) {
            log.warn("导入定时任务失败 code={}", code, e);
            result.add(TYPE_SCHEDULE, code, AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    /**
     * 导入数据集：saveDynamicDataset 会按目标环境表单重新解析列快照（导入端校验，
     * 源环境列快照不入目标库），表单缺失时报可读错误而不是静默丢图。
     */
    private void importDataset(String appId, AppPackageDTO.Dataset dataset, boolean overwrite, AppPackageImportResultDTO result) {
        Assert.hasText(dataset.getDatasetCode(), "应用包内存在缺少 datasetCode 的数据集");
        try {
            com.privernal.dynamic.report.domain.DynamicDataset existing = datasetService.findByCode(dataset.getDatasetCode());
            if (existing != null && !overwrite) {
                result.add(TYPE_DATASET, dataset.getDatasetCode(), AppPackageImportResultDTO.SKIPPED,
                        "数据集已存在，未开启覆盖");
                return;
            }
            com.privernal.dynamic.report.domain.DynamicDataset payload = new com.privernal.dynamic.report.domain.DynamicDataset();
            payload.setDatasetCode(dataset.getDatasetCode());
            payload.setDatasetName(dataset.getDatasetName());
            payload.setTableCode(dataset.getTableCode());
            // 已存在的沿用原归属，新建必须挂到本应用：导出是按 appId 筛数据集的，
            // 留 null 会让这个包再次导出时丢掉数据集，图表随后因找不到数据集而 FAILED
            payload.setAppId(existing != null && existing.getAppId() != null ? existing.getAppId() : appId);
            payload.setDefaultFilter(dataset.getDefaultFilter());
            // 多源四列（1.4）：外部源（http/csv）没有 tableCode 可解析，不带 sourceConfig
            // 的话导入后就是一个空壳数据集；动态表源缺省 sourceType 即默认值，行为不变
            payload.setSourceType(dataset.getSourceType());
            payload.setSourceConfig(dataset.getSourceConfig());
            payload.setRefreshConfig(dataset.getRefreshConfig());
            payload.setComputedColumns(dataset.getComputedColumns());
            datasetService.saveDynamicDataset(payload);
            result.add(TYPE_DATASET, dataset.getDatasetCode(), existing == null
                    ? AppPackageImportResultDTO.CREATED : AppPackageImportResultDTO.UPDATED,
                    existing == null ? "已创建数据集并解析列快照" : "已更新数据集并重解析列快照");
        } catch (Exception e) {
            log.warn("导入数据集失败 datasetCode={}", dataset.getDatasetCode(), e);
            result.add(TYPE_DATASET, dataset.getDatasetCode(), AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    /**
     * 导入大屏（1.4）：判重键 screen_code；走 saveDatavScreen 复用既有链路——
     * 菜单自动化（menu_code=screenCode 挂目标应用目录）、首个版本记录、组织归属都在
     * 链路里生成。导入的屏一律是草稿态：发布是目标环境的人工动作（发布快照含环境语境）。
     */
    private void importDatavScreen(String appId, AppPackageDTO.DatavScreen screen, boolean overwrite,
                                   AppPackageImportResultDTO result) {
        String code = screen.getScreenCode();
        if (!StringUtils.hasText(code)) {
            result.add(TYPE_DATAV_SCREEN, screen.getScreenName(), AppPackageImportResultDTO.FAILED, "缺少稳定编码 screenCode");
            return;
        }
        try {
            com.privernal.dynamic.datav.domain.DynamicDatavScreen existing = datavScreenService.findByCode(code);
            if (existing != null && !overwrite) {
                result.add(TYPE_DATAV_SCREEN, code, AppPackageImportResultDTO.SKIPPED, "大屏已存在，未开启覆盖");
                return;
            }
            com.privernal.dynamic.datav.domain.DynamicDatavScreen payload = new com.privernal.dynamic.datav.domain.DynamicDatavScreen();
            payload.setScreenCode(code);
            payload.setScreenName(screen.getScreenName());
            payload.setLayoutMode(screen.getLayoutMode());
            payload.setAppId(appId);
            payload.setStatus(0);
            if (StringUtils.hasText(screen.getSchemaJson())) {
                payload.setSchemaJson(JSON.parseObject(screen.getSchemaJson()));
            }
            datavScreenService.saveDatavScreen(payload);
            result.add(TYPE_DATAV_SCREEN, code, existing == null
                    ? AppPackageImportResultDTO.CREATED : AppPackageImportResultDTO.UPDATED,
                    existing == null ? "已创建大屏（草稿态，菜单已自动登记）" : "已覆盖大屏内容");
        } catch (Exception e) {
            log.warn("导入大屏失败 screenCode={}", code, e);
            result.add(TYPE_DATAV_SCREEN, code, AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    /**
     * 导入大屏素材（1.4）：assetCode 原样保留（schema 里引用的是 file_url 而不是编码，
     * 但保留编码让两次导入幂等）。file_url 不做改写：跨环境文件服务的域名/路径是否一致
     * 是部署侧的事，导入报告不猜测它。
     */
    private void importDatavAsset(String appId, AppPackageDTO.DatavAsset asset, boolean overwrite,
                                  AppPackageImportResultDTO result) {
        String code = asset.getAssetCode();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(asset.getFileUrl())) {
            result.add(TYPE_DATAV_ASSET, code, AppPackageImportResultDTO.FAILED, "素材缺少 assetCode 或 fileUrl");
            return;
        }
        try {
            boolean exists = datavAssetService.listAssets(null, null).stream()
                    .anyMatch(item -> code.equals(item.getAssetCode()));
            if (exists && !overwrite) {
                result.add(TYPE_DATAV_ASSET, code, AppPackageImportResultDTO.SKIPPED, "素材已存在，未开启覆盖");
                return;
            }
            com.privernal.dynamic.datav.domain.DynamicDatavAsset payload = new com.privernal.dynamic.datav.domain.DynamicDatavAsset();
            payload.setAssetCode(code);
            payload.setAssetName(asset.getAssetName());
            payload.setCategory(asset.getCategory());
            payload.setAssetType(asset.getAssetType());
            payload.setFileUrl(asset.getFileUrl());
            payload.setOriginalName(asset.getOriginalName());
            payload.setFileSize(asset.getFileSize());
            datavAssetService.saveAsset(payload);
            result.add(TYPE_DATAV_ASSET, code, exists
                    ? AppPackageImportResultDTO.UPDATED : AppPackageImportResultDTO.CREATED,
                    exists ? "已覆盖素材登记" : "已登记素材（文件字节需部署侧迁移，见包内注释）");
        } catch (Exception e) {
            log.warn("导入素材失败 assetCode={}", code, e);
            result.add(TYPE_DATAV_ASSET, code, AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    private void importReportAsset(String appId, AppPackageDTO.ReportAsset report, boolean overwrite,
                                   AppPackageImportResultDTO result) {
        Assert.hasText(report.getReportCode(), "应用包内存在缺少 reportCode 的图表");
        try {
            com.privernal.dynamic.report.domain.DynamicReport existing = reportService.findByCode(report.getReportCode());
            if (existing != null && !overwrite) {
                result.add(TYPE_REPORT, report.getReportCode(), AppPackageImportResultDTO.SKIPPED,
                        "图表已存在，未开启覆盖");
                return;
            }
            if (datasetService.findByCode(report.getDatasetCode()) == null) {
                result.add(TYPE_REPORT, report.getReportCode(), AppPackageImportResultDTO.FAILED,
                        "图表依赖的数据集不存在：" + report.getDatasetCode());
                return;
            }
            com.privernal.dynamic.report.domain.DynamicReport payload = new com.privernal.dynamic.report.domain.DynamicReport();
            payload.setReportCode(report.getReportCode());
            payload.setReportName(report.getReportName());
            payload.setReportType(report.getReportType());
            payload.setDatasetCode(report.getDatasetCode());
            payload.setAppId(existing != null ? existing.getAppId() : appId);
            payload.setConfig(report.getConfig());
            reportService.saveDynamicReport(payload);
            result.add(TYPE_REPORT, report.getReportCode(), existing == null
                    ? AppPackageImportResultDTO.CREATED : AppPackageImportResultDTO.UPDATED, "已保存图表");
        } catch (Exception e) {
            log.warn("导入图表失败 reportCode={}", report.getReportCode(), e);
            result.add(TYPE_REPORT, report.getReportCode(), AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    /**
     * 导入仪表盘：saveDashboard 新建时自动注册菜单（report_{code} + 按钮），已存在走覆盖更新不再重复注册
     */
    private void importDashboard(String appId, AppPackageDTO.Dashboard dashboard, boolean overwrite,
                                 AppPackageImportResultDTO result) {
        Assert.hasText(dashboard.getDashboardCode(), "应用包内存在缺少 dashboardCode 的仪表盘");
        try {
            com.privernal.dynamic.report.domain.DynamicDashboard existing = dashboardService.findByCode(dashboard.getDashboardCode());
            if (existing != null && !overwrite) {
                result.add(TYPE_DASHBOARD, dashboard.getDashboardCode(), AppPackageImportResultDTO.SKIPPED,
                        "仪表盘已存在，未开启覆盖");
                return;
            }
            com.privernal.dynamic.report.domain.DynamicDashboard payload = new com.privernal.dynamic.report.domain.DynamicDashboard();
            payload.setDashboardCode(dashboard.getDashboardCode());
            payload.setDashboardName(dashboard.getDashboardName());
            payload.setAppId(existing != null ? existing.getAppId() : appId);
            payload.setConfig(dashboard.getConfig());
            List<com.privernal.dynamic.report.domain.DynamicDashboardItem> items = new ArrayList<>();
            for (AppPackageDTO.DashboardItem source : dashboard.getItems()) {
                com.privernal.dynamic.report.domain.DynamicDashboardItem item = new com.privernal.dynamic.report.domain.DynamicDashboardItem();
                item.setReportCode(source.getReportCode());
                item.setTitle(source.getTitle());
                item.setSpan(source.getSpan());
                item.setOrderNum(source.getOrderNum());
                item.setConfig(source.getConfig());
                items.add(item);
            }
            dashboardService.saveDashboard(payload, items);
            result.add(TYPE_DASHBOARD, dashboard.getDashboardCode(), existing == null
                    ? AppPackageImportResultDTO.CREATED : AppPackageImportResultDTO.UPDATED,
                    existing == null ? "已创建仪表盘并注册菜单" : "已更新仪表盘");
        } catch (Exception e) {
            log.warn("导入仪表盘失败 dashboardCode={}", dashboard.getDashboardCode(), e);
            result.add(TYPE_DASHBOARD, dashboard.getDashboardCode(), AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    private String importAppInfo(AppPackageDTO.App app, AppPackageImportResultDTO result) {
        SystemAppInfoFilterDTO filter = new SystemAppInfoFilterDTO();
        filter.setCode(app.getCode());
        List<SystemAppInfoDTO> exists = systemAppInfoService.selectSystemAppInfoList(filter);
        if (exists != null && !exists.isEmpty()) {
            result.setAppAction(AppPackageImportResultDTO.SKIPPED);
            result.add(TYPE_APP, app.getCode(), AppPackageImportResultDTO.SKIPPED, "目标环境已有同编码应用，直接复用");
            return exists.get(0).getId();
        }

        SystemAppInfoDTO dto = new SystemAppInfoDTO();
        dto.setCode(app.getCode());
        dto.setAppName(app.getAppName());
        dto.setIcon(app.getIcon());
        dto.setType(app.getType());
        dto.setPcPath(app.getPcPath());
        dto.setAppPath(app.getAppPath());
        dto.setStatus(app.getStatus());
        dto.setSort(app.getSort());
        // 密钥不跨环境复用：导出物不携带 appSecret，目标环境重新生成，避免源环境密钥随包外流
        dto.setAppKey(app.getCode());
        dto.setAppSecret(UUID.randomUUID().toString().replace("-", ""));
        SystemAppInfo saved = systemAppInfoService.saveSystemAppInfo(dto);

        result.setAppAction(AppPackageImportResultDTO.CREATED);
        result.add(TYPE_APP, app.getCode(), AppPackageImportResultDTO.CREATED, "已创建应用并生成应用目录菜单");
        return saved.getId();
    }

    private void importForm(String appId, AppPackageDTO.Form form, boolean overwrite,
                            AppPackageImportResultDTO result) {
        Assert.hasText(form.getTableCode(), "应用包内存在缺少 tableCode 的表单");
        try {
            DynamicForm payload = new DynamicForm();
            payload.setAppId(appId);
            payload.setTableCode(form.getTableCode());
            payload.setTableName(form.getTableName());
            payload.setMetaData(form.getMetaData());
            payload.setIsWorkflow(form.getIsWorkflow());
            payload.setWorkflowCode(form.getWorkflowCode());

            DynamicForm existing = dynamicFormService.selectDynamicFormByTableCode(form.getTableCode());
            if (existing == null) {
                DynamicForm created = dynamicFormService.insertDynamicForm(payload);
                applyListConfig(created.getId(), form.getColumns());
                result.add(TYPE_FORM, form.getTableCode(), AppPackageImportResultDTO.CREATED,
                        "已建表并生成表单菜单与按钮权限");
            } else if (overwrite) {
                List<String> droppingFields = findFieldsToDrop(existing.getId(), form.getColumns());
                if (!droppingFields.isEmpty()) {
                    result.add(TYPE_FORM, form.getTableCode(), AppPackageImportResultDTO.SKIPPED,
                            "覆盖会删除目标环境的字段：" + String.join("、", droppingFields) + "，已跳过该表单");
                    return;
                }
                payload.setId(existing.getId());
                DynamicForm updated = dynamicFormService.updateDynamicForm(payload);
                applyListConfig(updated.getId(), form.getColumns());
                result.add(TYPE_FORM, form.getTableCode(), AppPackageImportResultDTO.UPDATED,
                        "已更新表单定义，物理表按增量补列");
            } else {
                result.add(TYPE_FORM, form.getTableCode(), AppPackageImportResultDTO.SKIPPED,
                        "表单已存在，未开启覆盖");
            }
        } catch (Exception e) {
            log.warn("导入动态表单失败 tableCode={}", form.getTableCode(), e);
            result.add(TYPE_FORM, form.getTableCode(), AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    /**
     * 覆盖导入的数据安全校验。建表链路会把物理表中不存在于 metaData 的列执行 ALTER TABLE DROP COLUMN
     * （AutoCreateTableServiceImpl.removeFieldsByMap），目标环境自行添加的字段不在应用包内，
     * 直接覆盖会被连数据一起删除且不可恢复，因此这里先拦下来。
     */
    private List<String> findFieldsToDrop(String formId, List<AppPackageDTO.Column> packageColumns) {
        Set<String> packageCodes = new HashSet<>();
        if (packageColumns != null) {
            for (AppPackageDTO.Column column : packageColumns) {
                if (StringUtils.hasText(column.getFiledCode())) {
                    packageCodes.add(column.getFiledCode());
                }
            }
        }
        List<String> droppingFields = new ArrayList<>();
        for (DynamicFormColumn target : dynamicFormColumnService.selectDynamicFormColumnByFormId(formId)) {
            if (StringUtils.hasText(target.getFiledCode()) && !packageCodes.contains(target.getFiledCode())) {
                droppingFields.add(target.getFiledCode());
            }
        }
        return droppingFields;
    }

    /**
     * 还原列表设计配置。列的物理类型由 metaData 经建表链路决定，此处只回填列表显示、
     * 查询显示、排序与默认值；建表链路未生成的列（例如源环境手工加过的字段）跳过并留痕。
     */
    private void applyListConfig(String formId, List<AppPackageDTO.Column> columns) {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        Map<String, DynamicFormColumn> targetByCode = new HashMap<>();
        for (DynamicFormColumn column : dynamicFormColumnService.selectDynamicFormColumnByFormId(formId)) {
            targetByCode.put(column.getFiledCode(), column);
        }
        for (AppPackageDTO.Column source : columns) {
            DynamicFormColumn target = targetByCode.get(source.getFiledCode());
            if (target == null) {
                log.warn("表单 {} 的列 {} 在目标环境未生成，跳过列表配置", formId, source.getFiledCode());
                continue;
            }
            if (StringUtils.hasText(source.getFiledName())) {
                target.setFiledName(source.getFiledName());
            }
            if (source.getShowList() != null) {
                target.setShowList(source.getShowList());
            }
            if (source.getShowQuery() != null) {
                target.setShowQuery(source.getShowQuery());
            }
            if (source.getSort() != null) {
                target.setSort(source.getSort());
            }
            if (source.getDefaultValue() != null) {
                target.setDefaultValue(source.getDefaultValue());
            }
            if (source.getQueryDefaultValue() != null) {
                target.setQueryDefaultValue(source.getQueryDefaultValue());
            }
            if (source.getOrderDir() != null) {
                target.setOrderDir(source.getOrderDir());
            }
            if (source.getSensitive() != null) {
                target.setSensitive(source.getSensitive());
            }
            if (source.getUniqueFlag() != null) {
                target.setUniqueFlag(source.getUniqueFlag());
            }
            dynamicFormColumnService.updateDynamicFormColumn(target);
        }
    }

    private void importFlow(AppPackageDTO.Flow flow, boolean overwrite, AppPackageImportResultDTO result) {
        Assert.hasText(flow.getFlowCode(), "应用包内存在缺少 flowCode 的流程");
        try {
            DefJson defJson = toImportableDefJson(flow.getDefinition(), flow.getFlowCode());
            Definition saved = flowDefinitionService.saveDefJson(defJson);
            Assert.notNull(saved, "流程定义保存后未查到记录");
            flowDefinitionService.publish(saved.getId());
            result.add(TYPE_FLOW, flow.getFlowCode(), AppPackageImportResultDTO.CREATED,
                    "已导入流程定义并发布版本 " + saved.getVersion());

            for (AppPackageDTO.FormVersion version : flow.getFormVersions()) {
                importFlowFormVersion(flow.getFlowCode(), version, overwrite, result);
            }
        } catch (Exception e) {
            log.warn("导入流程定义失败 flowCode={}", flow.getFlowCode(), e);
            result.add(TYPE_FLOW, flow.getFlowCode(), AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    private DefJson toImportableDefJson(JSONObject definition, String flowCode) {
        Assert.notNull(definition, "应用包缺少流程定义 JSON：" + flowCode);
        // 双保险：即便应用包被手工改回源环境 id，也不能让它覆盖目标环境的同 id 流程
        definition.remove("id");
        definition.remove("version");
        definition.remove("isPublish");
        DefJson defJson = definition.to(DefJson.class);
        Assert.hasText(defJson.getFlowCode(), "流程定义 JSON 缺少 flowCode：" + flowCode);
        return defJson;
    }

    private void importFlowFormVersion(String flowCode, AppPackageDTO.FormVersion version, boolean overwrite,
                                       AppPackageImportResultDTO result) {
        String code = flowCode + ":" + version.getTableCode();
        try {
            List<FlowFormVersionDTO> published =
                    flowFormVersionService.selectVersionList(flowCode, version.getTableCode(), VERSION_PUBLISHED);
            String draftId;
            String action;
            if (published == null || published.isEmpty()) {
                flowFormVersionService.initDraft(flowCode, version.getTableCode());
                draftId = findDraftId(flowCode, version.getTableCode());
                action = AppPackageImportResultDTO.CREATED;
            } else if (overwrite) {
                FlowFormVersionDTO current = pickCurrentPublished(published);
                if (current == null) {
                    result.add(TYPE_FLOW_FORM, code, AppPackageImportResultDTO.SKIPPED,
                            "已有版本但没有当前生效版，未复制新草稿");
                    return;
                }
                flowFormVersionService.copyAsDraft(current.getId());
                draftId = findDraftId(flowCode, version.getTableCode());
                action = AppPackageImportResultDTO.UPDATED;
            } else {
                result.add(TYPE_FLOW_FORM, code, AppPackageImportResultDTO.SKIPPED,
                        "表单节点权限配置已存在，未开启覆盖");
                return;
            }
            if (draftId == null) {
                result.add(TYPE_FLOW_FORM, code, AppPackageImportResultDTO.FAILED, "未能初始化流程表单配置草稿");
                return;
            }

            FlowFormVersionDTO draft = flowFormVersionService.selectVersionDetail(draftId);
            draft.setFormName(version.getFormName());
            draft.setFieldPermList(toFieldPerms(version.getFields()));
            draft.setBtnPermList(toBtnPerms(version.getButtons()));
            flowFormVersionService.saveDraft(draft);
            flowFormVersionService.publish(draftId);
            result.add(TYPE_FLOW_FORM, code, action, "已导入节点字段与按钮权限并发布");
        } catch (Exception e) {
            log.warn("导入流程表单配置失败 {}", code, e);
            result.add(TYPE_FLOW_FORM, code, AppPackageImportResultDTO.FAILED, e.getMessage());
        }
    }

    private String findDraftId(String flowCode, String tableCode) {
        List<FlowFormVersionDTO> drafts =
                flowFormVersionService.selectVersionList(flowCode, tableCode, VERSION_DRAFT);
        return drafts == null || drafts.isEmpty() ? null : drafts.get(0).getId();
    }

    private List<FlowFormFieldPermDTO> toFieldPerms(List<AppPackageDTO.FieldPerm> fields) {
        List<FlowFormFieldPermDTO> perms = new java.util.ArrayList<>();
        for (AppPackageDTO.FieldPerm field : fields) {
            FlowFormFieldPermDTO perm = new FlowFormFieldPermDTO();
            perm.setNodeCode(field.getNodeCode());
            perm.setFieldCode(field.getFieldCode());
            perm.setFieldName(field.getFieldName());
            perm.setIsShow(field.getIsShow());
            perm.setIsEdit(field.getIsEdit());
            perm.setIsRequired(field.getIsRequired());
            perm.setDefaultType(field.getDefaultType());
            perm.setDefaultValue(field.getDefaultValue());
            perm.setDictType(field.getDictType());
            perm.setSort(field.getSort());
            perms.add(perm);
        }
        return perms;
    }

    private List<FlowFormBtnPermDTO> toBtnPerms(List<AppPackageDTO.BtnPerm> buttons) {
        List<FlowFormBtnPermDTO> perms = new java.util.ArrayList<>();
        for (AppPackageDTO.BtnPerm btn : buttons) {
            FlowFormBtnPermDTO perm = new FlowFormBtnPermDTO();
            perm.setNodeCode(btn.getNodeCode());
            perm.setBtnCode(btn.getBtnCode());
            perm.setBtnName(btn.getBtnName());
            perm.setAliasName(btn.getAliasName());
            perm.setIsShow(btn.getIsShow());
            perm.setShowDialog(btn.getShowDialog());
            perm.setColor(btn.getColor());
            perm.setSort(btn.getSort());
            perms.add(perm);
        }
        return perms;
    }

    /** 优先取已发布的定义，其次是版本号最大的一条 */
    private Definition pickDefinition(List<Definition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        for (Definition definition : definitions) {
            if (PUBLISHED.equals(definition.getIsPublish())) {
                return definition;
            }
        }
        return definitions.get(definitions.size() - 1);
    }

    private FlowFormVersionDTO pickCurrentPublished(List<FlowFormVersionDTO> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        for (FlowFormVersionDTO version : versions) {
            if (CURRENT_FLAG_YES.equals(version.getCurrentFlag())) {
                return version;
            }
        }
        return versions.get(versions.size() - 1);
    }
}
