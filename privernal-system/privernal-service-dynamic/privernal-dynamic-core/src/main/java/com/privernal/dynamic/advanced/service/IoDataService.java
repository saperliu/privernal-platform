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
import com.privernal.dynamic.advanced.domain.DynamicIoImportTask;
import com.privernal.dynamic.advanced.engine.AdvancedKillSwitch;
import com.privernal.dynamic.advanced.engine.ConditionEvaluator;
import com.privernal.dynamic.advanced.engine.DataWriteAttributes;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.engine.SafeConditionSql;
import com.privernal.dynamic.advanced.engine.SensitiveDataMask;
import com.privernal.dynamic.advanced.mapper.DynamicFormIoConfigMapper;
import com.privernal.dynamic.advanced.mapper.DynamicIoImportTaskMapper;
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
import java.lang.management.ManagementFactory;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

/**
 * 表单数据导入导出运行时（T1 + R2-19 导入导出增强）。
 * <ul>
 *   <li>模板：两 Sheet（数据头+示例行 / 字段说明），日期列统一文本格式；</li>
 *   <li>导入（R2-19 改造）：两阶段——preview 解析+跨环境列结构预检（落 PENDING_MAPPING 任务）、
 *       start 按自定义列映射启动；任务全量持久化 {@link DynamicIoImportTask}，
 *       重启可续（心跳过期后由 IoImportTaskMaintainer CAS 认领断点续跑）、多实例可见（进度/记录读库）；
 *       行数据以「表头/字段编码为键的原始字符串」入库，类型转换在执行期做——续跑经 JSON 往返与首跑一致；
 *       类型/必填/唯一校验，重复键策略 SKIP/OVERWRITE/REJECT；sourceTag=IMPORT，默认抑制联动事件与
 *       DATE_FIELD 注册（§4.2），由「重算到期任务」/每日对账补注册；</li>
 *   <li>导出：分页 1000 循环流式写出，敏感列按 io_config.sensitive_policy 脱敏/剔除，
 *       选项类字段按 metaData props.options 回翻译，@Log 审计；</li>
 *   <li>kill switch：io 闸关停时全部拒绝（T9/D7）。</li>
 * </ul>
 */
@Slf4j
@Service
public class IoDataService {

    /** 失败行明细落库封顶：超出部分只计数不再追加（报告页提示已截断） */
    private static final int ERROR_CAP = 5000;

    /** 进度/心跳批量落库节流：行数或时间任一达到即刷库 */
    private static final int FLUSH_ROWS = 20;
    private static final long FLUSH_MILLIS = 2000L;

    /** 导入执行线程（单实例顺序执行足够；多实例靠 DB 认领保证同一任务只有一个执行者） */
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "io-import-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** 本实例执行者标识（hostname+pid+启动时间，仿调度实例注册口径） */
    private final String workerId = buildWorkerId();

    /** 执行者标识（IoImportTaskMaintainer 认领 stale 任务时写同一 worker_id） */
    public String workerId() {
        return workerId;
    }

    private static String buildWorkerId() {
        try {
            String host = java.net.InetAddress.getLocalHost().getHostName();
            return host + "-" + ProcessHandle.current().pid() + "-"
                    + ManagementFactory.getRuntimeMXBean().getStartTime();
        } catch (Exception e) {
            return "unknown-" + ProcessHandle.current().pid();
        }
    }

    /** 优雅停机：等在跑的行处理落库；未完成任务靠心跳过期被重新认领续跑 */
    @PreDestroy
    public void shutdownExecutor() {
        importExecutor.shutdown();
        try {
            if (!importExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                importExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            importExecutor.shutdownNow();
        }
    }

    @Autowired
    private DynamicFormColumnMapper formColumnMapper;

    @Autowired
    private DynamicFormIoConfigMapper ioConfigMapper;

    @Autowired
    private DynamicIoImportTaskMapper ioImportTaskMapper;

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
                row.createCell(3).setCellValue(isRequiredField(schema) ? "是" : "否");
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

    /**
     * 预检导入（两阶段第一步）：解析 Excel 表头与原始行，生成跨环境列结构预检报告，
     * 任务以 PENDING_MAPPING 落库（不执行）。行以「表头为键的原始字符串」存 rows_json，
     * 类型转换在执行期做——续跑经 JSON 往返后与首跑行为一致。
     *
     * @return taskId / total / headers / mapping（自动匹配建议）/ precheck（预检报告）/ fields（可映射字段）
     */
    public Map<String, Object> previewImport(String formId, byte[] fileBytes) throws Exception {
        if (!killSwitch.ioEnabled()) {
            throw new IllegalStateException("导入导出能力已被平台关停（kill switch）");
        }
        DynamicForm form = requireForm(formId);
        DynamicFormIoConfig config = loadConfig(formId);
        if (!"Y".equals(config.getImportEnabled())) {
            throw new IllegalArgumentException("该表单未开启数据导入");
        }
        List<DynamicFormColumn> columns = visibleColumns(formId, null);
        List<JSONObject> schemaFields = inputFields(form);
        // 解析（同步做，文件异常立刻反馈；行处理异步）
        List<String> headers = new ArrayList<>();
        List<Map<String, Object>> rows = parseSheet(fileBytes, headers);
        int limit = config.getImportLimit() == null ? 5000 : config.getImportLimit();
        if (rows.size() > limit) {
            throw new IllegalArgumentException("导入行数 " + rows.size() + " 超过单次上限 " + limit);
        }
        JSONObject precheck = buildPrecheck(headers, rows, columns, schemaFields);
        Map<String, String> mapping = suggestMapping(headers, columns, schemaFields);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        DynamicIoImportTask entity = new DynamicIoImportTask();
        entity.setId(taskId);
        entity.setFormId(formId);
        entity.setTableCode(form.getTableCode());
        entity.setDatabaseName(linkageSupport.resolveDatabase(form.getOrganizationId()));
        entity.setStatus(DynamicIoImportTask.STATUS_PENDING_MAPPING);
        entity.setTotal(rows.size());
        entity.setProcessed(0);
        entity.setSuccess(0);
        entity.setSkipped(0);
        entity.setFailed(0);
        entity.setHeadersJson(JSON.toJSONString(headers));
        entity.setMappingJson(JSON.toJSONString(mapping));
        entity.setPrecheckJson(precheck.toJSONString());
        entity.setRowsJson(JSON.toJSONString(rows));
        entity.setErrorsJson("[]");
        entity.setConfigJson(configSnapshot(config));
        // 归属挂表单（预检无业务库访问，EntityHandler 会话兜底；显式赋值保证与表单同组织）
        entity.setOrganizationId(form.getOrganizationId());
        entity.setDepartmentId(form.getDepartmentId());
        ioImportTaskMapper.insert(entity);
        log.info("导入预检 form={} rows={} unmatched={} missingRequired={} operator={}",
                formId, rows.size(), precheck.getJSONArray("unmatched") == null ? 0 : precheck.getJSONArray("unmatched").size(),
                precheck.getJSONArray("missingRequired") == null ? 0 : precheck.getJSONArray("missingRequired").size(),
                SecurityUtils.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("total", rows.size());
        result.put("headers", headers);
        result.put("mapping", mapping);
        result.put("precheck", precheck);
        result.put("fields", fieldSummaries(columns, schemaFields));
        return result;
    }

    /**
     * 确认自定义列映射并启动导入（两阶段第二步）：校验映射 → 按映射重排 rows_json（键改字段编码，
     * 重算 total）→ CAS 置 RUNNING → 提交本实例执行。行数据在库，任何实例发起/续跑等价。
     */
    public Map<String, Object> startImport(String taskId, Map<String, String> mapping) {
        if (!killSwitch.ioEnabled()) {
            throw new IllegalStateException("导入导出能力已被平台关停（kill switch）");
        }
        DynamicIoImportTask entity = requireTask(taskId);
        if (!DynamicIoImportTask.STATUS_PENDING_MAPPING.equals(entity.getStatus())) {
            throw new IllegalStateException("任务状态为 " + entity.getStatus() + "，不能重复启动");
        }
        DynamicForm form = requireForm(entity.getFormId());
        DynamicFormIoConfig config = loadConfig(entity.getFormId());
        if (!"Y".equals(config.getImportEnabled())) {
            throw new IllegalArgumentException("该表单未开启数据导入");
        }
        List<DynamicFormColumn> columns = visibleColumns(entity.getFormId(), null);
        // 映射校验：目标字段必须存在且不重复；空串=忽略该列
        Map<String, String> clean = new LinkedHashMap<>();
        Set<String> targets = new HashSet<>();
        if (mapping != null) {
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                String header = entry.getKey();
                String code = entry.getValue() == null ? "" : entry.getValue().trim();
                if (StringUtils.isEmpty(header)) {
                    continue;
                }
                if (!code.isEmpty()) {
                    if (columnByCode(columns, code) == null) {
                        throw new IllegalArgumentException("映射目标字段不存在：" + code);
                    }
                    if (!targets.add(code)) {
                        throw new IllegalArgumentException("多个列映射到同一字段：" + code);
                    }
                }
                clean.put(header, code);
            }
        }
        // 应用映射：rows 改以字段编码为键（值仍是原始字符串），并重算 total
        List<Map<String, Object>> rows = parseStoredRows(entity.getRowsJson());
        List<Map<String, Object>> mapped = new ArrayList<>();
        int total = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> out = new LinkedHashMap<>();
            boolean empty = true;
            for (Map.Entry<String, String> entry : clean.entrySet()) {
                String code = entry.getValue();
                if (code.isEmpty()) {
                    continue;
                }
                Object raw = row.get(entry.getKey());
                if (raw != null && !String.valueOf(raw).isEmpty()) {
                    empty = false;
                }
                out.put(code, raw);
            }
            if (!empty) {
                mapped.add(out);
                total++;
            }
        }
        DynamicIoImportTask update = new DynamicIoImportTask();
        update.setRowsJson(JSON.toJSONString(mapped));
        update.setMappingJson(JSON.toJSONString(clean));
        update.setTotal(total);
        update.setConfigJson(configSnapshot(config));
        ioImportTaskMapper.update(update, Wrappers.<DynamicIoImportTask>lambdaUpdate()
                .eq(DynamicIoImportTask::getId, taskId));
        // CAS 置 RUNNING：并发重复 start 只有一个成功（另一实例同任务也不会双跑）
        int claimed = ioImportTaskMapper.startPendingTask(taskId, workerId, new Date());
        if (claimed == 0) {
            throw new IllegalStateException("任务已启动或状态已变化，请刷新导入记录");
        }
        importExecutor.submit(() -> {
            try {
                runTask(taskId);
            } catch (Throwable t) {
                log.error("导入任务执行逃逸异常（runTask 外抛，已兜底）taskId={}", taskId, t);
            }
        });
        log.info("导入启动 form={} taskId={} total={} mapping={} operator={}",
                entity.getFormId(), taskId, total, clean.size(), SecurityUtils.getUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("total", total);
        return result;
    }

    /**
     * 兼容旧调用方的一步式导入：预检 → 自动映射 → 立即启动。
     * 保留原因：老前端/移动端/脚本按旧契约调用；新 UI 走 preview + start 两阶段（可改映射）。
     */
    public Map<String, Object> startImport(String formId, byte[] fileBytes) throws Exception {
        Map<String, Object> preview = previewImport(formId, fileBytes);
        @SuppressWarnings("unchecked")
        Map<String, String> mapping = (Map<String, String>) preview.get("mapping");
        return startImport(String.valueOf(preview.get("taskId")), mapping);
    }

    /**
     * 导入执行（首跑与续跑共用）：从库读任务，processed 为断点——跳过已处理行，
     * 重建文件内查重集合后继续。计数与失败明细每 20 行/2 秒落库（多实例读库可见进度）。
     * 全方法体（含首条任务行查询）都在忽略上下文与 try/catch 内：executor.submit 的 Future
     * 会吞掉逃逸 Throwable（R2-19 实测：无会话查询抛错被吞 → 任务卡 RUNNING 被反复认领）。
     */
    private void runTask(String taskId) {
        // 导入线程缺会话：任务行/表单/列元数据查询与业务落库都需绕过会话级过滤（§5.5 同口径），
        // 行级数据范围不适用
        OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
        DynamicIoImportTask entity = null;
        RunState state = new RunState(new DynamicIoImportTask());
        try {
            entity = ioImportTaskMapper.selectById(taskId);
            if (entity == null) {
                log.error("导入任务执行失败：任务不存在 taskId={}", taskId);
                return;
            }
            state = new RunState(entity);
            DynamicForm form = requireForm(entity.getFormId());
            JSONObject configSnap = StringUtils.isEmpty(entity.getConfigJson())
                    ? new JSONObject() : JSON.parseObject(entity.getConfigJson());
            String database = StringUtils.isNotEmpty(entity.getDatabaseName())
                    ? entity.getDatabaseName() : linkageSupport.resolveDatabase(form.getOrganizationId());
            List<DynamicFormColumn> columns = visibleColumns(entity.getFormId(), null);
            List<JSONObject> schemaFields = inputFields(form);
            List<Map<String, Object>> rows = parseStoredRows(entity.getRowsJson());
            String dup = StringUtils.isEmpty(configSnap.getString("dupStrategy")) ? "SKIP" : configSnap.getString("dupStrategy");
            boolean strict = !"N".equals(configSnap.getString("strictValidate"));
            boolean runScript = "Y".equals(configSnap.getString("runBackscript"));
            String importFlowMode = configSnap.getString("importFlowMode");
            JSONObject metaData = form.getMetaData();
            String backScript = null;
            if (runScript && metaData != null && metaData.getJSONObject("backScript") != null) {
                backScript = metaData.getJSONObject("backScript").getString("script");
            }
            List<String> uniqueCodes = uniqueColumnCodes(columns);
            // 导入反向解析：导出文件里人员/部门/关联列是文案（昵称/名称/displayField 值），
            // 写库前还原为存储 id；值本身已是 id 时保持不变
            ImportRefResolver refResolver = buildImportResolver(form, schemaFields);

            // 断点续跑：跳过已处理行，并从已处理行重建文件内查重集合（唯一键值）
            int offset = entity.getProcessed() == null ? 0 : Math.min(entity.getProcessed(), rows.size());
            Set<String> seenInFile = new HashSet<>();
            for (int i = 0; i < offset && i < rows.size(); i++) {
                markSeenInFile(seenInFile, uniqueCodes, rows.get(i));
            }
            state.lastFlushTime = System.currentTimeMillis();
            state.lastFlushProcessed = offset;
            for (int i = offset; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                int rowIndex = state.processed.get() + 1;
                try {
                    if (row.isEmpty()) {
                        state.processed.incrementAndGet();
                        continue;
                    }
                    // 类型转换：rows_json 存原始字符串，首跑/续跑都从这里转（口径一致）
                    Map<String, Object> converted = convertRow(columns, row);
                    refResolver.resolve(converted);
                    // 必填校验
                    if (strict) {
                        String missing = checkRequired(converted, schemaFields, columns);
                        if (missing != null) {
                            throw new IllegalArgumentException("必填字段缺失：" + missing);
                        }
                    }
                    // id 有值 → 更新；唯一列查重
                    Object rowId = converted.get("id");
                    if (rowId == null || String.valueOf(rowId).isEmpty()) {
                        converted.remove("id");
                        String dupKey = findDuplicate(database, entity.getTableCode(), uniqueCodes, converted, seenInFile);
                        if (dupKey != null) {
                            switch (dup) {
                                case "REJECT" -> throw new IllegalArgumentException(
                                        "唯一键冲突（" + dupKey + "），整批拒绝（dup_strategy=REJECT）");
                                case "OVERWRITE" -> {
                                    String existId = locateId(database, entity.getTableCode(), uniqueCodes, converted);
                                    if (existId != null) {
                                        converted.put("id", existId);
                                    } else {
                                        state.skipped.incrementAndGet();
                                        state.errors.add("行 " + rowIndex + "：文件内重复，跳过");
                                        // processed 由行级 finally 统一递增（旧实现此处+finally 双计，R2-19 修正）
                                        flushIfDue(state, taskId);
                                        continue;
                                    }
                                }
                                default -> {
                                    state.skipped.incrementAndGet();
                                    state.errors.add("行 " + rowIndex + "：唯一键冲突（" + dupKey + "），按 SKIP 跳过");
                                    // processed 由行级 finally 统一递增（旧实现此处+finally 双计，R2-19 修正）
                                    flushIfDue(state, taskId);
                                    continue;
                                }
                            }
                        }
                    }
                    // 数据标题 & 工作流表单落草稿（import_flow_mode=NONE，§4.1）
                    if (metaData != null) {
                        String dataTitle = FormDataTitleUtils.buildTitle(metaData, converted);
                        if (StringUtils.isNotEmpty(dataTitle)) {
                            converted.put("title", dataTitle);
                        }
                    }
                    if (form.getIsWorkflow() != null && form.getIsWorkflow() == 1
                            && !"AUTO_START".equals(importFlowMode)) {
                        converted.put("status", 0);
                    }
                    // 落库：sourceTag=IMPORT（联动/DATE_FIELD 注册按 trigger_mode 抑制，§4.2）；
                    // 导入线程无会话，代理身份=表单创建人（动态表 created_by NOT NULL）
                    DataWriteAttributes.Snapshot previous = DataWriteAttributes.push(
                            DataWriteAttributes.SOURCE_IMPORT, 0,
                            StringUtils.isNotEmpty(form.getCreatedBy()) ? form.getCreatedBy() : "system-import",
                            form.getDepartmentId(), form.getOrganizationId());
                    try {
                        if (StringUtils.isNotEmpty(backScript)) {
                            commonService.saveTableData(database, entity.getTableCode(), backScript, converted, true);
                        } else {
                            dynamicTableService.saveOrUpdate(database, entity.getTableCode(), converted);
                        }
                    } finally {
                        DataWriteAttributes.restore(previous);
                    }
                    state.success.incrementAndGet();
                } catch (Exception e) {
                    state.failed.incrementAndGet();
                    state.errors.add("行 " + rowIndex + "：" + e.getMessage());
                    log.warn("导入行失败 form={} row={} error={}", entity.getFormId(), rowIndex, e.getMessage());
                } finally {
                    state.processed.incrementAndGet();
                }
                flushIfDue(state, taskId);
            }
            finishTask(state, taskId, null);
        } catch (Exception e) {
            log.error("导入任务异常 taskId={} form={}", taskId, entity == null ? null : entity.getFormId(), e);
            state.errors.add("任务异常终止：" + e.getMessage());
            finishTask(state, taskId, e.getMessage());
        } finally {
            OrganizationContextHolder.clear();
        }
        log.info("导入任务完成 taskId={} form={} total={} success={} skipped={} failed={} status={}",
                taskId, entity == null ? null : entity.getFormId(), state.totalOf(), state.success.get(),
                state.skipped.get(), state.failed.get(), state.terminalStatus);
    }

    /**
     * 断点续跑入口（IoImportTaskMaintainer 认领 stale 任务后调用）：行数据/断点都在库，直接重入执行循环。
     * Throwable 兜底：Future.submit 不取回结果时逃逸异常被静默吞掉（无日志），必须在此显式落日志。
     */
    public void resumeTask(String taskId) {
        log.info("导入任务断点续跑 taskId={} worker={}", taskId, workerId);
        importExecutor.submit(() -> {
            try {
                runTask(taskId);
            } catch (Throwable t) {
                log.error("导入任务执行逃逸异常（runTask 外抛，已兜底）taskId={}", taskId, t);
            }
        });
    }

    /** 执行期可变状态：内存计数（从 DB 断点播种）+ 节流落库 */
    private static class RunState {
        final AtomicInteger processed;
        final AtomicInteger success;
        final AtomicInteger skipped;
        final AtomicInteger failed;
        final List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        long lastFlushTime = 0L;
        int lastFlushProcessed = 0;
        String terminalStatus;

        RunState(DynamicIoImportTask entity) {
            processed = new AtomicInteger(entity.getProcessed() == null ? 0 : entity.getProcessed());
            success = new AtomicInteger(entity.getSuccess() == null ? 0 : entity.getSuccess());
            skipped = new AtomicInteger(entity.getSkipped() == null ? 0 : entity.getSkipped());
            failed = new AtomicInteger(entity.getFailed() == null ? 0 : entity.getFailed());
        }

        int totalOf() {
            return processed.get();
        }
    }

    /** 每 20 行或 2 秒落一次进度/心跳/失败明细（落库失败不中断导入，下个窗口重试） */
    private void flushIfDue(RunState state, String taskId) {
        long now = System.currentTimeMillis();
        if (state.processed.get() - state.lastFlushProcessed < FLUSH_ROWS
                && now - state.lastFlushTime < FLUSH_MILLIS) {
            return;
        }
        state.lastFlushProcessed = state.processed.get();
        state.lastFlushTime = now;
        flushProgress(taskId, state, null);
    }

    /** 进度/心跳/失败明细落库；statusOverride 非空时同时改状态（结束态）。落库无会话（导入线程/续跑线程）也要能写：组织过滤忽略 */
    private void flushProgress(String taskId, RunState state, String statusOverride) {
        boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            if (!oldIgnore) {
                OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
            }
            DynamicIoImportTask update = new DynamicIoImportTask();
            update.setProcessed(state.processed.get());
            update.setSuccess(state.success.get());
            update.setSkipped(state.skipped.get());
            update.setFailed(state.failed.get());
            update.setErrorsJson(JSON.toJSONString(cappedErrors(state.errors)));
            update.setHeartbeatTime(new Date());
            if (statusOverride != null) {
                update.setStatus(statusOverride);
            }
            ioImportTaskMapper.update(update, Wrappers.<DynamicIoImportTask>lambdaUpdate()
                    .eq(DynamicIoImportTask::getId, taskId));
        } catch (Exception e) {
            log.warn("导入进度落库失败 taskId={}（下个节流窗口重试）", taskId, e);
        } finally {
            if (!oldIgnore) {
                OrganizationContextHolder.clear();
            }
        }
    }

    /** 收尾：终态 + 结束时间；清心跳（已结束任务不再参与 stale 认领扫描）。上下文口径同 {@link #flushProgress} */
    private void finishTask(RunState state, String taskId, String taskError) {
        state.terminalStatus = taskError == null ? DynamicIoImportTask.STATUS_FINISHED : DynamicIoImportTask.STATUS_FAILED;
        boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            if (!oldIgnore) {
                OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
            }
            DynamicIoImportTask update = new DynamicIoImportTask();
            update.setProcessed(state.processed.get());
            update.setSuccess(state.success.get());
            update.setSkipped(state.skipped.get());
            update.setFailed(state.failed.get());
            update.setErrorsJson(JSON.toJSONString(cappedErrors(state.errors)));
            update.setStatus(state.terminalStatus);
            update.setFinishedTime(new Date());
            if (taskError != null) {
                update.setRemark(taskError.length() <= 900 ? taskError : taskError.substring(0, 900));
            }
            ioImportTaskMapper.update(update, Wrappers.<DynamicIoImportTask>lambdaUpdate()
                    .eq(DynamicIoImportTask::getId, taskId)
                    .set(DynamicIoImportTask::getHeartbeatTime, null));
        } catch (Exception e) {
            log.error("导入任务结果落库失败 taskId={}", taskId, e);
        } finally {
            if (!oldIgnore) {
                OrganizationContextHolder.clear();
            }
        }
    }

    private List<String> cappedErrors(List<String> errors) {
        synchronized (errors) {
            if (errors.size() <= ERROR_CAP) {
                return new ArrayList<>(errors);
            }
            List<String> capped = new ArrayList<>(errors.subList(0, ERROR_CAP));
            capped.add("……失败明细超过 " + ERROR_CAP + " 条已截断，失败行总数见 failed 计数");
            return capped;
        }
    }

    /** 文件内查重集合登记（唯一列有值才登记；续跑时对已处理行回放同一口径） */
    private void markSeenInFile(Set<String> seenInFile, List<String> uniqueCodes, Map<String, Object> row) {
        for (String code : uniqueCodes) {
            Object value = row.get(code);
            if (value != null && !String.valueOf(value).isEmpty()) {
                seenInFile.add(code + "=" + value);
            }
        }
    }

    /**
     * 类型转换：键=字段编码，值=原始字符串。convertValue 对空值返回 null——与旧链路一致
     * （旧解析对空单元格 put(code, null)，checkRequired/buildTitle 均按空值口径处理）。
     */
    private Map<String, Object> convertRow(List<DynamicFormColumn> columns, Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            DynamicFormColumn column = columnByCode(columns, entry.getKey());
            String raw = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            out.put(entry.getKey(), convertValue(column, raw));
        }
        return out;
    }

    /** 解析 rows_json（PENDING_MAPPING=表头键；RUNNING 后=字段编码键，两阶段统一用同一读取） */
    private List<Map<String, Object>> parseStoredRows(String rowsJson) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (StringUtils.isEmpty(rowsJson)) {
            return rows;
        }
        JSONArray array = JSON.parseArray(rowsJson);
        if (array == null) {
            return rows;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject row = array.getJSONObject(i);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** Excel 解析（不转换、不匹配）：行以「表头原文」为键的原始字符串；示例行跳过 */
    private List<Map<String, Object>> parseSheet(byte[] fileBytes, List<String> headersOut) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheet("数据") != null ? workbook.getSheet("数据") : workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("模板缺少表头行");
            }
            List<String> headerTexts = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String text = readCellAsString(header.getCell(i));
                headerTexts.add(text == null ? "" : text.trim());
            }
            headersOut.addAll(headerTexts);
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
                for (int c = 0; c < headerTexts.size(); c++) {
                    String text = headerTexts.get(c);
                    if (text.isEmpty()) {
                        continue;
                    }
                    String raw = readCellAsString(row.getCell(c));
                    if (raw != null && raw.isEmpty()) {
                        raw = null;
                    }
                    if (raw != null) {
                        empty = false;
                    }
                    data.put(text, raw);
                }
                if (!empty) {
                    rows.add(data);
                }
            }
        }
        return rows;
    }

    /**
     * 自动匹配建议（表头 → 字段编码）：字段名称（模板表头口径）与字段编码双重对照，
     * 未命中映射为空串（忽略）。跨环境导出的文件表头=原环境字段名，名称对照是主路径。
     */
    private Map<String, String> suggestMapping(List<String> headers, List<DynamicFormColumn> columns,
                                               List<JSONObject> schemaFields) {
        Map<String, String> byHeader = new HashMap<>();
        for (DynamicFormColumn column : columns) {
            byHeader.putIfAbsent(displayCode(column, schemaFields).toLowerCase(), column.getFiledCode());
            byHeader.putIfAbsent(column.getFiledCode().toLowerCase(), column.getFiledCode());
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String header : headers) {
            String code = StringUtils.isEmpty(header) ? null : byHeader.get(header.toLowerCase());
            mapping.put(header, code == null ? "" : code);
        }
        return mapping;
    }

    /**
     * 跨环境列结构预检（R2-19）：把上传文件表头与本环境表单结构对照——
     * matched（表头↔字段）、unmatched（目标环境无此字段，跨环境导入主症状）、
     * missingRequired（必填列缺失）、warnings（TYPE_MISMATCH 样本试转换失败 / UNIQUE_MISSING 唯一键列缺失）。
     */
    private JSONObject buildPrecheck(List<String> headers, List<Map<String, Object>> rows,
                                     List<DynamicFormColumn> columns, List<JSONObject> schemaFields) {
        Map<String, String> byHeaderLower = new HashMap<>();
        for (DynamicFormColumn column : columns) {
            byHeaderLower.putIfAbsent(displayCode(column, schemaFields).toLowerCase(), column.getFiledCode());
            byHeaderLower.putIfAbsent(column.getFiledCode().toLowerCase(), column.getFiledCode());
        }
        Map<String, DynamicFormColumn> byHeader = new LinkedHashMap<>();
        Map<String, String> headerByCode = new LinkedHashMap<>();
        for (String header : headers) {
            if (StringUtils.isEmpty(header)) {
                continue;
            }
            String code = byHeaderLower.get(header.toLowerCase());
            if (code != null) {
                byHeader.put(header, columnByCode(columns, code));
                headerByCode.putIfAbsent(code, header);
            }
        }
        JSONObject report = new JSONObject();
        JSONArray matched = new JSONArray();
        for (Map.Entry<String, DynamicFormColumn> entry : byHeader.entrySet()) {
            DynamicFormColumn column = entry.getValue();
            if (column == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("header", entry.getKey());
            item.put("field", column.getFiledCode());
            item.put("fieldName", column.getFiledName());
            item.put("fieldType", column.getFiledType());
            matched.add(item);
        }
        report.put("matched", matched);
        JSONArray unmatched = new JSONArray();
        for (String header : headers) {
            if (!StringUtils.isEmpty(header) && !byHeader.containsKey(header)) {
                JSONObject item = new JSONObject();
                item.put("header", header);
                item.put("reason", "目标环境表单没有该字段（跨环境导入常见：两边结构不一致），可忽略或映射到其他字段");
                unmatched.add(item);
            }
        }
        report.put("unmatched", unmatched);
        Set<String> mappedCodes = new HashSet<>(headerByCode.keySet());
        JSONArray missingRequired = new JSONArray();
        JSONArray warnings = new JSONArray();
        for (DynamicFormColumn column : columns) {
            String code = column.getFiledCode();
            if (DynamicFormColumn.isSystemField(code) || mappedCodes.contains(code)) {
                continue;
            }
            JSONObject schema = schemaField(schemaFields, code);
            if (isRequiredField(schema)) {
                JSONObject item = new JSONObject();
                item.put("field", code);
                item.put("fieldName", column.getFiledName());
                item.put("reason", "文件未包含必填字段：strict_validate=Y 时对应行将校验失败");
                missingRequired.add(item);
            }
            if ("Y".equals(column.getUniqueFlag())) {
                JSONObject item = new JSONObject();
                item.put("kind", "UNIQUE_MISSING");
                item.put("field", code);
                item.put("fieldName", column.getFiledName());
                item.put("reason", "唯一键列缺失：文件内/库内重复无法识别，重复键策略对该列失效");
                warnings.add(item);
            }
        }
        report.put("missingRequired", missingRequired);
        // 类型样本试转换：数值/日期列取前 20 个非空样本，首报一个失败样本即止
        for (DynamicFormColumn column : columns) {
            String code = column.getFiledCode();
            String header = headerByCode.get(code);
            if (header == null) {
                continue;
            }
            String type = StringUtils.isEmpty(column.getFiledType()) ? "TEXT" : column.getFiledType().toUpperCase();
            boolean numeric = type.contains("INT") || type.contains("NUMBER") || type.contains("DECIMAL")
                    || type.contains("DOUBLE") || type.contains("FLOAT");
            boolean date = type.contains("DATE") || type.contains("TIME");
            if (!numeric && !date) {
                continue;
            }
            int checked = 0;
            for (Map<String, Object> row : rows) {
                Object raw = row.get(header);
                if (raw == null || String.valueOf(raw).isEmpty()) {
                    continue;
                }
                try {
                    convertValue(column, String.valueOf(raw));
                } catch (Exception e) {
                    JSONObject item = new JSONObject();
                    item.put("kind", "TYPE_MISMATCH");
                    item.put("header", header);
                    item.put("field", code);
                    item.put("fieldName", column.getFiledName());
                    item.put("sample", String.valueOf(raw));
                    item.put("reason", e.getMessage());
                    warnings.add(item);
                    break;
                }
                if (++checked >= 20) {
                    break;
                }
            }
        }
        report.put("warnings", warnings);
        return report;
    }

    /** 可映射字段清单（预检响应用，UI 下拉数据源） */
    private List<Map<String, Object>> fieldSummaries(List<DynamicFormColumn> columns, List<JSONObject> schemaFields) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (DynamicFormColumn column : columns) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", column.getFiledCode());
            item.put("fieldName", column.getFiledName());
            item.put("fieldType", column.getFiledType());
            JSONObject schema = schemaField(schemaFields, column.getFiledCode());
            item.put("required", isRequiredField(schema));
            item.put("unique", "Y".equals(column.getUniqueFlag()));
            fields.add(item);
        }
        return fields;
    }

    /** io 配置快照（执行期口径以快照为准：预检到执行期间配置变更不影响在途任务） */
    private String configSnapshot(DynamicFormIoConfig config) {
        JSONObject snapshot = new JSONObject();
        snapshot.put("dupStrategy", config.getDupStrategy());
        snapshot.put("strictValidate", config.getStrictValidate());
        snapshot.put("runBackscript", config.getRunBackscript());
        snapshot.put("importFlowMode", config.getImportFlowMode());
        snapshot.put("triggerMode", config.getTriggerMode());
        snapshot.put("importLimit", config.getImportLimit());
        return snapshot.toJSONString();
    }

    /** 导入进度轮询（读库：多实例可见，重启不丢） */
    public Map<String, Object> progress(String taskId) {
        DynamicIoImportTask entity = requireTask(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", entity.getStatus());
        result.put("finished", DynamicIoImportTask.STATUS_FINISHED.equals(entity.getStatus())
                || DynamicIoImportTask.STATUS_FAILED.equals(entity.getStatus()));
        result.put("total", entity.getTotal());
        result.put("processed", entity.getProcessed());
        result.put("success", entity.getSuccess());
        result.put("skipped", entity.getSkipped());
        result.put("failed", entity.getFailed());
        result.put("finishedTime", entity.getFinishedTime());
        result.put("createdBy", entity.getCreatedBy());
        return result;
    }

    /** 导入记录（最近 20 条，读库：多实例可见的历史痕迹） */
    public List<Map<String, Object>> listTasks(String formId) {
        List<DynamicIoImportTask> list = ioImportTaskMapper.selectList(Wrappers.<DynamicIoImportTask>lambdaQuery()
                .eq(DynamicIoImportTask::getFormId, formId)
                .orderByDesc(DynamicIoImportTask::getCreatedTime)
                .last("limit 20"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DynamicIoImportTask entity : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", entity.getId());
            item.put("status", entity.getStatus());
            item.put("total", entity.getTotal());
            item.put("processed", entity.getProcessed());
            item.put("success", entity.getSuccess());
            item.put("skipped", entity.getSkipped());
            item.put("failed", entity.getFailed());
            item.put("createdBy", entity.getCreatedBy());
            item.put("createdTime", entity.getCreatedTime());
            item.put("finishedTime", entity.getFinishedTime());
            result.add(item);
        }
        return result;
    }

    /** 失败行报告（读库 errors_json，可修改后重新导入） */
    public byte[] buildFailureReport(String taskId) throws Exception {
        DynamicIoImportTask entity = requireTask(taskId);
        List<String> errors = new ArrayList<>();
        if (!StringUtils.isEmpty(entity.getErrorsJson())) {
            try {
                JSONArray array = JSON.parseArray(entity.getErrorsJson());
                if (array != null) {
                    for (int i = 0; i < array.size(); i++) {
                        Object item = array.get(i);
                        if (item != null) {
                            errors.add(String.valueOf(item));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("失败明细解析失败 taskId={}", taskId, e);
            }
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("失败明细");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("行号");
            header.createCell(1).setCellValue("原因");
            for (int i = 0; i < errors.size(); i++) {
                Row row = sheet.createRow(i + 1);
                String error = errors.get(i);
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

    private DynamicIoImportTask requireTask(String taskId) {
        DynamicIoImportTask entity = ioImportTaskMapper.selectById(taskId);
        if (entity == null) {
            throw new IllegalArgumentException("导入任务不存在或已过期：" + taskId);
        }
        return entity;
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

    /**
     * 必填判定（R2-19 预检顺带修正）：设计器口径必填在 rules[].required（字符串 "true"，表单保存链路
     * DynamicFormServiceImpl 归一化），旧代码只读顶层 required 布尔导致设计器表单必填校验从未生效——
     * 两种口径都认，兼容 API 直存 metaData 的顶层布尔形态。
     */
    private boolean isRequiredField(JSONObject schema) {
        if (schema == null) {
            return false;
        }
        if (Boolean.TRUE.equals(schema.getBoolean("required"))) {
            return true;
        }
        JSONArray rules = schema.getJSONArray("rules");
        if (rules == null) {
            return false;
        }
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule == null) {
                continue;
            }
            Object required = rule.get("required");
            if ((required instanceof String str && "true".equalsIgnoreCase(str.trim()))
                    || Boolean.TRUE.equals(required)) {
                return true;
            }
        }
        return false;
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

    private Object convertValue(DynamicFormColumn column, String raw) {
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
            if (isRequiredField(schema)) {
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
    private String findDuplicate(String database, String tableCode, List<String> uniqueCodes, Map<String, Object> row, Set<String> seenInFile) {
        for (String code : uniqueCodes) {
            Object value = row.get(code);
            if (value == null || String.valueOf(value).isEmpty()) {
                continue;
            }
            String fileKey = code + "=" + value;
            if (!seenInFile.add(fileKey)) {
                return fileKey + "（文件内重复）";
            }
            if (locateId(database, tableCode, uniqueCodes, row) != null) {
                return fileKey;
            }
        }
        return null;
    }

    /** 按唯一列定位已存在行 id（值绑定查询；返回首个命中） */
    private String locateId(String database, String tableCode, List<String> uniqueCodes, Map<String, Object> row) {
        for (String code : uniqueCodes) {
            Object value = row.get(code);
            if (value == null || String.valueOf(value).isEmpty()) {
                continue;
            }
            List<Map<String, Object>> hits = queryBoundRows(database, tableCode,
                    "SELECT `id` FROM `" + tableCode + "` WHERE `" + code + "` = #{params.p1} "
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
}
