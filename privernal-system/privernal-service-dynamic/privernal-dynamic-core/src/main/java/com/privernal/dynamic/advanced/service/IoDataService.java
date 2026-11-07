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
import com.privernal.common.datascope.aspect.DataScopeHelper;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.advanced.domain.DynamicFormIoConfig;
import com.privernal.dynamic.advanced.engine.AdvancedKillSwitch;
import com.privernal.dynamic.advanced.engine.ConditionEvaluator;
import com.privernal.dynamic.advanced.engine.DataWriteAttributes;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.engine.SafeConditionSql;
import com.privernal.dynamic.advanced.engine.SensitiveDataMask;
import com.privernal.dynamic.advanced.mapper.DynamicFormIoConfigMapper;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageQueryMapper;
import com.privernal.dynamic.dba.utils.FormDataTitleUtils;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dba.utils.JsonValueCodec;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.domain.DynamicFormRelation;
import com.privernal.dynamic.dbs.mapper.DynamicFormColumnMapper;
import com.privernal.dynamic.dbs.mapper.DynamicFormRelationMapper;
import com.privernal.dynamic.dbs.service.CommonService;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.filter.WorkflowUserFilterDTO;
import com.privernal.common.web.domain.CommonResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 表单数据导入导出运行时（T1）。
 * <ul>
 *   <li>模板：两 Sheet（数据头+示例行 / 字段说明），日期列统一文本格式；</li>
 *   <li>导入：异步执行 + 进度轮询 + 失败报告（可下载回改）；类型/必填/唯一校验，
 *       重复键策略 SKIP/OVERWRITE/REJECT；sourceTag=IMPORT，默认抑制联动事件与 DATE_FIELD 注册（§4.2），
 *       由「重算到期任务」/每日对账补注册；</li>
 *   <li>导出：分页 1000 循环流式写出，敏感列按 io_config.sensitive_policy 脱敏/剔除，
 *       选项类字段按 metaData props.options 回翻译，@Log 审计；</li>
 *   <li>kill switch：io 闸关停时全部拒绝（T9/D7）。</li>
 * </ul>
 */
@Slf4j
@Service
public class IoDataService {

    /** 导入执行线程（单实例顺序执行足够；多实例部署时任务各自独立不冲突） */
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "io-import-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** 导入任务状态（内存态；1 小时后由轮询方触发清理） */
    private final Map<String, ImportTask> tasks = new ConcurrentHashMap<>();

    @Autowired
    private DynamicFormColumnMapper formColumnMapper;

    @Autowired
    private DynamicFormIoConfigMapper ioConfigMapper;

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private CommonService commonService;

    @Autowired
    private LinkageSupport linkageSupport;

    @Autowired
    private DynamicLinkageQueryMapper linkageQueryMapper;

    @Autowired
    private AdvancedKillSwitch killSwitch;

    @Autowired
    private com.privernal.dynamic.dbs.service.IDynamicFormColumnService dynamicFormColumnService;

    @Autowired
    private DynamicFormRelationMapper dynamicFormRelationMapper;

    @Autowired
    private RemoteUserService remoteUserService;

    // ---------------- 模板 ----------------

    public byte[] buildTemplate(String formId) throws Exception {
        DynamicForm form = requireForm(formId);
        DynamicFormIoConfig config = loadConfig(formId);
        if (!"Y".equals(config.getImportEnabled())) {
            throw new IllegalArgumentException("该表单未开启数据导入，请在「高级设置 → 导入/导出设置」开启");
        }
        List<DynamicFormColumn> columns = visibleColumns(formId, null);
        List<JSONObject> schemaFields = inputFields(form);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("数据");
            Sheet meta = workbook.createSheet("字段说明");
            Row header = sheet.createRow(0);
            CellStyle headStyle = workbook.createCellStyle();
            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headStyle.setFont(headFont);
            for (int i = 0; i < columns.size(); i++) {
                DynamicFormColumn column = columns.get(i);
                header.createCell(i).setCellValue(displayCode(column, schemaFields));
                header.getCell(i).setCellStyle(headStyle);
            }
            CellStyle sampleStyle = workbook.createCellStyle();
            sampleStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            sampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row sample = sheet.createRow(1);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = sample.createCell(i);
                cell.setCellValue("__示例行（导入时自动跳过，可整行删除）");
                cell.setCellStyle(sampleStyle);
            }
            Row metaHeader = meta.createRow(0);
            String[] heads = {"字段编码", "字段名称", "类型", "必填", "取值范围/说明"};
            for (int i = 0; i < heads.length; i++) {
                metaHeader.createCell(i).setCellValue(heads[i]);
                metaHeader.getCell(i).setCellStyle(headStyle);
            }
            int r = 1;
            for (DynamicFormColumn column : columns) {
                JSONObject schema = schemaField(schemaFields, displayCode(column, schemaFields));
                Row row = meta.createRow(r++);
                row.createCell(0).setCellValue(displayCode(column, schemaFields));
                row.createCell(1).setCellValue(column.getFiledName());
                row.createCell(2).setCellValue(column.getFiledType());
                row.createCell(3).setCellValue(schema != null && Boolean.TRUE.equals(schema.getBoolean("required")) ? "是" : "否");
                row.createCell(4).setCellValue(optionsText(schema));
            }
            for (int i = 0; i < columns.size(); i++) {
                sheet.setColumnWidth(i, 22 * 256);
            }
            meta.setColumnWidth(0, 26 * 256);
            meta.setColumnWidth(4, 46 * 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ---------------- 导入 ----------------

    public Map<String, Object> startImport(String formId, byte[] fileBytes) throws Exception {
        if (!killSwitch.ioEnabled()) {
            throw new IllegalStateException("导入导出能力已被平台关停（kill switch）");
        }
        DynamicForm form = requireForm(formId);
        DynamicFormIoConfig config = loadConfig(formId);
        if (!"Y".equals(config.getImportEnabled())) {
            throw new IllegalArgumentException("该表单未开启数据导入");
        }
        String database = linkageSupport.resolveDatabase(form.getOrganizationId());
        List<DynamicFormColumn> columns = visibleColumns(formId, null);
        List<JSONObject> schemaFields = inputFields(form);

        // 解析（同步做，文件异常立刻反馈；行处理异步）
        List<Map<String, Object>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheet("数据") != null ? workbook.getSheet("数据") : workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("模板缺少表头行");
            }
            List<String> headerCodes = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String code = readCellAsString(header.getCell(i));
                headerCodes.add(code == null ? "" : code.trim());
            }
            Map<String, String> codeByHeader = new HashMap<>();
            for (DynamicFormColumn column : columns) {
                codeByHeader.put(displayCode(column, schemaFields).toLowerCase(), column.getFiledCode());
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String first = readCellAsString(row.getCell(0));
                if (first != null && first.startsWith("__示例行")) {
                    continue;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                boolean empty = true;
                for (int c = 0; c < headerCodes.size(); c++) {
                    String code = codeByHeader.get(headerCodes.get(c).toLowerCase());
                    if (code == null) {
                        continue;
                    }
                    String raw = readCellAsString(row.getCell(c));
                    if (raw != null && !raw.isEmpty()) {
                        empty = false;
                    }
                    data.put(code, convertValue(columnByCode(columns, code), raw, schemaField(schemaFields, headerCodes.get(c).trim())));
                }
                if (!empty) {
                    rows.add(data);
                }
            }
        }

        int limit = config.getImportLimit() == null ? 5000 : config.getImportLimit();
        if (rows.size() > limit) {
            throw new IllegalArgumentException("导入行数 " + rows.size() + " 超过单次上限 " + limit);
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        ImportTask task = new ImportTask();
        task.taskId = taskId;
        task.formId = formId;
        task.tableCode = form.getTableCode();
        task.total = rows.size();
        task.rows = rows;
        task.database = database;
        task.config = config;
        task.form = form;
        task.columns = columns;
        task.schemaFields = schemaFields;
        tasks.put(taskId, task);
        importExecutor.submit(() -> runImport(task));
        return Map.of("taskId", taskId, "total", rows.size());
    }

    private void runImport(ImportTask task) {
        // 导入线程缺会话：显式注入组织与数据源上下文（§5.5 同口径），行级范围不适用
        OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
        OrganizationContextHolder.setOrganizationId(task.form.getOrganizationId());
        try {
            boolean strict = !"N".equals(task.config.getStrictValidate());
            String dup = StringUtils.isEmpty(task.config.getDupStrategy()) ? "SKIP" : task.config.getDupStrategy();
            boolean runScript = "Y".equals(task.config.getRunBackscript());
            JSONObject metaData = task.form.getMetaData();
            String backScript = null;
            if (runScript && metaData != null && metaData.getJSONObject("backScript") != null) {
                backScript = metaData.getJSONObject("backScript").getString("script");
            }
            List<String> uniqueCodes = uniqueColumnCodes(task.columns);
            Set<String> seenInFile = new HashSet<>();
            // 导入反向解析：导出文件里人员/部门/关联列是文案（昵称/名称/displayField 值），
            // 写库前还原为存储 id；值本身已是 id 时保持不变
            ImportRefResolver refResolver = buildImportResolver(task.form, task.schemaFields);

            for (Map<String, Object> row : task.rows) {
                int rowIndex = task.processed.get() + 1;
                try {
                    if (row.isEmpty()) {
                        task.processed.incrementAndGet();
                        continue;
                    }
                    refResolver.resolve(row);
                    // 必填校验
                    if (strict) {
                        String missing = checkRequired(row, task.schemaFields, task.columns);
                        if (missing != null) {
                            throw new IllegalArgumentException("必填字段缺失：" + missing);
                        }
                    }
                    // id 有值 → 更新；唯一列查重
                    Object rowId = row.get("id");
                    if (rowId == null || String.valueOf(rowId).isEmpty()) {
                        row.remove("id");
                        String dupKey = findDuplicate(task, uniqueCodes, row, seenInFile);
                        if (dupKey != null) {
                            switch (dup) {
                                case "REJECT" -> throw new IllegalArgumentException(
                                        "唯一键冲突（" + dupKey + "），整批拒绝（dup_strategy=REJECT）");
                                case "OVERWRITE" -> {
                                    String existId = locateId(task, uniqueCodes, row);
                                    if (existId != null) {
                                        row.put("id", existId);
                                    } else {
                                        task.skipped.incrementAndGet();
                                        task.errors.add("行 " + rowIndex + "：文件内重复，跳过");
                                        task.processed.incrementAndGet();
                                        continue;
                                    }
                                }
                                default -> {
                                    task.skipped.incrementAndGet();
                                    task.errors.add("行 " + rowIndex + "：唯一键冲突（" + dupKey + "），按 SKIP 跳过");
                                    task.processed.incrementAndGet();
                                    continue;
                                }
                            }
                        }
                    }
                    // 数据标题 & 工作流表单落草稿（import_flow_mode=NONE，§4.1）
                    if (metaData != null) {
                        String dataTitle = FormDataTitleUtils.buildTitle(metaData, row);
                        if (StringUtils.isNotEmpty(dataTitle)) {
                            row.put("title", dataTitle);
                        }
                    }
                    if (task.form.getIsWorkflow() != null && task.form.getIsWorkflow() == 1
                            && !"AUTO_START".equals(task.config.getImportFlowMode())) {
                        row.put("status", 0);
                    }
                    // 落库：sourceTag=IMPORT（联动/DATE_FIELD 注册按 trigger_mode 抑制，§4.2）；
                    // 导入线程无会话，代理身份=表单创建人（动态表 created_by NOT NULL）
                    DataWriteAttributes.Snapshot previous = DataWriteAttributes.push(
                            DataWriteAttributes.SOURCE_IMPORT, 0,
                            StringUtils.isNotEmpty(task.form.getCreatedBy()) ? task.form.getCreatedBy() : "system-import",
                            task.form.getDepartmentId(), task.form.getOrganizationId());
                    try {
                        if (StringUtils.isNotEmpty(backScript)) {
                            commonService.saveTableData(task.database, task.tableCode, backScript, row, true);
                        } else {
                            dynamicTableService.saveOrUpdate(task.database, task.tableCode, row);
                        }
                    } finally {
                        DataWriteAttributes.restore(previous);
                    }
                    task.success.incrementAndGet();
                } catch (Exception e) {
                    task.failed.incrementAndGet();
                    task.errors.add("行 " + rowIndex + "：" + e.getMessage());
                    log.warn("导入行失败 form={} row={} error={}", task.formId, rowIndex, e.getMessage());
                } finally {
                    task.processed.incrementAndGet();
                }
            }
        } catch (Exception e) {
            log.error("导入任务异常 form={}", task.formId, e);
            task.errors.add("任务异常终止：" + e.getMessage());
        } finally {
            task.finished = true;
            OrganizationContextHolder.clear();
            log.info("导入任务完成 form={} total={} success={} skipped={} failed={}",
                    task.formId, task.total, task.success.get(), task.skipped.get(), task.failed.get());
        }
    }

    public Map<String, Object> progress(String taskId) {
        ImportTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("导入任务不存在或已过期：" + taskId);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("finished", task.finished);
        result.put("total", task.total);
        result.put("processed", task.processed.get());
        result.put("success", task.success.get());
        result.put("skipped", task.skipped.get());
        result.put("failed", task.failed.get());
        return result;
    }

    public byte[] buildFailureReport(String taskId) throws Exception {
        ImportTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("导入任务不存在或已过期：" + taskId);
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("失败明细");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("行号");
            header.createCell(1).setCellValue("原因");
            for (int i = 0; i < task.errors.size() && i < 5000; i++) {
                Row row = sheet.createRow(i + 1);
                String error = task.errors.get(i);
                int split = error.indexOf('：');
                row.createCell(0).setCellValue(split > 0 ? error.substring(0, split) : ("行 " + (i + 1)));
                row.createCell(1).setCellValue(error);
            }
            sheet.setColumnWidth(1, 90 * 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ---------------- 导出 ----------------

    public byte[] export(String formId, Map<String, Object> query) throws Exception {
        if (!killSwitch.ioEnabled()) {
            throw new IllegalStateException("导入导出能力已被平台关停（kill switch）");
        }
        DynamicForm form = requireForm(formId);
        DynamicFormIoConfig config = loadConfig(formId);
        if (!"Y".equals(config.getExportEnabled())) {
            throw new IllegalArgumentException("该表单未开启数据导出");
        }
        String database = linkageSupport.resolveDatabase(form.getOrganizationId());
        // 导出范围（§4.1）：行级数据范围由会话数据权限切面叠加（列表同通道），这里只处理列与脱敏
        List<DynamicFormColumn> columns = visibleColumns(formId, config.getExportColumns());
        List<JSONObject> schemaFields = inputFields(form);
        boolean hide = "HIDE".equals(config.getSensitivePolicy());
        Map<String, DynamicFormColumn> byCode = new HashMap<>();
        for (DynamicFormColumn column : columns) {
            byCode.put(column.getFiledCode(), column);
        }

        List<Map<String, Object>> allRows = new ArrayList<>();
        int pageNum = 1;
        PageQuery pageQuery = new PageQuery(query == null ? Map.of() : query);
        // 导出与列表共用同一查询通道：findByPage 的过滤值是「已组装的 SQL 片段」，必须先过
        // FormTransferTable.dataGetSQL 做列白名单校验 + 字面量转义 + 类型化条件组装（like/IN/BETWEEN），
        // 直接透传原始查询会把裸值当 SQL 片段拼接（语法错误且存在注入面）
        List<com.privernal.dynamic.dto.DynamicFormColumnDTO> exportFilterFields =
                dynamicFormColumnService.selectTableColumnByCode(form.getTableCode());
        com.privernal.dynamic.dba.utils.FormTransferTable.dataGetSQL(pageQuery.source, exportFilterFields);
        pageQuery.source.remove("keywords");
        pageQuery.source.remove("keyword");
        while (true) {
            pageQuery.source.put("pageNum", pageNum);
            pageQuery.source.put("pageSize", 1000);
            com.privernal.dynamic.dba.vo.PageResultInfo page =
                    dynamicTableService.findByPage(database, form.getTableCode(), pageQuery.source);
            // PageResultInfo.getList() 是 List<Object>（元素实为 Map），逐个收窄
            List<Map<String, Object>> list = new ArrayList<>();
            if (page.getList() != null) {
                for (Object item : page.getList()) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> row = (Map<String, Object>) item;
                        list.add(row);
                    }
                }
            }
            allRows.addAll(list);
            if (list.size() < 1000 || allRows.size() >= 50000) {
                break;
            }
            pageNum++;
        }
        // 组件级回译上下文：人员/部门/关联列的 id → 展示文案（每次导出批量构建一次）
        ExportCellTranslator cellTranslator = buildCellTranslator(form, schemaFields, columns, allRows);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("数据");
            Row header = sheet.createRow(0);
            List<String> exportCodes = new ArrayList<>();
            for (DynamicFormColumn column : columns) {
                if (hide && SensitiveDataMask.needMask(column.getSensitive())) {
                    continue;
                }
                exportCodes.add(column.getFiledCode());
            }
            CellStyle headStyle = workbook.createCellStyle();
            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headStyle.setFont(headFont);
            for (int i = 0; i < exportCodes.size(); i++) {
                DynamicFormColumn column = byCode.get(exportCodes.get(i));
                header.createCell(i).setCellValue(column == null ? exportCodes.get(i) : column.getFiledName());
                header.getCell(i).setCellStyle(headStyle);
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (int r = 0; r < allRows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Map<String, Object> data = allRows.get(r);
                for (int c = 0; c < exportCodes.size(); c++) {
                    String code = exportCodes.get(c);
                    DynamicFormColumn column = byCode.get(code);
                    Object value = ConditionEvaluator.lookup(data, code);
                    if (value == null) {
                        continue;
                    }
                    if (column != null && SensitiveDataMask.needMask(column.getSensitive())) {
                        value = SensitiveDataMask.mask(String.valueOf(value));
                    } else {
                        // 组件级回译优先（人员→昵称、部门→名称、关联→displayField），options 回译兜底
                        Object translated = cellTranslator.translate(code, value);
                        value = translated != null ? translated
                                : translateOption(schemaField(schemaFields, code), value);
                    }
                    if (value instanceof Date date) {
                        row.createCell(c).setCellValue(format.format(date));
                    } else if (value instanceof Number || value instanceof Boolean) {
                        row.createCell(c).setCellValue(String.valueOf(value));
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(value));
                    }
                }
            }
            for (int i = 0; i < exportCodes.size(); i++) {
                sheet.setColumnWidth(i, 24 * 256);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            log.info("数据导出 form={} operator={} rows={} maskedPolicy={}",
                    formId, SecurityUtils.getUserId(), allRows.size(), config.getSensitivePolicy());
            return out.toByteArray();
        }
    }

    // ---------------- 内部工具 ----------------

    /**
     * 导出单元格组件级回译（对齐前端 field-translate 语义）：
     * user-select/user-multi-select → 昵称；dept-select/dept-multi-select → 部门名称；
     * relation-attr/relation-backfill → 关联配置 displayField 的值（缺失回退数据标题）。
     * 多值形态（统一信封 / 裸数组 / 逗号串）逐段回译后顿号连接；字典缺失回退原值。
     * 一次导出构建一个实例：人员/部门按导出行内出现的 id 批量查询，关联表整表拉一次。
     */
    private final class ExportCellTranslator {

        private final Map<String, String> compTypes = new HashMap<>();
        private final Map<String, String> userNames = new HashMap<>();
        private final Map<String, String> deptNames = new HashMap<>();
        private final Map<String, Map<String, String>> relationLabels = new HashMap<>();

        ExportCellTranslator(DynamicForm form, List<JSONObject> schemaFields,
                             List<DynamicFormColumn> columns, List<Map<String, Object>> rows) {
            Set<String> userIds = new LinkedHashSet<>();
            Set<String> deptIds = new LinkedHashSet<>();
            Map<String, JSONObject> relationCfg = new LinkedHashMap<>();
            for (DynamicFormColumn column : columns) {
                JSONObject schema = schemaField(schemaFields, column.getFiledCode());
                if (schema == null) {
                    continue;
                }
                String type = StringUtils.defaultString(schema.getString("type"));
                if (StringUtils.isEmpty(type)) {
                    continue;
                }
                compTypes.put(column.getFiledCode(), type);
                if (type.startsWith("user-")) {
                    for (Map<String, Object> row : rows) {
                        collectSplitIds(ConditionEvaluator.lookup(row, column.getFiledCode()), userIds);
                    }
                } else if (type.startsWith("dept-")) {
                    for (Map<String, Object> row : rows) {
                        collectSplitIds(ConditionEvaluator.lookup(row, column.getFiledCode()), deptIds);
                    }
                } else if (type.startsWith("relation")
                        && schema.getJSONObject("props") != null
                        && schema.getJSONObject("props").getJSONObject("relation") != null) {
                    relationCfg.put(column.getFiledCode(), schema.getJSONObject("props").getJSONObject("relation"));
                }
            }
            userNames.putAll(findUserNames(userIds));
            deptNames.putAll(findDeptNames(deptIds));
            for (Map.Entry<String, JSONObject> entry : relationCfg.entrySet()) {
                relationLabels.put(entry.getKey(), buildRelationLabelMap(form, entry.getValue()));
            }
        }

        /** @return 回译文案；字段非组件级回译类型或字典缺失时返回 null（走 options 回译/原值兜底） */
        String translate(String code, Object value) {
            if (value == null) {
                return null;
            }
            String type = compTypes.get(code);
            if (type == null) {
                return null;
            }
            Map<String, String> dict;
            if (type.startsWith("user-")) {
                dict = userNames;
            } else if (type.startsWith("dept-")) {
                dict = deptNames;
            } else if (type.startsWith("relation")) {
                dict = relationLabels.get(code);
            } else {
                return null;
            }
            if (dict == null || dict.isEmpty()) {
                return null;
            }
            List<String> parts = splitMultiValues(value);
            if (parts.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (sb.length() > 0) {
                    sb.append('、');
                }
                sb.append(dict.getOrDefault(part, part));
            }
            return sb.toString();
        }
    }

    private ExportCellTranslator buildCellTranslator(DynamicForm form, List<JSONObject> schemaFields,
                                                     List<DynamicFormColumn> columns,
                                                     List<Map<String, Object>> rows) {
        return new ExportCellTranslator(form, schemaFields, columns, rows);
    }

    /** 从单元格值（可能是信封/数组/逗号串）收集 id 集合 */
    private void collectSplitIds(Object value, Set<String> ids) {
        for (String part : splitMultiValues(value)) {
            ids.add(part);
        }
    }

    /**
     * 多值拆分：统一信封 {"v":[...]} / 裸 JSON 数组 / 逗号分号串 / 裸值，与前端 field-translate 同口径
     */
    private List<String> splitMultiValues(Object value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                if (item != null && !"".equals(item)) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return out;
        }
        if (raw.startsWith("{")) {
            try {
                JSONArray v = JSON.parseObject(raw).getJSONArray("v");
                if (v != null) {
                    for (Object item : v) {
                        if (item != null && !"".equals(item)) {
                            out.add(String.valueOf(item));
                        }
                    }
                    return out;
                }
            } catch (Exception ignored) {
                // 非信封对象按整体展示
            }
            out.add(raw);
            return out;
        }
        if (raw.startsWith("[") && raw.endsWith("]")) {
            try {
                for (Object item : JSON.parseArray(raw)) {
                    if (item != null && !"".equals(item)) {
                        out.add(String.valueOf(item));
                    }
                }
                return out;
            } catch (Exception ignored) {
                // 非法 JSON 落到逗号拆分
            }
        }
        for (String part : raw.split("[,;，；]")) {
            if (!part.trim().isEmpty()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    /** 批量查用户昵称（与列表 created_by 翻译同通道），失败返回空 Map（导出原值兜底） */
    private Map<String, String> findUserNames(Set<String> userIds) {
        Map<String, String> names = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        try {
            WorkflowUserFilterDTO filter = new WorkflowUserFilterDTO();
            filter.setUserList(new ArrayList<>(userIds));
            CommonResult<List<SystemUserDTO>> result = remoteUserService.findUserListByIds(filter, "");
            if (result.getCode() == CommonResult.SUCCESS && result.getData() != null) {
                for (SystemUserDTO user : result.getData()) {
                    names.put(String.valueOf(user.getId()), user.getNickName());
                }
            }
        } catch (Exception e) {
            log.warn("导出人员回译失败 userIds={}", userIds, e);
        }
        return names;
    }

    /** 批量查部门名称（与列表 department_id 翻译同通道），失败返回空 Map（导出原值兜底） */
    private Map<String, String> findDeptNames(Set<String> deptIds) {
        Map<String, String> names = new HashMap<>();
        if (deptIds == null || deptIds.isEmpty()) {
            return names;
        }
        try {
            WorkflowUserFilterDTO filter = new WorkflowUserFilterDTO();
            filter.setDeptList(new ArrayList<>(deptIds));
            CommonResult<List<SystemDepartmentDTO>> result = remoteUserService.findDeptListByIds(filter, "");
            if (result.getCode() == CommonResult.SUCCESS && result.getData() != null) {
                for (SystemDepartmentDTO dept : result.getData()) {
                    names.put(String.valueOf(dept.getId()), dept.getDeptName());
                }
            }
        } catch (Exception e) {
            log.warn("导出部门回译失败 deptIds={}", deptIds, e);
        }
        return names;
    }

    /**
     * 关联列回译映射：被关联表整表拉 id + displayField（驼峰转下划线列，缺失回退 title）。
     * 表名/列名过标识符校验；字典查询忽略数据范围（回译是元数据展示，不是行级数据读取），
     * 查询失败回退空 Map（导出原值）。
     */
    private Map<String, String> buildRelationLabelMap(DynamicForm form, JSONObject relation) {
        Map<String, String> map = new HashMap<>();
        String targetTable = relation.getString("formCode");
        if (StringUtils.isEmpty(targetTable) || !SafeConditionSql.isValidIdentifier(targetTable)) {
            return map;
        }
        // 数据源列名优先取 relation 登记的 return_columns（displayField + mappings.field），
        // displayField 是第一段；都拿不到时回退数据标题 title
        String labelColumn = null;
        String displayField = relation.getString("displayField");
        if (StringUtils.isEmpty(displayField)) {
            DynamicFormRelation registered = dynamicFormRelationMapper.selectOne(
                    Wrappers.<DynamicFormRelation>lambdaQuery()
                            .eq(DynamicFormRelation::getFormId, form.getId())
                            .eq(DynamicFormRelation::getTargetTableCode, targetTable)
                            .last("limit 1"));
            if (registered != null && StringUtils.isNotEmpty(registered.getReturnColumns())) {
                displayField = registered.getReturnColumns().split(",")[0].trim();
            }
        }
        if (StringUtils.isNotEmpty(displayField)) {
            String column = FormTransferTable.toDbColumnName(displayField);
            if (SafeConditionSql.isValidIdentifier(column)) {
                labelColumn = column;
            }
        }
        String sql = "SELECT `id`" + (labelColumn != null ? ", `" + labelColumn + "`" : ", `title`")
                + " FROM `" + targetTable + "` WHERE deleted_flag = 'N' LIMIT 50000";
        DataScopeHelper.mergeIgnoreStrategy(true, true);
        try {
            DynamicDataSourceContextHolder.push(resolveExportDatabase(form));
            List<Map<String, Object>> rows = linkageQueryMapper.selectRows(sql, java.util.Map.of());
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id == null) {
                    continue;
                }
                Object label = labelColumn != null ? row.get(labelColumn) : null;
                if (label == null || StringUtils.isEmpty(String.valueOf(label))) {
                    label = row.get("title");
                }
                map.put(String.valueOf(id), label == null ? String.valueOf(id) : String.valueOf(label));
            }
        } catch (Exception e) {
            log.warn("导出关联列回译失败 form={} target={}", form.getTableCode(), targetTable, e);
        } finally {
            DynamicDataSourceContextHolder.poll();
            DataScopeHelper.clearIgnoreStrategy();
        }
        return map;
    }

    /** 关联表数据所在库：目标表单同组织库，退回当前表单的库 */
    private String resolveExportDatabase(DynamicForm form) {
        return linkageSupport.resolveDatabase(form.getOrganizationId());
    }

    /**
     * 导入反向解析上下文（导出回译的逆操作）：人员昵称→id、部门名称→id、关联 displayField 值→id。
     * 供"导出→修改→导回"闭环使用——导出文件里人员/部门/关联列已是文案，写库前必须还原为存储 id。
     * 值本身已是 id（未命中文案映射）时保持原值；多值逐段还原后逗号拼回。
     */
    private final class ImportRefResolver {

        private final Map<String, String> compTypes = new HashMap<>();
        private final Map<String, String> userByName = new HashMap<>();
        private final Map<String, String> deptByName = new HashMap<>();
        private final Map<String, Map<String, String>> relationIds = new HashMap<>();

        ImportRefResolver(DynamicForm form, List<JSONObject> schemaFields) {
            for (JSONObject schema : schemaFields) {
                String field = schema.getString("field");
                String type = StringUtils.defaultString(schema.getString("type"));
                if (StringUtils.isNotEmpty(field) && StringUtils.isNotEmpty(type)
                        && (type.startsWith("user-") || type.startsWith("dept-") || type.startsWith("relation"))) {
                    compTypes.put(field, type);
                }
            }
            try {
                CommonResult<List<SystemUserDTO>> users =
                        remoteUserService.findUserByOrg(form.getOrganizationId(), "");
                if (users.getCode() == CommonResult.SUCCESS && users.getData() != null) {
                    for (SystemUserDTO user : users.getData()) {
                        if (StringUtils.isNotEmpty(user.getNickName())) {
                            userByName.putIfAbsent(user.getNickName(), String.valueOf(user.getId()));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("导入人员反查字典失败 form={}", form.getTableCode(), e);
            }
            try {
                com.privernal.system.filter.SystemDepartmentFilterDTO deptFilter =
                        new com.privernal.system.filter.SystemDepartmentFilterDTO();
                deptFilter.setOrganizationId(form.getOrganizationId());
                CommonResult<List<SystemDepartmentDTO>> depts =
                        remoteUserService.findDeptListByOrgId(deptFilter, "");
                if (depts.getCode() == CommonResult.SUCCESS && depts.getData() != null) {
                    for (SystemDepartmentDTO dept : depts.getData()) {
                        if (StringUtils.isNotEmpty(dept.getDeptName())) {
                            deptByName.putIfAbsent(dept.getDeptName(), String.valueOf(dept.getId()));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("导入部门反查字典失败 form={}", form.getTableCode(), e);
            }
            // 关联列：id→label 映射反转即 label→id（复用导出的关联查询，含显示字段三级回退）
            for (JSONObject schema : schemaFields) {
                String field = schema.getString("field");
                String type = StringUtils.defaultString(schema.getString("type"));
                if (!type.startsWith("relation") || schema.getJSONObject("props") == null) {
                    continue;
                }
                JSONObject relation = schema.getJSONObject("props").getJSONObject("relation");
                if (relation == null || StringUtils.isEmpty(relation.getString("formCode"))) {
                    continue;
                }
                Map<String, String> idToLabel = buildRelationLabelMap(form, relation);
                Map<String, String> labelToId = new HashMap<>();
                idToLabel.forEach((id, label) -> labelToId.putIfAbsent(label, id));
                relationIds.put(field, labelToId);
            }
        }

        /** 行内组件级反解析：就地替换 row 中命中的值为存储 id */
        void resolve(Map<String, Object> row) {
            for (Map.Entry<String, String> entry : compTypes.entrySet()) {
                String code = entry.getKey();
                if (!row.containsKey(code)) {
                    continue;
                }
                List<String> parts = splitMultiValues(row.get(code));
                if (parts.isEmpty()) {
                    continue;
                }
                Map<String, String> dict;
                if (entry.getValue().startsWith("user-")) {
                    dict = userByName;
                } else if (entry.getValue().startsWith("dept-")) {
                    dict = deptByName;
                } else {
                    dict = relationIds.get(code);
                }
                if (dict == null || dict.isEmpty()) {
                    continue;
                }
                boolean changed = false;
                List<String> resolved = new ArrayList<>();
                for (String part : parts) {
                    String mapped = dict.get(part);
                    if (mapped != null) {
                        changed = true;
                        resolved.add(mapped);
                    } else {
                        // 未命中：可能本身已是 id/存储原值，保留
                        resolved.add(part);
                    }
                }
                if (changed) {
                    row.put(code, String.join(",", resolved));
                }
            }
        }
    }

    private ImportRefResolver buildImportResolver(DynamicForm form, List<JSONObject> schemaFields) {
        return new ImportRefResolver(form, schemaFields);
    }


    private DynamicForm requireForm(String formId) {
        DynamicForm form = linkageSupport.loadForm(formId);
        if (form == null || StringUtils.isEmpty(form.getTableCode())) {
            throw new IllegalArgumentException("表单不存在：" + formId);
        }
        return form;
    }

    private DynamicFormIoConfig loadConfig(String formId) {
        DynamicFormIoConfig config = ioConfigMapper.selectOne(Wrappers.<DynamicFormIoConfig>lambdaQuery()
                .eq(DynamicFormIoConfig::getFormId, formId));
        if (config == null) {
            config = new DynamicFormIoConfig();
            config.setExportEnabled("N");
            config.setImportEnabled("N");
            config.setSensitivePolicy("MASK");
        }
        return config;
    }

    /** 导出/模板列集：export_columns 非空用它（软失败跳过失效码），否则 show_list=1 列 + id */
    private List<DynamicFormColumn> visibleColumns(String formId, String exportColumnsJson) {
        List<DynamicFormColumn> all = formColumnMapper.selectList(Wrappers.<DynamicFormColumn>lambdaQuery()
                .eq(DynamicFormColumn::getFormId, formId));
        List<DynamicFormColumn> result = new ArrayList<>();
        Set<String> picked = new HashSet<>();
        if (StringUtils.isNotEmpty(exportColumnsJson)) {
            try {
                JSON.parseArray(exportColumnsJson).forEach(code -> {
                    String text = String.valueOf(code);
                    for (DynamicFormColumn column : all) {
                        if (text.equals(column.getFiledCode()) && picked.add(text)) {
                            result.add(column);
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("export_columns 解析失败，回落 show_list：{}", exportColumnsJson);
            }
        }
        if (result.isEmpty()) {
            for (DynamicFormColumn column : all) {
                if (column.getShowList() != null && column.getShowList() == 1
                        && StringUtils.isNotEmpty(column.getFiledCode()) && picked.add(column.getFiledCode())) {
                    result.add(column);
                }
            }
        }
        return result;
    }

    /** 表单的输入字段 schema（required/options 来源） */
    private List<JSONObject> inputFields(DynamicForm form) {
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
            if (child != null && StringUtils.isNotEmpty(child.getString("field"))) {
                result.add(child);
            }
        }
        return result;
    }

    private JSONObject schemaField(List<JSONObject> schemaFields, String code) {
        if (code == null) {
            return null;
        }
        for (JSONObject field : schemaFields) {
            if (code.equalsIgnoreCase(field.getString("field"))) {
                return field;
            }
        }
        return null;
    }

    /** 模板/导出表头：优先字段名称（友好），查重按编码 */
    private String displayCode(DynamicFormColumn column, List<JSONObject> schemaFields) {
        return StringUtils.isNotEmpty(column.getFiledName()) ? column.getFiledName() : column.getFiledCode();
    }

    private DynamicFormColumn columnByCode(List<DynamicFormColumn> columns, String code) {
        for (DynamicFormColumn column : columns) {
            if (column.getFiledCode().equals(code)) {
                return column;
            }
        }
        return null;
    }

    private String optionsText(JSONObject schema) {
        if (schema == null || schema.getJSONObject("props") == null) {
            return "";
        }
        Object options = schema.getJSONObject("props").get("options");
        return options == null ? "" : JSON.toJSONString(options);
    }

    /**
     * 选项值 → 文案（props.options 的 label/value 对）。
     * 多值形态（统一信封 / 裸数组 / 逗号串）逐个回译并用顿号连接，
     * 避免把 {@code {"v":["a"],"src":{...}}} 整段写进单元格。
     */
    private Object translateOption(JSONObject schema, Object value) {
        if (schema == null || schema.getJSONObject("props") == null || value == null) {
            return value;
        }
        Object options = schema.getJSONObject("props").get("options");
        if (!(options instanceof JSONArray array)) {
            return value;
        }
        Map<String, String> labelByValue = new java.util.LinkedHashMap<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject option = array.getJSONObject(i);
            if (option != null && option.get("value") != null) {
                labelByValue.put(String.valueOf(option.get("value")), String.valueOf(option.get("label")));
            }
        }
        List<String> values = JsonValueCodec.values(String.valueOf(value));
        if (values.isEmpty()) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        for (String item : values) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            String label = labelByValue.get(item);
            sb.append(label != null && !label.isEmpty() ? label : item);
        }
        return sb.toString();
    }

    private String readCellAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            switch (cell.getCellType()) {
                case STRING -> {
                    return cell.getStringCellValue().trim();
                }
                case NUMERIC -> {
                    double number = cell.getNumericCellValue();
                    if (number == Math.floor(number) && !cell.getCellStyle().getDataFormatString().contains("0.00")) {
                        return String.valueOf((long) number);
                    }
                    return new BigDecimal(number).toPlainString();
                }
                case BOOLEAN -> {
                    return String.valueOf(cell.getBooleanCellValue());
                }
                case FORMULA -> {
                    try {
                        return cell.getStringCellValue().trim();
                    } catch (Exception e) {
                        return new BigDecimal(cell.getNumericCellValue()).toPlainString();
                    }
                }
                default -> {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Object convertValue(DynamicFormColumn column, String raw, JSONObject schema) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String type = column == null || StringUtils.isEmpty(column.getFiledType()) ? "TEXT" : column.getFiledType().toUpperCase();
        try {
            if (type.contains("INT") || type.contains("NUMBER") || type.contains("DECIMAL")
                    || type.contains("DOUBLE") || type.contains("FLOAT")) {
                return new BigDecimal(raw).stripTrailingZeros().toPlainString();
            }
            if (type.contains("DATE") || type.contains("TIME")) {
                for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd"}) {
                    try {
                        return new SimpleDateFormat(pattern).parse(raw);
                    } catch (ParseException ignored) {
                        // 尝试下一个格式
                    }
                }
                throw new IllegalArgumentException("日期格式不合法：" + raw + "（应为 yyyy-MM-dd HH:mm:ss）");
            }
            return raw;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数值格式不合法：" + raw);
        }
    }

    private String checkRequired(Map<String, Object> row, List<JSONObject> schemaFields, List<DynamicFormColumn> columns) {
        for (DynamicFormColumn column : columns) {
            if (DynamicFormColumn.isSystemField(column.getFiledCode())) {
                continue;
            }
            JSONObject schema = schemaField(schemaFields, column.getFiledCode());
            if (schema != null && Boolean.TRUE.equals(schema.getBoolean("required"))) {
                Object value = row.get(column.getFiledCode());
                if (value == null || String.valueOf(value).isEmpty()) {
                    return column.getFiledName() + "(" + column.getFiledCode() + ")";
                }
            }
        }
        return null;
    }

    private List<String> uniqueColumnCodes(List<DynamicFormColumn> columns) {
        List<String> codes = new ArrayList<>();
        for (DynamicFormColumn column : columns) {
            if ("Y".equals(column.getUniqueFlag()) && !DynamicFormColumn.isSystemField(column.getFiledCode())) {
                codes.add(column.getFiledCode());
            }
        }
        return codes;
    }

    /** 文件内 + 库内唯一查重（返回首个冲突的描述；库内查询绑定值，杜绝拼接） */
    private String findDuplicate(ImportTask task, List<String> uniqueCodes, Map<String, Object> row, Set<String> seenInFile) {
        for (String code : uniqueCodes) {
            Object value = row.get(code);
            if (value == null || String.valueOf(value).isEmpty()) {
                continue;
            }
            String fileKey = code + "=" + value;
            if (!seenInFile.add(fileKey)) {
                return fileKey + "（文件内重复）";
            }
            if (locateId(task, uniqueCodes, row) != null) {
                return fileKey;
            }
        }
        return null;
    }

    /** 按唯一列定位已存在行 id（值绑定查询；返回首个命中） */
    private String locateId(ImportTask task, List<String> uniqueCodes, Map<String, Object> row) {
        for (String code : uniqueCodes) {
            Object value = row.get(code);
            if (value == null || String.valueOf(value).isEmpty()) {
                continue;
            }
            List<Map<String, Object>> hits = queryBoundRows(task.database, task.tableCode,
                    "SELECT `id` FROM `" + task.tableCode + "` WHERE `" + code + "` = #{params.p1} "
                            + "AND deleted_flag = 'N' LIMIT 1", value);
            if (!hits.isEmpty() && hits.get(0).get("id") != null) {
                return String.valueOf(hits.get(0).get("id"));
            }
        }
        return null;
    }

    private List<Map<String, Object>> queryBoundRows(String database, String tableCode, String sql, Object value) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
            DynamicDataSourceContextHolder.push(database);
            return linkageQueryMapper.selectRows(sql, Map.of("p1", String.valueOf(value)));
        } finally {
            DynamicDataSourceContextHolder.poll();
            if (oldIgnore == null) {
                OrganizationContextHolder.clear();
            } else {
                OrganizationContextHolder.setIgnore(Boolean.TRUE.equals(oldIgnore));
            }
        }
    }

    /** 列表过滤条件包装（export 只透传分页外的过滤键） */
    private static class PageQuery {
        final Map<String, Object> source = new LinkedHashMap<>();
        final Map<String, Object> filter = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        PageQuery(Map<String, Object> query) {
            Object filterObj = query.get("filter");
            Map<String, Object> raw = filterObj instanceof Map ? (Map<String, Object>) filterObj : query;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if ("pageNum".equals(entry.getKey()) || "pageSize".equals(entry.getKey())
                        || "orderBys".equals(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                source.put(entry.getKey(), entry.getValue());
                filter.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 导入任务内存态 */
    private static class ImportTask {
        String taskId;
        String formId;
        String tableCode;
        String database;
        int total;
        volatile boolean finished;
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger skipped = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Map<String, Object>> rows;
        DynamicFormIoConfig config;
        DynamicForm form;
        List<DynamicFormColumn> columns;
        List<JSONObject> schemaFields;
    }
}
