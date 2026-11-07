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

package com.privernal.dynamic.dbs.controller;

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.core.constant.UserConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.PreAuthorizeException;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.annotation.ServiceRequest;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.dba.constants.TableFieldConstant;
import com.privernal.dynamic.dba.utils.FormDataTitleUtils;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.privernal.dynamic.dbs.domain.DynamicDatabase;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.service.CommonService;
import com.privernal.dynamic.dbs.service.DynamicFlowAccessService;
import com.privernal.dynamic.dbs.service.DynamicFormRelationService;
import com.privernal.dynamic.dbs.service.DynamicRelationDataService;
import com.privernal.dynamic.dbs.service.DynamicSubTableService;
import com.privernal.dynamic.dbs.service.DynamicTableCodePrefixService;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.dbs.util.DynamicTableAuthUtil;
import com.privernal.dynamic.dbs.util.PageUtil;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;
import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.flow.dto.FlowStartDTO;
import com.privernal.flow.feign.RemoteFlowService;
import com.privernal.system.dto.SystemDepartmentDTO;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.system.filter.WorkflowUserFilterDTO;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.privernal.common.core.constant.UserConstants.USER_TYPE_SYSTEM;
import static com.privernal.dynamic.constants.DynamicConstants.*;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-27 11:02
 * @Description
 */
@RestController
public class CommonController {

    private static final Logger logger = LoggerFactory.getLogger(CommonController.class);

    /** 流程变量里日期时间值的统一字符串格式（与表单提交口径一致） */
    private static final java.time.format.DateTimeFormatter DATETIME_VARIABLE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final java.time.format.DateTimeFormatter TIME_VARIABLE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 关联显示数据查询的授权线索：引用方表单编码（不是数据列，查询前要移除） */
    private static final String FIELD_FROM_TABLE_CODE = "fromTableCode";

    /** 关联显示数据查询的授权线索：流程业务数据id（只从查询串取，body 里的同名词按数据列处理） */
    private static final String FIELD_BUSINESS_ID = "businessId";

    @Autowired
    CommonService commonService;

    @Autowired
    DynamicTableService dynamicTableService;

    @Autowired
    IDynamicFormService dynamicFormService;

    @Autowired
    IDynamicFormColumnService dynamicFormColumnService;

    @Autowired
    RemoteUserService remoteUserService;

    @Autowired
    RemoteFlowService remoteFlowService;

    @Autowired
    com.privernal.dynamic.dbs.mapper.InitiatorRoleMapper initiatorRoleMapper;

    @Autowired
    DynamicSubTableService dynamicSubTableService;

    @Autowired
    DynamicTableCodePrefixService dynamicTableCodePrefixService;

    @Autowired
    DynamicRelationDataService dynamicRelationDataService;

    @Autowired
    DynamicFormRelationService dynamicFormRelationService;

    @Autowired
    DynamicFlowAccessService dynamicFlowAccessService;

    @Autowired
    com.privernal.dynamic.script.service.ScriptBindingService scriptBindingService;

    @Operation(summary = "新增数据库配置")
    @PreAuthorize(hasPermi = "dynamic:database:add")
    @Log(title = "动态数据库", operationType = OperationType.INSERT)
    @PostMapping("/dbs/open/database/add")
    public CommonResult<Object> databaseAddConfig(@RequestBody DynamicDatabase data) {
        try {
            boolean success = commonService.saveDatabase(data);
            return success ? CommonResult.ok("成功") : CommonResult.fail("保存数据库配置失败");
        } catch (Exception e) {
            logger.error("databaseAddConfig error: {}", data, e);
            return CommonResult.fail(e.getMessage());
        }
    }

    @Operation(summary = "数据库新增表")
    @PreAuthorize(hasAnyPermi = {"dynamic:form:add", "dynamic:form:edit"})
    @Log(title = "动态表", operationType = OperationType.INSERT)
    @PostMapping("/dbs/open/table/add")
    public Object databaseAddTable(@RequestBody DynamicForm dynamicForm) {
        if (dynamicForm == null) {
            return CommonResult.fail("表单数据不能为空");
        }
        // 与 dynamicFormService 的入口一致：表名统一带上企业组织编码前缀（已带前缀的原样返回）
        dynamicForm.setTableCode(dynamicTableCodePrefixService.decorate(dynamicForm.getTableCode()));
        String database = currentDatabase();
        logger.info("databaseAddTable database {} tableCode {}", database, dynamicForm.getTableCode());
        //创建表
        dynamicTableService.createTable(database, dynamicForm.getTableCode(), dynamicForm.getMetaData());
        //保存或更新form
        if (dynamicForm.getId() == null) {
            dynamicForm.setCreatedTime(new Date());
            dynamicForm.setCreatedBy(USER_TYPE_SYSTEM);
        }
        dynamicForm.setUpdatedTime(new Date());
        commonService.saveForm(database, dynamicForm);
        // 该入口绕过了 dynamicFormService，关联引用登记在这里补上，否则引用关系不会生效
        dynamicFormRelationService.syncFormRelations(dynamicForm);
        logger.info("databaseAddTable save finish tableCode {}", dynamicForm.getTableCode());
        return dynamicForm;
    }

    @Operation(summary = "数据库查询表单列表")
    @PreAuthorize(hasPermi = "dynamic:form:list")
    @PostMapping("/dbs/open/table/list")
    public Object findTableList(@RequestBody DynamicFormFilterDTO data) {
        PageResultInfo pageResultInfo = new PageResultInfo();
        if (data != null) {
            IPage<DynamicForm> page = commonService.findTableList(currentDatabase(), data);
            PageUtil.transferPageObject(pageResultInfo, page);
        }
        return pageResultInfo;
    }

    /**
     * 此为配置的菜单路径
     *
     * @param tableCode 表单的编码
     * @return 返回表单的数据
     */
    @Operation(summary = "根据tableCode查询一条form数据")
    @PreAuthorize(hasPermi = "dynamic:form:query")
    @GetMapping("/dbs/open/table/{tableCode}")
    public Object findTableByCode(@PathVariable("tableCode") String tableCode) {
        String database = currentDatabase();
        logger.info("findTableByCode database {} tableCode {}", database, tableCode);
        return commonService.findTableByCode(database, tableCode);
    }

    /**
     * 根据ID查询一个表单
     *
     * @param id 表单的id
     * @return 返回表单的数据
     */
    @Operation(summary = "根据id查询一条form数据")
    @PreAuthorize(hasPermi = "dynamic:form:query")
    @GetMapping("/dbs/open/table/find/id")
    public Object findTableById(@RequestParam String id) {
        String database = currentDatabase();
        logger.info("findTableById database {} id {}", database, id);
        return commonService.findTableById(database, id);
    }

    /**
     * 根据ID删除表单
     *
     * @param id 表单的id
     * @return 返回表单的数据
     */
    @Operation(summary = "根据id删除表单数据")
    @PreAuthorize(hasPermi = "dynamic:form:remove")
    @Log(title = "动态表", operationType = OperationType.DELETE)
    @DeleteMapping("/dbs/open/table/{id}")
    public Object deleteTableById(@PathVariable String id) {
        String database = currentDatabase();
        logger.info("deleteTableById database {} id {}", database, id);
        DynamicForm dynamicForm = commonService.findTableById(database, id);
        if (dynamicForm == null) {
            return CommonResult.fail("表单不存在：" + id);
        }
        // 与表单管理页同一条级联删除链路：列配置/快照/引用登记/菜单授权/应用绑定/流程/物理表一并清理，
        // 不再走本类旧的「删行 + drop 表」半截逻辑
        return dynamicFormService.deleteDynamicFormById(id) > 0;
    }

    /**
     * 动态表单增加或编辑数据
     *
     * @param data 数据
     * @return 结果
     */
    @Operation(summary = "动态表增加数据")
    @Log(title = "动态表数据", operationType = OperationType.INSERT)
    @PostMapping("/dbs/open/dynamic/data/add")
    public Object addTableData(@RequestBody Map<String, Object> data) throws Exception {
        logger.info("动态表增加数据 tableCode {}", data == null ? null : data.get(FIELD_TABLE_CODE));
        String tableName = stringField(data, FIELD_TABLE_CODE);
        if (tableName == null) {
            return CommonResult.fail("tableCode 不能为空");
        }
        boolean isInsert = data.get("id") == null;
        // 先按表校验按钮权限再动数据：无 id 是新增要 add，带 id 是更新要 edit
        DynamicTableAuthUtil.requirePermission(tableName, isInsert ? "add" : "edit");
        String database = SecurityUtils.getDatabaseName();

        //查询此表单的后台编写的代码，无脚本或未配置 transactional 时默认同事务执行
        DynamicForm dynamicForm = dynamicFormService.selectDynamicFormByTableCode(tableName);
        if (dynamicForm == null || dynamicForm.getMetaData() == null) {
            return CommonResult.fail("表单不存在或表单元数据缺失：" + tableName);
        }
        String backScript = null;
        boolean transactional = true;
        JSONObject backScriptConfig = dynamicForm.getMetaData().getJSONObject(BACK_SCRIPT);
        if (backScriptConfig != null) {
            backScript = backScriptConfig.getString(BACK_SCRIPT_ATTR);
            Boolean configured = backScriptConfig.getBoolean(BACK_SCRIPT_TRANSACTIONAL);
            transactional = configured == null || configured;
        }
        // startFlow=false 时只落数据不发流程（草稿），由列表的「发起」入口后续显式提交
        Object startFlowOption = data.get(FIELD_START_FLOW);
        boolean autoStartFlow = startFlowOption == null || Boolean.parseBoolean(startFlowOption.toString());
        boolean isWorkflowForm = dynamicForm.getIsWorkflow() != null && dynamicForm.getIsWorkflow() == 1
                && StringUtils.isNotEmpty(dynamicForm.getWorkflowCode());
        if (isWorkflowForm && isInsert && (!autoStartFlow || data.get("status") == null)) {
            // 流程表单新增一律先落 0（WorkflowDataStatus.STAGING），避免 saveOrUpdate 的默认值 1 把它写成「已完成」：
            // 自动发起成功后由状态回写改为 2，发起失败则留在 0，列表显示「发起」可直接重试
            data.put("status", WorkflowDataStatus.STAGING.getCode().intValue());
        }
        // 数据标题：按表单配置（表单属性-数据标题）把所选字段值拼接进公共列 title，
        // 未配置或本次提交取不到值时不写入，避免局部更新（如仅改状态）清空已有标题
        String dataTitle = FormDataTitleUtils.buildTitle(dynamicForm.getMetaData(), data);
        if (StringUtils.isNotEmpty(dataTitle)) {
            data.put(TableFieldConstant.FIELD_TITLE, dataTitle);
        }
        // 「不允许重复」（输入框 props.unique）保存前查重：重复直接返回错误，不落库
        dynamicSubTableService.assertUniqueFields(database, tableName, dynamicForm.getMetaData(), data);
        // 表单保存校验挂点（独立表）：在 backScript 之前按 execution_order
        // 统一执行 SAVE_VALIDATE 绑定，失败抛业务异常中止保存并回显 fail_message
        scriptBindingService.runSaveValidations(dynamicForm.getId(), tableName, data);
        // 子表行数组必须先于 saveTableData 提取：saveOrUpdate 会把 Map/Collection 值原地序列化成
        // JSON 文本（写主表快照列），保存后再取到的只是字符串，行数据将永远不会拆写进子表表
        List<DynamicSubTableService.SubTableDef> subTableDefs =
                dynamicSubTableService.resolveSubTables(tableName, dynamicForm.getMetaData());
        Map<String, List<Map<String, Object>>> subTableRows = extractSubTableRows(subTableDefs, data);
        Object result = commonService.saveTableData(database, tableName, backScript, data, transactional);
        // 子表（sub-table 组件）行数据独立落库：主表编码_子表编码 表，parent_id 关联主表数据 ID，
        // 整体重建（先删旧行再插当前行），与前端行的增删改交互对齐
        if (result instanceof Map) {
            saveSubTableRows(database, subTableDefs, subTableRows, (Map<String, Object>) result);
        }
        // 表单启用工作流时，新增数据自动发起流程（表单字段值作为流程变量，供互斥网关与审批人脚本解析）
        if (autoStartFlow && isWorkflowForm && isInsert && result instanceof Map) {
            CommonResult<FlowInstanceStatusDTO> startResult =
                    startWorkflow(tableName, dynamicForm.getWorkflowCode(), (Map<String, Object>) result);
            if (startResult != null && startResult.getCode() != CommonResult.SUCCESS) {
                logger.warn("表单 {} 数据保存成功但自动发起流程失败：{}", tableName, startResult.getMessage());
            }
        }
        return CommonResult.ok(result);
    }

    /**
     * 草稿数据显式发起流程：按表单绑定（dynamic_form.workflow_code）发起，已有运行中实例时不重复发起
     *
     * @param data {tableCode, id}
     */
    @Operation(summary = "动态表数据发起流程")
    @Log(title = "动态表数据", operationType = OperationType.INSERT)
    @PostMapping("/dbs/open/dynamic/data/startFlow")
    public CommonResult<FlowInstanceStatusDTO> startFlowByData(@RequestBody Map<String, Object> data) {
        String tableCode = stringField(data, FIELD_TABLE_CODE);
        String businessId = stringField(data, "id");
        if (tableCode == null) {
            return CommonResult.fail("tableCode 不能为空");
        }
        if (businessId == null) {
            return CommonResult.fail("id 不能为空");
        }
        // 发起的是本人提交的业务数据：按新增或修改权限放行，具体能发起哪条仍由数据范围与流程待办决定
        DynamicTableAuthUtil.requirePermission(tableCode, "add", "edit");
        DynamicForm dynamicForm = dynamicFormService.selectDynamicFormByTableCode(tableCode);
        if (dynamicForm == null) {
            return CommonResult.fail("表单不存在：" + tableCode);
        }
        if (dynamicForm.getIsWorkflow() == null || dynamicForm.getIsWorkflow() != 1
                || StringUtils.isEmpty(dynamicForm.getWorkflowCode())) {
            return CommonResult.fail("表单未绑定流程，无法发起：" + tableCode);
        }
        try {
            CommonResult<FlowInstanceStatusDTO> exist = remoteFlowService.getStatusByBusinessId(businessId);
            if (exist != null && exist.getCode() == CommonResult.SUCCESS && exist.getData() != null
                    && Boolean.TRUE.equals(exist.getData().getRunning())) {
                return CommonResult.fail("该数据已有进行中的流程实例：" + exist.getData().getNodeName());
            }
        } catch (Exception e) {
            logger.warn("查询数据 {} 的流程状态失败，继续尝试发起", businessId, e);
        }
        Map<String, Object> row = dynamicTableService.findById(currentDatabase(), tableCode, businessId);
        if (row == null || row.isEmpty()) {
            return CommonResult.fail("数据不存在：" + businessId);
        }
        return startWorkflow(tableCode, dynamicForm.getWorkflowCode(), row);
    }

    /**
     * 按表单绑定发起流程（新增自动发起与草稿显式发起共用）
     */
    private CommonResult<FlowInstanceStatusDTO> startWorkflow(String tableCode, String workflowCode,
                                                              Map<String, Object> savedData) {
        Object businessId = savedData.get("id");
        try {
            if (businessId == null || StringUtils.isEmpty(businessId.toString())) {
                return CommonResult.fail("数据缺少主键 id，无法发起流程");
            }
            String handler = SecurityUtils.getUserId();
            if (StringUtils.isEmpty(handler)) {
                Object createdBy = savedData.getOrDefault("createdBy", savedData.get("created_by"));
                handler = createdBy == null ? null : createdBy.toString();
            }
            if (StringUtils.isEmpty(handler)) {
                logger.warn("表单 {} 已启用工作流但无法确定发起人，跳过发起", tableCode);
                return CommonResult.fail("无法确定流程发起人");
            }
            FlowStartDTO startDTO = new FlowStartDTO();
            startDTO.setTableCode(tableCode);
            // 表单自身的流程编码优先：dynamic_form.workflow_code 是设计器保存的权威绑定，
            // 不依赖 flow_form_bind/flow_form_version 的反查（未配置绑定的动态表会解析失败）
            startDTO.setFlowCode(workflowCode);
            startDTO.setBusinessId(businessId.toString());
            startDTO.setHandler(handler);
            startDTO.setVariables(flowVariables(savedData));
            CommonResult<FlowInstanceStatusDTO> result = remoteFlowService.start(startDTO);
            if (result != null && result.getCode() == CommonResult.SUCCESS) {
                logger.info("表单 {} 数据 {} 发起流程成功：{}", tableCode, businessId,
                        result.getData() == null ? null : result.getData().getInstanceId());
                return result;
            }
            logger.warn("表单 {} 数据 {} 发起流程失败：{}", tableCode, businessId,
                    result == null ? null : result.getMessage());
            return result == null ? CommonResult.fail("发起流程失败") : result;
        } catch (Exception e) {
            logger.error("表单 {} 数据 {} 发起流程异常", tableCode, businessId, e);
            return CommonResult.fail("发起流程异常：" + e.getMessage());
        }
    }

    /**
     * 流程变量：动态表行数据以数据库列名（下划线）为键，而取人脚本与网关条件按表单字段名（驼峰）解析，
     * 因此下划线键额外补一份驼峰别名，保持与表单提交时发起的变量口径一致。
     * 同时注入网关上下文保留变量（发起人/部门/组织/角色名），供网关流转条件（wfexpr）的
     * 「组织机构」「角色」分组引用——发起后的第一跳网关只用发起时变量，必须在这里备齐。
     */
    private Map<String, Object> flowVariables(Map<String, Object> savedData) {
        Map<String, Object> variables = new HashMap<>();
        // 值先做类型归一：列表「发起」入口的数据来自 findById（JDBC 类型），datetime/date/time 列
        // 是 LocalDateTime 等.temporal 类型——warm-flow 的 Jackson 未注册 JavaTimeModule，
        // 直接进变量在发起时序列化报「Map转换异常」（2026-10-08 开课申请实测）。
        // 转成与表单提交一致的字符串格式，网关条件按字符串比较的口径也不变
        savedData.forEach((key, value) -> {
            Object normalized = normalizeTemporalValue(value);
            variables.put(key, normalized);
            if (key != null && key.indexOf('_') >= 0) {
                variables.putIfAbsent(camelCase(key), normalized);
            }
        });
        variables.remove("id");
        // 发起即创建：补 created_by 变量，script:DATA_OWNER 办理人来源依赖它，
        // 否则标准发起链路下 DATA_OWNER 解析不到人、被缺人兜底改派给组织管理员
        String initiator = com.privernal.common.security.service.SecurityUtils.getUserId();
        if (initiator != null && !initiator.isEmpty()) {
            variables.putIfAbsent("created_by", initiator);
            variables.putIfAbsent("createdBy", initiator);
        }
        putGatewayContextVars(variables);
        return variables;
    }

    /**
     * 值 temporal 归一（流程变量与数据行共用）：JDBC temporal 类型转字符串
     * （datetime→yyyy-MM-dd HH:mm:ss、date→yyyy-MM-dd、time→HH:mm:ss）。
     * 流程变量侧是 warm-flow 的 Jackson 未注册 JavaTimeModule 会报「Map转换异常」；
     * 数据行侧是 ISO 带 T 字符串与组件 valueFormat 不匹配导致编辑回显为空。
     */
    private Object normalizeTemporalValue(Object value) {
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.format(DATETIME_VARIABLE_FORMAT);
        }
        if (value instanceof java.time.LocalDate ld) {
            return ld.toString();
        }
        if (value instanceof java.time.LocalTime lt) {
            return lt.format(TIME_VARIABLE_FORMAT);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DATETIME_VARIABLE_FORMAT);
        }
        if (value instanceof java.sql.Date sd) {
            return sd.toLocalDate().toString();
        }
        if (value instanceof Date d) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
        }
        return value;
    }

    /** 网关上下文保留变量（与 flow 模块 FlowExprConditionStrategy 的键约定一致） */
    private void putGatewayContextVars(Map<String, Object> variables) {
        try {
            String userId = SecurityUtils.getUserId();
            if (StringUtils.isEmpty(userId)) {
                return;
            }
            variables.putIfAbsent("__initiator", userId);
            variables.putIfAbsent("__initiator_name", SecurityUtils.getNickName());
            variables.putIfAbsent("__initiator_dept", SecurityUtils.getDepartmentId());
            variables.putIfAbsent("__initiator_org", SecurityUtils.getOrganizationId());
            variables.putIfAbsent("__initiator_roles", initiatorRoleMapper.selectRoleNamesByUserId(userId));
        } catch (Exception e) {
            // 上下文注入失败不阻塞发起，仅角色/组织类网关条件不可用
            logger.warn("注入网关上下文变量失败", e);
        }
    }

    private String camelCase(String columnName) {
        StringBuilder builder = new StringBuilder(columnName.length());
        boolean upperNext = false;
        for (char c : columnName.toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return builder.toString();
    }

    /**
     * 把提交数据中的子表行数组拆写进子表数据库表（主表编码_子表编码，parent_id=主表数据 ID）。
     * 行数组在 saveTableData 之前由 {@link #extractSubTableRows} 提取（saveOrUpdate 会把
     * 集合值原地序列化成 JSON 文本），这里只负责按主表数据 ID 落子表表。
     * 任一子表写失败时整体抛错（主表数据已在 saveTableData 事务内提交，重新保存走更新分支，子表整体重建幂等）。
     */
    private void saveSubTableRows(String database, List<DynamicSubTableService.SubTableDef> subTableDefs,
                                  Map<String, List<Map<String, Object>>> subTableRows,
                                  Map<String, Object> savedData) {
        Object id = savedData.get("id");
        if (id == null || StringUtils.isEmpty(id.toString())) {
            return;
        }
        for (DynamicSubTableService.SubTableDef def : subTableDefs) {
            List<Map<String, Object>> rows = subTableRows.get(def.getField());
            if (rows != null) {
                dynamicSubTableService.saveRows(database, def.getChildTable(), id.toString(), rows);
            }
        }
    }

    /**
     * 保存前提取子表行数组（键=子表字段编码，值为 List&lt;Map&gt;；历史/兼容场景值可能是
     * 已序列化的 JSON 数组文本，这里一并还原），并返回按子表字段编码索引的行集合。
     * 提取后原 Map 中的值保持不动（快照列仍由 saveOrUpdate 序列化落库，列表/打印回退链共用）。
     */
    private Map<String, List<Map<String, Object>>> extractSubTableRows(List<DynamicSubTableService.SubTableDef> subTableDefs,
                                                                       Map<String, Object> data) {
        Map<String, List<Map<String, Object>>> extracted = new LinkedHashMap<>();
        for (DynamicSubTableService.SubTableDef def : subTableDefs) {
            Object rows = data.get(def.getField());
            if (rows instanceof List) {
                extracted.put(def.getField(), castRowList((List<?>) rows));
            } else if (rows instanceof String && !StringUtils.isEmpty((String) rows)) {
                try {
                    Object parsed = com.alibaba.fastjson2.JSON.parse((String) rows);
                    if (parsed instanceof List) {
                        extracted.put(def.getField(), castRowList((List<?>) parsed));
                    }
                } catch (Exception e) {
                    logger.warn("子表行数据不是合法 JSON 数组，跳过拆写：field={}", def.getField());
                }
            }
        }
        return extracted;
    }

    /**
     * 行集合逐项校验为 Map，混入非对象元素（脏数据）时剔除该行，避免后续强转失败
     */
    private List<Map<String, Object>> castRowList(List<?> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map) {
                result.add((Map<String, Object>) row);
            }
        }
        return result;
    }

    /**
     * 动态表删除数据
     *
     * @param tableName 表名
     * @param id        数据的ID
     * @return
     */
    @Operation(summary = "动态表删除数据,根据id,")
    @Log(title = "动态表数据", operationType = OperationType.DELETE)
    @DeleteMapping("/dbs/open/dynamic/{tableName}/{id}")
    public Object deleteTableDataById(@PathVariable("tableName") String tableName, @PathVariable("id") String id) {
        // 表名来自路径，任何登录用户可猜测：先按表校验删除权限
        DynamicTableAuthUtil.requirePermission(tableName, "remove");
        String database = SecurityUtils.getDatabaseName();
        logger.info("deleteTableDataById database {} tableName {} id {}", database, tableName, id);
        // 子表行级联删除：先清该数据全部子表行，再删主表数据
        DynamicForm dynamicForm = dynamicFormService.selectDynamicFormByTableCode(tableName);
        if (dynamicForm != null) {
            for (DynamicSubTableService.SubTableDef def : dynamicSubTableService.resolveSubTables(tableName, dynamicForm.getMetaData())) {
                dynamicSubTableService.deleteByParentIds(database, def.getChildTable(), List.of(id));
            }
        }
        dynamicTableService.deleteById(database, tableName, id);
        return CommonResult.ok(id, "删除成功");
    }

    /**
     * 动态表单查询单条数据
     *
     * @param tableName 表名
     * @param id        数据的ID
     * @return 结果
     */
    @Operation(summary = "动态表查询数据,根据id,")
    @GetMapping("/dbs/open/dynamic/{tableName}/{id}")
    public Object findTableDataById(@PathVariable("tableName") String tableName, @PathVariable("id") String id) {
        String database = SecurityUtils.getDatabaseName();
        logger.info("findTableDataById database {} tableName {} id {}", database, tableName, id);
        // 单条读取用于列表详情与编辑回显：有查看或有编辑权限均可（只授编辑的角色不能因此断掉回显）
        boolean byButton = DynamicTableAuthUtil.hasPermission(tableName, "list", "edit");
        if (!byButton && !dynamicFlowAccessService.isFlowParticipant(tableName, id)) {
            // 审批人没有申请单所在表的按钮权限，但要能查看自己经手的那条数据
            throw new PreAuthorizeException();
        }
        // 流程参与人放行时同样跳过部门/本人数据范围，否则读到的正是自己看不到的一条数据
        return byButton ? CommonResult.ok(readBusinessRow(database, tableName, id), "查询成功")
                : CommonResult.ok(DynamicRelationDataService.withoutDataScope(
                        () -> readBusinessRow(database, tableName, id)), "查询成功");
    }

    /**
     * 按 id 读取一条业务数据并补齐子表行与列名驼峰别名
     */
    private Map<String, Object> readBusinessRow(String database, String tableName, String id) {
        Map<String, Object> map = dynamicTableService.findById(database, tableName, id);
        // 子表行回查：按子表定义把行数据列表填回 formData 的子表字段（编辑回显）；
        // 行列表为空时回退解析主表行的子表 JSON 快照（历史数据先于拆行落库，仅有快照）。
        // 快照存放在主表列（下划线列名），而子表 field 是设计器原名（可含连字符），取值需归一
        if (map != null && !map.isEmpty()) {
            DynamicForm dynamicForm = dynamicFormService.selectDynamicFormByTableCode(tableName);
            if (dynamicForm != null) {
                for (DynamicSubTableService.SubTableDef def : dynamicSubTableService.resolveSubTables(tableName, dynamicForm.getMetaData())) {
                    List<Map<String, Object>> rows = dynamicSubTableService.findRows(database, def.getChildTable(), id);
                    if (rows.isEmpty()) {
                        Object snapshot = map.containsKey(def.getField())
                                ? map.get(def.getField())
                                : map.get(FormTransferTable.toDbColumnName(def.getField()));
                        rows = parseRows(snapshot);
                    }
                    // 快照列键必须移除：它的驼峰别名（subTable2228）会随回显混进 formData，
                    // 保存时被转写成不存在的列（sub_table2228）导致 UPDATE 失败；数组已由 field 键承载
                    map.remove(FormTransferTable.toDbColumnName(def.getField()));
                    map.put(def.getField(), withCamelAliases(rows));
                }
            }
            // 主表列名（下划线）补驼峰别名：表单字段编码为驼峰（如 userName）时编辑回显按键取值
            withCamelAliases(map);
            // temporal 归一：datetime/date/time 列的 JDBC 值（LocalDateTime 等）序列化是 ISO 带 T 的字符串，
            // 与组件 valueFormat（yyyy-MM-dd HH:mm 等）不匹配会导致编辑回显解析失败显示为空
            normalizeTemporalValues(map);
        }
        return map;
    }

    /** 行值 temporal 归一（Map 版）：见 {@link #normalizeTemporalValue(Object)} */
    private void normalizeTemporalValues(Map<String, Object> row) {
        if (row == null) {
            return;
        }
        row.replaceAll((k, v) -> normalizeTemporalValue(v));
    }

    /** 行值 temporal 归一（分页结果版）：列表列直接展示字符串值，避免 ISO 带 T 原样出现在列里 */
    @SuppressWarnings("unchecked")
    private void normalizeTemporalValues(PageResultInfo pageResult) {
        List<Object> records = pageResult.getList();
        if (records == null) {
            return;
        }
        for (Object record : records) {
            if (record instanceof Map) {
                normalizeTemporalValues((Map<String, Object>) record);
            }
        }
    }

    /**
     * 为 Map 键补驼峰别名（user_name → userName，已存在同名键时不覆盖），返回原对象
     */
    private Map<String, Object> withCamelAliases(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> aliases = new HashMap<>();
        data.forEach((key, value) -> {
            if (key != null && key.indexOf('_') >= 0) {
                aliases.putIfAbsent(camelCase(key), value);
            }
        });
        aliases.forEach(data::putIfAbsent);
        return data;
    }

    /**
     * 为行列表的每一行补驼峰别名，返回原列表
     */
    private List<Map<String, Object>> withCamelAliases(List<Map<String, Object>> rows) {
        if (rows != null) {
            rows.forEach(this::withCamelAliases);
        }
        return rows;
    }

    /**
     * @param data 过滤条件 键值对形式
     *             {
     *             pageNum  1
     *             pageSize  10
     *             name  'luce'
     *             }
     * @return
     */
    @Operation(summary = "动态表查询所有数据,并分页")
    @PostMapping("/dbs/open/dynamic/data/list")
    public Object findTableData(@RequestBody Map<String, Object> data) {
        String database = SecurityUtils.getDatabaseName();
        logger.info("findTableData database {}", database);
        String tableCode = stringField(data, FIELD_TABLE_CODE);
        if (tableCode == null) {
            return CommonResult.fail("tableCode 不能为空");
        }
        // 分页查询按提交的目标表校验查看权限：数据范围（本人/部门）在查询层再收敛
        DynamicTableAuthUtil.requirePermission(tableCode, "list");
        data.remove(FIELD_TABLE_CODE);
        return CommonResult.ok(queryTableData(database, tableCode, data), "查询成功");
    }

    /**
     * 关联表单显示数据查询：设计器的关联表单下拉与列表的关联字段翻译都只用到被关联表的
     * 显示字段与回填源字段，按「有引用方表单权限即可读取」放行，不要求被关联表的 list 按钮权限。
     * 这类调用方通常没有被关联表的数据范围配置，缺省会收敛成「只看自己创建的」而下拉恒为空，
     * 所以由引用方放行时跳过部门/本人数据范围（组织隔离仍生效），返回列再按关联配置收敛。
     * 审批人没有引用方表单权限，改为按流程参与人放行（引用方表单编码 + 业务数据id 成对给出）。
     *
     * @param data 查询体 {tableCode 被关联表单, fromTableCode 可选, 其余为分页与查询条件}
     * @param tableCodeParam    被关联表单编码（自定义 dataUrl 场景下组件不放进请求体，从查询串取）
     * @param fromTableCodeParam 引用方表单编码，同上
     * @param businessIdParam    流程业务数据id，仅从查询串取，避免与同名数据列的过滤条件冲突
     */
    @Operation(summary = "关联表单显示数据查询（按引用方表单或流程参与人授权，只读）")
    @PostMapping("/dbs/open/dynamic/data/relationList")
    public Object findRelationDisplayData(@RequestBody Map<String, Object> data,
                                          @RequestParam(value = FIELD_TABLE_CODE, required = false) String tableCodeParam,
                                          @RequestParam(value = FIELD_FROM_TABLE_CODE, required = false) String fromTableCodeParam,
                                          @RequestParam(value = FIELD_BUSINESS_ID, required = false) String businessIdParam) {
        String database = SecurityUtils.getDatabaseName();
        String tableCode = firstNotEmpty(stringField(data, FIELD_TABLE_CODE), tableCodeParam);
        if (tableCode == null) {
            return CommonResult.fail("tableCode 不能为空");
        }
        // 引用方表单编码只是授权线索，不是数据列，必须先取走再进查询链路
        String fromTableCode = firstNotEmpty(stringField(data, FIELD_FROM_TABLE_CODE), fromTableCodeParam);
        data.remove(FIELD_FROM_TABLE_CODE);
        DynamicRelationDataService.RelationReadGrant grant =
                dynamicRelationDataService.resolveReadGrant(tableCode, fromTableCode, businessIdParam);
        data.remove(FIELD_TABLE_CODE);
        PageResultInfo pageResult = grant.isViaReferencer()
                ? DynamicRelationDataService.withoutDataScope(() -> queryTableData(database, tableCode, data))
                : queryTableData(database, tableCode, data);
        Set<String> visibleColumns = grant.getVisibleColumns();
        if (visibleColumns != null) {
            retainColumns(pageResult, visibleColumns);
        }
        return CommonResult.ok(pageResult, "查询成功");
    }

    /**
     * 动态表分页查询链路（data/list 与关联显示数据共用）：列类型归一、子表行字段条件、
     * 创建人/部门翻译与子表聚合列填充
     */
    private PageResultInfo queryTableData(String database, String tableCode, Map<String, Object> data) {
        // 物理表不存在（孤儿表单/元数据残留）时给友好提示，避免裸抛 SQL "table doesn't exist" 异常
        if (!dynamicTableService.tableExists(database, tableCode)) {
            throw new ServiceException("表单数据表不存在，请重新保存表单后重试");
        }
        // 处理查询字段的类型
        List<DynamicFormColumnDTO> fields = dynamicFormColumnService.selectTableColumnByCode(tableCode);
        // 子表行字段条件（列编码为「子表字段.行内字段」）不是主表列，先摘出来转成关联子表 EXISTS，
        // 否则按主表列名校验会直接报错
        Map<String, String> subTableConditions = takeSubTableConditions(data, database, tableCode, fields);
        FormTransferTable.dataGetSQL(data, fields);
        data.putAll(subTableConditions);

        data.remove("keywords");
        PageResultInfo map = dynamicTableService.findByPage(database, tableCode, data);
        normalizeTemporalValues(map);
        translateCreatorAndDept(map);
        fillSubTableColumnSummaries(map, tableCode, fields);
        return map;
    }

    /**
     * 列收敛：只保留关联配置用到的字段（含驼峰/下划线两种键写法）与公共列，其余键从数据行移除
     */
    @SuppressWarnings("unchecked")
    private void retainColumns(PageResultInfo pageResult, Set<String> visibleColumns) {
        List<Object> records = pageResult.getList();
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<String> keep = new HashSet<>();
        for (String column : visibleColumns) {
            keep.add(normalizeColumnKey(column));
        }
        for (Object record : records) {
            if (record instanceof Map) {
                ((Map<String, Object>) record).keySet()
                        .removeIf(key -> key != null && !keep.contains(normalizeColumnKey(key)));
            }
        }
    }

    /** 列键归一：去掉下划线并转小写，使 userName / user_name / USERNAME 视为同一列 */
    private String normalizeColumnKey(String key) {
        return key.replace("_", "").toLowerCase();
    }

    /**
     * 摘出子表行字段的查询条件并转为 WHERE 片段：命中的键从提交数据中移除，
     * 由 {@link DynamicSubTableService#buildRowFieldConditions} 按当前表单子表定义解析子表表名与行内列；
     * 解析不到（子表或行内字段已被删除）会报错提示「该子表字段已失效」，不静默忽略
     */
    private Map<String, String> takeSubTableConditions(Map<String, Object> data, String database, String tableCode,
                                                       List<DynamicFormColumnDTO> fields) {
        Map<String, Object> filters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (FormTransferTable.isSubTableColumnCode(entry.getKey())) {
                filters.put(entry.getKey(), entry.getValue());
            }
        }
        if (filters.isEmpty()) {
            return Map.of();
        }
        filters.keySet().forEach(data::remove);
        DynamicForm dynamicForm = dynamicFormService.selectDynamicFormByTableCode(tableCode);
        JSONObject metaData = dynamicForm == null ? null : dynamicForm.getMetaData();
        if (dynamicForm == null) {
            logger.warn("子表查询条件按失效处理：表单定义不存在 tableCode={}", tableCode);
        }
        return dynamicSubTableService.buildRowFieldConditions(database, tableCode, metaData, filters, fields);
    }

    /**
     * 填充子表聚合列：dynamic_form_column 中 filedCode=「子表字段.行内列名」的列
     * （子表行内字段不再平铺进主表），值从主表行的子表 JSON 快照解析行数组后按行内字段拼接（顿号分隔）
     */
    @SuppressWarnings("unchecked")
    private void fillSubTableColumnSummaries(PageResultInfo pageResult, String tableCode,
                                             List<DynamicFormColumnDTO> fields) {
        List<Object> records = pageResult.getList();
        if (records == null || records.isEmpty() || fields == null || fields.isEmpty()) {
            return;
        }
        // 收集「子表字段.行内列名」形态的列，按子表字段分组
        Map<String, List<String[]>> summaryColumns = new HashMap<>();
        for (DynamicFormColumnDTO field : fields) {
            String code = field.getFiledCode();
            if (code != null && code.indexOf('.') > 0) {
                int dot = code.indexOf('.');
                summaryColumns.computeIfAbsent(code.substring(0, dot), k -> new ArrayList<>())
                        .add(new String[]{code, code.substring(dot + 1)});
            }
        }
        if (summaryColumns.isEmpty()) {
            return;
        }
        for (Object o : records) {
            Map<String, Object> row = (Map<String, Object>) o;
            for (Map.Entry<String, List<String[]>> entry : summaryColumns.entrySet()) {
                // 列配置前缀是设计器原名（可含连字符），主表行键是归一后的下划线列名，取值前归一
                Object raw = row.get(entry.getKey());
                if (raw == null) {
                    raw = row.get(FormTransferTable.toDbColumnName(entry.getKey()));
                }
                if (raw == null || "".equals(raw.toString())) {
                    continue;
                }
                List<Map<String, Object>> rows = parseRows(raw);
                if (rows.isEmpty()) {
                    continue;
                }
                for (String[] col : entry.getValue()) {
                    StringBuilder summary = new StringBuilder();
                    for (Map<String, Object> childRow : rows) {
                        Object value = pickRowValue(childRow, col[1]);
                        if (value == null || "".equals(value.toString())) {
                            continue;
                        }
                        if (summary.length() > 0) {
                            summary.append("、");
                        }
                        summary.append(value);
                    }
                    row.put(col[0], summary.toString());
                }
            }
        }
    }

    /**
     * 解析主表行中的子表 JSON 快照为行数组，非法 JSON 返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRows(Object raw) {
        try {
            Object parsed = com.alibaba.fastjson2.JSON.parse(raw.toString());
            if (parsed instanceof List) {
                return (List<Map<String, Object>>) parsed;
            }
        } catch (Exception e) {
            logger.debug("子表 JSON 快照解析失败：{}", raw);
        }
        return new ArrayList<>();
    }

    /**
     * 行内字段取值：列名（下划线）→ 驼峰 逐级回退
     */
    private Object pickRowValue(Map<String, Object> row, String column) {
        if (row.containsKey(column)) {
            return row.get(column);
        }
        return row.get(camelCase(column));
    }

    /**
     * 此为配置的菜单路径
     *
     * @param tableCode 表单的编码
     * @return 返回表单的数据
     */
    @ServiceRequest
    @Operation(summary = "根据tableCode查询form数据的列信息")
    @GetMapping("/inner/dbs/open/table/column/{tableCode}")
    public CommonResult<List<DynamicFormColumnDTO>> findTableColumnByCode(@PathVariable("tableCode") String tableCode) {
        logger.info("findTableColumnByCode tableCode {}", tableCode);
        return CommonResult.ok(dynamicFormColumnService.selectTableColumnByCode(tableCode));
    }

    /**
     * 当前数据源：优先取登录用户上下文，取不到时回退主数据源
     */
    private String currentDatabase() {
        try {
            String database = SecurityUtils.getDatabaseName();
            if (StringUtils.isNotEmpty(database)) {
                return database;
            }
        } catch (Exception e) {
            logger.debug("登录上下文无业务库信息，回退主数据源");
        }
        return DEFAULT_DATABASE;
    }

    private String stringField(Map<String, Object> data, String field) {
        if (data == null) {
            return null;
        }
        Object value = data.get(field);
        return value == null || StringUtils.isEmpty(value.toString()) ? null : value.toString();
    }

    /** 请求体优先、查询串兜底（自定义 dataUrl 的组件不把授权线索放进请求体） */
    private String firstNotEmpty(String bodyValue, String paramValue) {
        return StringUtils.isNotEmpty(bodyValue) ? bodyValue : paramValue;
    }

    /**
     * 将分页结果中的创建人/部门ID批量翻译为昵称/部门名
     */
    @SuppressWarnings("unchecked")
    private void translateCreatorAndDept(PageResultInfo pageResult) {
        List<Object> records = pageResult.getList();
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<String> userIds = new LinkedHashSet<>();
        Set<String> deptIds = new LinkedHashSet<>();
        for (Object o : records) {
            Map<String, Object> row = (Map<String, Object>) o;
            collectId(row, TableFieldConstant.FIELD_CREATED_BY, userIds);
            collectId(row, TableFieldConstant.FIELD_DEPARTMENT_ID, deptIds);
        }
        Map<String, String> userNames = findUserNames(userIds);
        Map<String, String> deptNames = findDeptNames(deptIds);
        for (Object o : records) {
            Map<String, Object> row = (Map<String, Object>) o;
            String creator = Objects.toString(row.get(TableFieldConstant.FIELD_CREATED_BY), null);
            if (creator != null) {
                if (USER_TYPE_SYSTEM.equals(creator)) {
                    row.put(TableFieldConstant.FIELD_CREATED_NAME, UserConstants.USER_TYPE_SYSTEM_NAME);
                } else {
                    String name = userNames.get(creator);
                    if (name != null) {
                        row.put(TableFieldConstant.FIELD_CREATED_NAME, name);
                    }
                }
            }
            String departmentId = Objects.toString(row.get(TableFieldConstant.FIELD_DEPARTMENT_ID), null);
            if (departmentId != null) {
                String deptName = deptNames.get(departmentId);
                if (deptName != null) {
                    row.put(TableFieldConstant.FIELD_DEPARTMENT_NAME, deptName);
                }
            }
        }
    }

    private void collectId(Map<String, Object> row, String field, Set<String> ids) {
        Object value = row.get(field);
        if (value != null && StringUtils.isNotEmpty(value.toString())) {
            ids.add(value.toString());
        }
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
            logger.warn("批量查询用户昵称失败 userIds {}", userIds, e);
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
            logger.warn("批量查询部门名称失败 deptIds {}", deptIds, e);
        }
        return names;
    }
}
