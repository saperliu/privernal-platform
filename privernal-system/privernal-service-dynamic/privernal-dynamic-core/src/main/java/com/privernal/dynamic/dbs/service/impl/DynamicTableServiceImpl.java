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
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dba.constants.Constants;
import com.privernal.dynamic.dba.dialect.DatabaseDialect;
import com.privernal.dynamic.dba.mapper.DynamicTableDataMapper;
import com.privernal.dynamic.dba.mapper.DynamicTableMapper;
import com.privernal.dynamic.dba.service.AutoCreateTableService;
import com.privernal.dynamic.dba.utils.ClassTools;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dba.utils.JsonValueCodec;
import com.privernal.dynamic.dba.utils.ShadowColumnSupport;
import com.privernal.dynamic.dba.vo.MysqlTableInfo;
import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.mapper.DynamicFormColumnMapper;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import com.privernal.dynamic.dbs.service.DynamicSubTableService;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.dynamic.dbs.support.SafeConditionFragment;
import com.privernal.dynamic.advanced.engine.DataWriteAttributes;
import com.privernal.dynamic.advanced.engine.LinkageDispatcher;
import com.privernal.dynamic.advanced.engine.LinkageIdemUtils;
import com.privernal.system.api.WebhookEventData;
import com.privernal.system.api.WebhookPublisher;
import com.privernal.system.constant.WebhookEventTypes;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 动态表的创建及数据操作服务实现
 * <p>
 * 基于方言接口 {@link DatabaseDialect} 实现跨数据库的动态表操作，
 * 支持 MySQL / PostgreSQL / SQL Server 等多种数据库。
 * 所有 SQL 构建均委托给 {@link DatabaseDialect} 实现，
 * 本服务只负责业务逻辑编排与数据持久化。
 * </p>
 *
 * @author saperliu
 * @version 1.1.0
 * @see DynamicTableService
 * @see DatabaseDialect
 */
@Slf4j
@Transactional
@Service
public class DynamicTableServiceImpl implements DynamicTableService {

    /**
     * 合法表名/列名白名单：表名与列名经 ${} 拼接进 SQL，必须先行校验防注入
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /**
     * 排序方向白名单，order by 的方向值原样拼入 SQL
     */
    private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc", "ASC", "DESC");

    @Autowired
    private DynamicTableDataMapper dynamicTableDataMapper;

    @Autowired
    private DynamicTableMapper dynamicTableMapper;

    @Autowired
    private AutoCreateTableService autoCreateTableService;

    @Autowired
    private DatabaseDialect databaseDialect;

    @Autowired
    private DynamicSubTableService dynamicSubTableService;

    @Autowired
    private WebhookPublisher webhookPublisher;

    /**
     * SYNC 联动规则的直调入口（§7.5.2）。ObjectProvider 惰性解析：联动分发器依赖动作处理器，
     * 处理器又依赖本服务，直接注入会形成 bean 环。
     */
    @Autowired
    private ObjectProvider<LinkageDispatcher> linkageDispatcher;

    @Autowired
    private DynamicFormMapper dynamicFormMapper;

    @Autowired
    private DynamicFormColumnMapper dynamicFormColumnMapper;

    @org.springframework.context.annotation.Lazy
    @Autowired
    private com.privernal.job.api.ScheduleTaskClient scheduleTaskClient;

    /**
     * P0-5/P2-2：数据删除级联取消到期任务时，由注册器按该表单启用中的 DATE_FIELD 规则
     * 逐个精确取消（注册路径同样用它），避免在此重复实现规则查询与 key 构造。
     */
    @Autowired
    private com.privernal.dynamic.advanced.trigger.DateExpiryRegistrar dateExpiryRegistrar;

    /** 数据删除级联取消用：deleteById 处于 @DS("#database") 业务库上下文，
     *  system_schedule_task 在主库，必须换线程离开数据源栈（仿 WebhookEventService.enqueueAsync） */
    private final java.util.concurrent.ExecutorService scheduleCancelExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "schedule-cancel");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 保存或更新动态表数据
     * <p>
     * 根据数据中是否包含主键 {@code id} 判断执行新增或更新操作：
     * <ul>
     *   <li>包含有效 id -> 执行更新</li>
     *   <li>不包含 id -> 执行新增，自动生成 UUID 作为主键</li>
     * </ul>
     * 自动过滤系统字段（table_name, meta_data 等）避免写入动态表。
     * </p>
     *
     * @param database  数据源名称
     * @param tableName 目标表名
     * @param data      数据键值对（驼峰命名）
     * @return 主键 ID（新增时返回新生成的 ID，更新时返回原 ID）
     */
    @Override
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public String saveOrUpdate(String database, String tableName, Map<String, Object> data) {

        Assert.notNull(tableName, "tableName must not be null");
        Assert.hasLength(tableName, "tableName must not be empty");
        Assert.notNull(data, "data must not be null");
        assertValidIdentifier(tableName);

        boolean isSave = true;
        String updateId = null;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Map || val instanceof Collection) {
                // Map/集合（如子表行数组）值序列化为 JSON，避免 Java 序列化对象直接写入文本列。
                // 开关打开后，纯标量集合（多选/附件/多人员）统一落 {"v":[...]} 信封，
                // 含对象的集合（子表行）保持数组形态以兼容既有回显链路。
                val = ShadowColumnSupport.isJsonProtocolEnabled()
                        ? JsonValueCodec.wrap(val)
                        : JSON.toJSONString(val);
                entry.setValue(val);
            }
            if (Constants.KEY_FIELD.equalsIgnoreCase(entry.getKey())) {
                String id = val == null ? null : val.toString();
                if (StringUtils.isNotEmpty(id)) {
                    updateId = id;
                    isSave = false;
                }
            }
        }

        Map<String, Object> dataTemp = transferName(data);
        dataTemp.keySet().removeAll(Constants.SYSTEM_FIELDS);
        // 影子列（{业务列}_v0）由数据库生成，用户提交里若带同名键必须剔除：
        // 给生成列赋值会被 MySQL 拒绝（Generated column may not be assigned），
        // 而设计器/流程变量里出现同名字段是有可能的（如字段就叫 xxx_v0）
        dataTemp.keySet().removeIf(JsonValueCodec::isShadowColumn);
        // 剔除动态表中不存在的列（历史回显脏键/别名残留），避免 Unknown column 导致保存失败；
        // id 为主键列恒保留。元数据查询查的是 information_schema，必须忽略组织数据范围
        // （拦截器会拼 organization_id 条件，information_schema 无此列）
        Boolean oldIgnoreForColumns = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            List<MysqlTableInfo> tableColumns = dynamicTableMapper.executeQueryForColumns(databaseDialect.buildGetTableColumnsSql(tableName));
            Set<String> existingColumns = new HashSet<>(ClassTools.getPropertyValueList(tableColumns, MysqlTableInfo.COLUMN_NAME_KEY));
            dataTemp.keySet().removeIf(key ->
                    !Constants.KEY_FIELD.equalsIgnoreCase(key) && !existingColumns.contains(key));
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnoreForColumns);
        }
        log.debug("剔除动态表不存在列后保存：table={}, columns={}", tableName, dataTemp.keySet());
        // 写入属性：来源标记/级联深度/免登代理身份（调用方 push，作用域一次方法调用，§7.5.3）
        DataWriteAttributes.Snapshot writeAttrs = DataWriteAttributes.peek();
        // before 快照：更新分支在写之前按主键读一次，watchFields 对比与幂等键都吃这一份（§7.2.1）
        Map<String, Object> beforeRow = null;
        if (!isSave) {
            beforeRow = findById(database, tableName, updateId);
        }
        // 幂等键由发布点生成且稳定可重放（§7.5.1）：同一次写入的多重投递同一值
        long writeMillis = System.currentTimeMillis();
        if (isSave) {
            String id = IdWorker.get32UUID();
            dataTemp.put(Constants.KEY_FIELD, id);
            if (StringUtils.isNull(dataTemp.get("status")) || StringUtils.isEmpty(dataTemp.get("status").toString())) {
                dataTemp.put("status", 1);
            }
            // 列配置了「保存数据的默认值」且本次提交无值时，落库默认值（业务字段；系统字段锁定不可配）
            applyColumnDefaults(tableName, dataTemp);
            // 动态表 created_by/department_id 为 NOT NULL，新增时补齐审计字段（与更新分支的 updated_* 对应）
            dataTemp.put("created_time", new Date());
            String userId = SecurityUtils.getUserId();
            if (StringUtils.isNotEmpty(userId)) {
                dataTemp.put("created_by", userId);
                dataTemp.put("department_id", SecurityUtils.getDepartmentId());
            } else if (StringUtils.isNotEmpty(writeAttrs.proxyCreatedBy)) {
                // 免登链路（外链）落代理用户，保证归属与审计（§5.1）
                dataTemp.put("created_by", writeAttrs.proxyCreatedBy);
                dataTemp.put("department_id", writeAttrs.proxyDepartmentId);
            }
            // organization_id 常态由组织拦截器回填；联动/免登线程忽略了组织拦截器，这里按
            // 事件/会话显式补（写入属性携带的优先，上下文可能被子调用清掉）
            Object existingOrg = dataTemp.get("organization_id");
            if (existingOrg == null || StringUtils.isEmpty(String.valueOf(existingOrg))) {
                String orgId = StringUtils.isNotEmpty(writeAttrs.organizationId)
                        ? writeAttrs.organizationId : resolveOrganizationIdForEvent();
                if (StringUtils.isNotEmpty(orgId)) {
                    dataTemp.put("organization_id", orgId);
                }
            }
            log.debug("新增动态表数据：table={}, id={}", tableName, id);
            dynamicTableDataMapper.insert(tableName, dataTemp);
            // SYNC 联动规则在业务事务内直调（§7.5.2）：失败向上抛，本事务整体回滚、事件不发布
            dispatchSyncLinkage(database, tableName, id, "CREATE", null, dataTemp, writeAttrs, writeMillis);
            // 建表链路已让 table_code 与物理表名同步加组织前缀，两者同值，这里按 tableCode 对外发事件
            publishDataEvent(WebhookEventTypes.DATA_CREATED, tableName, id, data, null, writeAttrs, writeMillis);
            dateExpiryRegistrar.onDataWrite(database, tableName, data, id);
            return id;
        } else {
            dataTemp.put("updated_time", new Date());
            // 机器调用（OpenAPI）没有登录态，由调用方在数据里显式带 updatedBy；有登录态时仍以会话为准
            String updateUserId = SecurityUtils.getUserId();
            if (StringUtils.isNotEmpty(updateUserId)) {
                dataTemp.put("updated_by", updateUserId);
            } else if (StringUtils.isNotEmpty(writeAttrs.proxyCreatedBy)) {
                dataTemp.put("updated_by", writeAttrs.proxyCreatedBy);
            }
            log.debug("更新动态表数据：table={}, id={}", tableName, updateId);
            dynamicTableDataMapper.update(tableName, dataTemp);
            dispatchSyncLinkage(database, tableName, updateId, "UPDATE", beforeRow, dataTemp, writeAttrs, writeMillis);
            publishDataEvent(WebhookEventTypes.DATA_UPDATED, tableName, updateId, data, beforeRow, writeAttrs, writeMillis);
            dateExpiryRegistrar.onDataWrite(database, tableName, data, updateId);
            return updateId;
        }
    }

    /**
     * SYNC 联动直调（§7.5.2）：在业务事务内执行 async_mode=SYNC 的规则；
     * 无匹配规则时零开销。组织上下文沿用当前调用方（写链路必带）。
     */
    private void dispatchSyncLinkage(String database, String tableName, String dataId, String eventType,
                                     Map<String, Object> beforeRow, Map<String, Object> afterRow,
                                     DataWriteAttributes.Snapshot writeAttrs, long writeMillis) {
        LinkageDispatcher dispatcher = linkageDispatcher.getIfAvailable();
        if (dispatcher == null) {
            return;
        }
        String organizationId = resolveOrganizationIdForEvent();
        if (StringUtils.isEmpty(organizationId)) {
            return;
        }
        String eventId = LinkageIdemUtils.dataEventId(writeAttrs.sourceTag, database, tableName, dataId, writeMillis);
        // SYNC 规则的条件/模板读"写入后的完整行视图"：before 全行 ∪ 本次提交键（提交优先）
        Map<String, Object> afterView = afterRow;
        if (afterRow != null && beforeRow != null) {
            afterView = new HashMap<>(beforeRow);
            afterView.putAll(afterRow);
        }
        try {
            dispatcher.dispatchSync(organizationId, database, tableName, dataId, eventType,
                    beforeRow, afterView, writeAttrs.sourceTag, SecurityUtils.getUserId(),
                    writeAttrs.depth, eventId);
        } catch (Exception e) {
            // SYNC 语义：动作失败整体回滚（§11 验收），这里把异常透传给事务边界
            throw e instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(e);
        }
    }

    /**
     * 组织上下文兜底：登录写入链路 OrganizationContextHolder 常为空（数据权限拦截器查 SQL 时
 * 走会话兜底、发事件时却拿不到），这是 预警的隐性故障——
     * 库与组织同源于会话，这里按同一次序兜底，缺两者才放弃发事件。
     */
    private String resolveOrganizationIdForEvent() {
        String organizationId = OrganizationContextHolder.getOrganizationId();
        if (StringUtils.isEmpty(organizationId)) {
            organizationId = SecurityUtils.getOrganizationId();
        }
        return organizationId;
    }

    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    public boolean tableExists(String database, String tableName) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);
            int exist = dynamicTableMapper.executeQueryForCount(databaseDialect.buildTableExistsSql(tableName));
            return exist > 0;
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
        }
    }

    /**
     * 应用「保存数据的默认值」：新增数据时，列配置了 default_value 且本次提交无值（缺键/空串），
     * 则落库配置值。仅业务字段可配保存默认值（系统字段在列配置更新链路锁定），按 filed_code 匹配
     * 提交键（transferName 已把提交键转为下划线列名）。查列配置失败不阻断保存主链路。
     *
     * @param tableName 物理表名（= dynamic_form.table_code）
     * @param dataTemp  待插入数据（下划线列名键）
     */
    private void applyColumnDefaults(String tableName, Map<String, Object> dataTemp) {
        try {
            DynamicForm form = dynamicFormMapper.selectOne(
                    new LambdaPrivernalWrapper<DynamicForm>().eq(DynamicForm::getTableCode, tableName));
            if (form == null) {
                return;
            }
            LambdaPrivernalWrapper<DynamicFormColumn> columnWrapper = new LambdaPrivernalWrapper<>();
            columnWrapper.eq(DynamicFormColumn::getFormId, form.getId());
            List<DynamicFormColumn> columns = dynamicFormColumnMapper.selectList(columnWrapper);
            for (DynamicFormColumn column : columns) {
                String code = column.getFiledCode();
                String defaultValue = column.getDefaultValue();
                if (DynamicFormColumn.isSystemField(code) || StringUtils.isEmpty(defaultValue)) {
                    continue;
                }
                // 「如果无数据则存默认值」：字段未提交或提交了空值都算无数据；已有值不覆盖
                Object value = dataTemp.get(code);
                if (value != null && !(value instanceof String s && StringUtils.isEmpty(s))) {
                    continue;
                }
                dataTemp.put(code, defaultValue);
            }
        } catch (Exception e) {
            log.warn("应用列保存默认值失败，跳过：table={} error={}", tableName, e.getMessage());
        }
    }

    /**
     * 数据变更对外通知。组织取当前数据范围上下文，取不到就不发：
     * 订阅按组织匹配，缺组织既会漏配也可能错配到别的组织的订阅。
     * <p>
     * v1.2 富事件：增量携带 before 快照 / sourceTag / databaseName / 稳定幂等键（§7.2.1/§7.5.1），
     * 内部监听器（联动）与 webhook 订阅扇出在同一 afterCommit 时机被通知。
     */
    private void publishDataEvent(String eventType, String tableCode, String bizId, Map<String, Object> data,
                                  Map<String, Object> beforeData, DataWriteAttributes.Snapshot writeAttrs,
                                  long writeMillis) {
        String organizationId = resolveOrganizationIdForEvent();
        if (StringUtils.isEmpty(organizationId)) {
            log.warn("动态表数据变更缺少组织上下文，跳过 Webhook 事件 eventType={} tableCode={} bizId={}",
                    eventType, tableCode, bizId);
            return;
        }
        WebhookEventData event = new WebhookEventData();
        event.setEventType(eventType);
        event.setOrganizationId(organizationId);
        event.setTableCode(tableCode);
        event.setBizId(bizId);
        event.setDatabaseName(databaseNameOfContext());
        event.setData(data == null ? null : new JSONObject(data));
        event.setBeforeData(beforeData == null ? null : new JSONObject(beforeData));
        event.setOperatorId(SecurityUtils.getUserId());
        event.setSourceTag(writeAttrs.sourceTag);
        event.setDepth(writeAttrs.depth);
        event.setEventId(LinkageIdemUtils.dataEventId(writeAttrs.sourceTag, event.getDatabaseName(), tableCode, bizId, writeMillis));
        event.setOccurredAt(new Date(writeMillis));
        webhookPublisher.publish(event);
    }

    /** 业务库名：优先当前事务已解析的数据源，取不到回落组织配置（免登/异步链路显式压栈过） */
    private String databaseNameOfContext() {
        try {
            String current = com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.peek();
            if (StringUtils.isNotEmpty(current)) {
                return current;
            }
        } catch (Exception e) {
            // dynamic-datasource 未启用栈上下文时忽略
        }
        // 会话登录链路：库名取会话（与写入方 getDatabaseName() 同源）
        String sessionDb = SecurityUtils.getDatabaseName();
        return StringUtils.isEmpty(sessionDb) ? null : sessionDb;
    }

    /**
     * 将 Map 的 key 由驼峰命名转换为下划线命名，并校验转换后的列名为合法标识符
     *
     * @param data 原始数据
     * @return 转换后的数据
     */
    private Map<String, Object> transferName(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> newMap = new HashMap<>();
        data.forEach((key, value) -> {
            // 与建表列名推导（FormTransferTable.toDbColumnName）保持同一转换，字段编码中的连字符等非法字符清洗为下划线
            String newKey = FormTransferTable.toDbColumnName(key);
            assertValidIdentifier(newKey);
            newMap.put(newKey, value);
        });
        return newMap;
    }

    /**
     * 校验表名/列名为合法标识符，不合法直接拒绝
     */
    private static void assertValidIdentifier(String name) {
        if (name == null || !IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("非法的表名或列名：" + name);
        }
    }

    /**
     * 根据 ID 删除一条数据（软删：置 deleted_flag='Y'，查询链路统一过滤；
     * DATA_DELETED 事件仍照常发布，订阅方按 bizId 同步删除语义）
     *
     * @param database  数据源名称
     * @param tableName 目标表名
     * @param id        数据 ID
     */
    @Override
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteById(String database, String tableName, String id) {
        Assert.notNull(tableName, "tableName must not be null");
        Assert.notNull(id, "id must not be null");
        Assert.hasLength(tableName, "tableName must not be empty");
        Assert.hasLength(id, "id must not be empty");
        assertValidIdentifier(tableName);
        log.debug("删除动态表数据：table={}, id={}", tableName, id);
        // 删除事件必须带被删数据快照（联动删除动作要引用被删行，§7.2/§7.2.1）
        Map<String, Object> beforeRow = findById(database, tableName, id);
        DataWriteAttributes.Snapshot writeAttrs = DataWriteAttributes.peek();
        long writeMillis = System.currentTimeMillis();
        // 软删（T15）：物理表自带 deleted_* 基线列；删除人=会话用户，缺会话落代理身份
        String deletedBy = SecurityUtils.getUserId();
        if (StringUtils.isEmpty(deletedBy)) {
            deletedBy = StringUtils.isNotEmpty(writeAttrs.proxyCreatedBy) ? writeAttrs.proxyCreatedBy : "system";
        }
        dynamicTableDataMapper.deleteById(tableName, id, deletedBy);
        dispatchSyncLinkage(database, tableName, id, "DELETE", beforeRow, null, writeAttrs, writeMillis);
        publishDataEvent(WebhookEventTypes.DATA_DELETED, tableName, id, null, beforeRow, writeAttrs, writeMillis);
        cancelDataScheduleTasks(tableName, id);
    }

    /**
     * 批量删除数据
     *
     * @param database  数据源名称
     * @param tableName 目标表名
     * @param data      ID 列表
     */
    @Override
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteByIds(String database, String tableName, List<String> data) {
        Assert.notNull(tableName, "tableName must not be null");
        Assert.notNull(data, "data must not be null");
        Assert.hasLength(tableName, "tableName must not be empty");
        Assert.notEmpty(data, "data must not be empty");
        assertValidIdentifier(tableName);
        log.debug("批量删除动态表数据：table={}, ids={}", tableName, data);
        // 删除事件必须带被删数据快照（联动删除动作要引用被删行，§7.2/§7.2.1）
        DataWriteAttributes.Snapshot writeAttrs = DataWriteAttributes.peek();
        long writeMillis = System.currentTimeMillis();
        Map<String, Map<String, Object>> beforeRows = new HashMap<>();
        for (String id : data) {
            beforeRows.put(id, findById(database, tableName, id));
        }
        String deletedBy = SecurityUtils.getUserId();
        if (StringUtils.isEmpty(deletedBy)) {
            DataWriteAttributes.Snapshot attrs = DataWriteAttributes.peek();
            deletedBy = StringUtils.isNotEmpty(attrs.proxyCreatedBy) ? attrs.proxyCreatedBy : "system";
        }
        dynamicTableDataMapper.deleteByIds(tableName, data, deletedBy);
        // 一条数据一个事件：订阅方按 bizId 增量同步，不需要知道这是对端的一次批量操作
        for (String id : data) {
            Map<String, Object> beforeRow = beforeRows.get(id);
            dispatchSyncLinkage(database, tableName, id, "DELETE", beforeRow, null, writeAttrs, writeMillis);
            publishDataEvent(WebhookEventTypes.DATA_DELETED, tableName, id, null, beforeRow, writeAttrs, writeMillis);
        }
        data.forEach(item -> cancelDataScheduleTasks(tableName, item));
    }

    /**
 * 数据删除级联取消到期任务（②）：biz_key 形如
     * {@code linkdate:{tableCode}:{ruleCode}:{dataId}}（P0-2 统一由 LinkageBizKeys 构造）。
     * <p>
     * 仿 publishDataEvent：缺组织上下文只 warn 不静默；异步执行需自行处理线程上下文。
     * 取消动作委托给 {@link com.privernal.dynamic.advanced.trigger.DateExpiryRegistrar}，
     * 由它按该表单启用中的 DATE_FIELD 规则逐个精确取消（P0-5 修复 2 / P2-2），
     * 不再用 {@code biz_key LIKE '%:dataId'} 前导通配。
     */
    private void cancelDataScheduleTasks(String tableName, String dataId) {
        // P0-5：普通登录链路 OrganizationContextHolder 为空（仅 OpenAPI 过滤器写入），
        // 必须与发事件同款回退到会话组织，否则界面删数据的到期任务永不取消
        String organizationId = resolveOrganizationIdForEvent();
        if (StringUtils.isEmpty(organizationId)) {
            log.warn("动态表数据删除缺少组织上下文，到期任务未级联取消 dataId={}", dataId);
            return;
        }
        scheduleCancelExecutor.submit(() -> {
            try {
                int cancelled = dateExpiryRegistrar.cancelForDeletedRow(tableName, dataId);
                if (cancelled > 0) {
                    log.info("数据删除级联取消到期提醒 table={} dataId={} 取消 {} 条", tableName, dataId, cancelled);
                }
            } catch (Exception e) {
                log.error("数据删除级联取消到期任务失败 table={} dataId={}", tableName, dataId, e);
            }
        });
    }

    /**
     * 分页查询动态表数据
     * <p>
     * 从过滤条件中提取分页参数（pageNum, pageSize）和排序参数（orderBys），
     * 自动计算起始行偏移量，调用 Mapper 执行查询并组装分页结果。
     * </p>
     *
     * @param database  数据源名称
     * @param tableName 目标表名
     * @param data      过滤条件（键值对形式，支持包含 pageNum、pageSize、orderBys）
     * @return 分页查询结果
     */
    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public PageResultInfo findByPage(String database, String tableName, Map<String, Object> data) {
        Assert.notNull(tableName, "tableName must not be null");
        Assert.notNull(data, "data must not be null");
        Assert.hasLength(tableName, "tableName must not be empty");
        assertValidIdentifier(tableName);

        int startRowVal = 0;
        int pageSizeVal = Constants.DEFAULT_PAGE_SIZE;
        int pageNumVal = Constants.DEFAULT_PAGE_NUM;
        LinkedHashMap<String, String> orderByVal = null;
        PageResultInfo pageResultInfo = new PageResultInfo();

        for (String key : data.keySet()) {
            Object value = data.get(key);
            if (Constants.PAGE_SIZE.equals(key)) {
                pageSizeVal = toInt(value, Constants.DEFAULT_PAGE_SIZE);
            } else if (Constants.PAGE_NUM.equals(key)) {
                if (value != null) {
                    pageNumVal = toInt(value, Constants.DEFAULT_PAGE_NUM);
                }
            } else if (Constants.ORDER_BY.equals(key)) {
                orderByVal = (LinkedHashMap<String, String>) value;
            }
        }
        // orderBys 已提取，残留的 Map 值若进入 WHERE 片段会导致 SQL 语法错误
        data.remove(Constants.ORDER_BY);
        if (orderByVal != null && !orderByVal.isEmpty()) {
            // 键转为拼进 order by 的表达式：主表列加反引号（列编码可能是 order/desc 等保留字），
            // 子表行字段不是主表列，按极值子查询排序；方向值仍单独按白名单校验
            LinkedHashMap<String, String> expressions = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : orderByVal.entrySet()) {
                String column = entry.getKey();
                String direction = entry.getValue();
                if (direction == null || !SORT_DIRECTIONS.contains(direction)) {
                    throw new IllegalArgumentException("非法的排序方向：" + direction);
                }
                if (FormTransferTable.isSubTableColumnCode(column)) {
                    expressions.put(dynamicSubTableService.buildRowFieldSortExpression(database, tableName, column,
                            "asc".equalsIgnoreCase(direction)), direction);
                } else {
                    assertValidIdentifier(column);
                    expressions.put("`" + column + "`", direction);
                }
            }
            orderByVal = expressions;
        }
        if (pageSizeVal <= 0) {
            pageSizeVal = Constants.DEFAULT_PAGE_SIZE;
        }
        if (pageNumVal <= 0) {
            pageNumVal = Constants.DEFAULT_PAGE_NUM;
        }
        Map<Object, Object> tableMap = new HashMap<>();
        // S1 修复：条件片段必须先经安全翻译器重建（字段白名单 + 参数绑定），
        // 语法外输入一律拒绝——这里的数据可能来自匿名分享端点 /pub/view/list/{token}
        Map<String, Object> condParams = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (Constants.PAGE_NUM.equals(key) || Constants.PAGE_SIZE.equals(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null || "".equals(value)) {
                continue;
            }
            if (!(value instanceof String condition)) {
                throw new IllegalArgumentException("查询条件值必须是字符串片段：" + key);
            }
            Object[] parsed = SafeConditionFragment.parse(condition.trim());
            entry.setValue(parsed[0]);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedParams = (Map<String, Object>) parsed[1];
            condParams.putAll(parsedParams);
        }
        tableMap.put(tableName, data);

        // condParams 必须作为独立 @Param 传入：SafeConditionFragment 产出的
        // #{condParams.pN} 占位从 mapper 根参数解析，塞进 tableMap 内层时
        // MyBatis 找不到（BindingException: Parameter 'condParams' not found）
        int queryCount = dynamicTableDataMapper.countData(tableName, tableMap, condParams);
        if (queryCount == 0) {
            pageResultInfo.setPages(0);
        } else {
            int totalPage = (queryCount + pageSizeVal - 1) / pageSizeVal;
            if (totalPage <= pageNumVal) {
                pageNumVal = totalPage;
            }
            if (pageNumVal != 1) {
                startRowVal = (pageNumVal - 1) * pageSizeVal;
            }
            tableMap.put(Constants.PAGE_NUM, startRowVal);
            tableMap.put(Constants.PAGE_SIZE, pageSizeVal);

            if (orderByVal != null && !orderByVal.isEmpty()) {
                tableMap.put(Constants.ORDER_BY, orderByVal);
            }
            List<Map<String, Object>> query = dynamicTableDataMapper.selectList(tableName, tableMap, condParams);

            pageResultInfo.setList(new ArrayList<>(query));
            pageResultInfo.setPages(totalPage);
        }

        pageResultInfo.setPageNum(pageNumVal);
        pageResultInfo.setPageSize(pageSizeVal);
        pageResultInfo.setTotal(queryCount);

        log.debug("分页查询动态表：table={}, page={}/{}, total={}", tableName, pageNumVal, pageSizeVal, queryCount);
        return pageResultInfo;
    }

    /**
     * 将分页参数安全转为 int，null 或非数值时返回默认值
     */
    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                log.warn("非法的分页参数，使用默认值 {}：{}", defaultValue, value);
            }
        }
        return defaultValue;
    }

    /**
     * 根据 ID 查询单条数据
     *
     * @param database  数据源名称
     * @param tableName 目标表名
     * @param id        数据 ID
     * @return 数据内容键值对
     */
    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    public HashMap<String, Object> findById(String database, String tableName, String id) {
        Assert.notNull(tableName, "tableName must not be null");
        Assert.notNull(id, "id must not be null");
        Assert.hasLength(tableName, "tableName must not be empty");
        Assert.hasLength(id, "id must not be empty");
        assertValidIdentifier(tableName);
        return dynamicTableDataMapper.selectById(tableName, id);
    }

    /**
     * 直接执行 SQL 查询（仅允许只读查询）
     * <p>
     * SQL 原样执行，为防止脚本误操作只允许单条 SELECT/WITH 语句，
     * 写操作请使用 {@link #saveOrUpdate}、{@link #deleteById} 等专用方法。
     * </p>
     *
     * @param database  数据源名称
     * @param tableName 目标表名
     * @param sql       查询 SQL
     * @return 查询结果列表，若无结果则返回空列表
     */
    @Override
    @DS("#database")
    @Transactional(readOnly = true)
    public List<HashMap<String, Object>> querySql(String database, String tableName, String sql) {
        Assert.notNull(sql, "sql must not be null");
        Assert.hasLength(sql, "sql must not be empty");
        assertValidIdentifier(tableName);
        assertReadonlySql(sql);
        List<HashMap<String, Object>> query = dynamicTableDataMapper.querySql(tableName, sql);
        return query == null ? new ArrayList<>() : query;
    }

    /**
     * 校验 SQL 为单条只读查询：以 select/with 开头且不含语句分隔符
     */
    private static void assertReadonlySql(String sql) {
        String normalized = sql.trim();
        // 去除行注释与块注释后再判断，防止用注释绕过前缀校验
        String stripped = normalized.replaceAll("--[^\n]*", " ").replaceAll("/\\*.*?\\*/", " ").trim();
        String lower = stripped.toLowerCase();
        boolean readonly = (lower.startsWith("select") || lower.startsWith("with")) && !stripped.contains(";");
        if (!readonly) {
            throw new IllegalArgumentException("querySql 仅允许执行单条只读查询（SELECT/WITH）：" + normalized);
        }
    }

    /**
     * 根据动态表单数据创建或更新数据库表
     * <p>
     * 先通过 {@link DatabaseDialect#buildTableExistsSql} 检查表是否存在，
     * 不存在则执行建表，已存在则执行改表。
     * </p>
     *
     * @param database  数据源名称
     * @param tableName 目标表名
     * @param data      表单数据（含字段定义、类型、长度等）
     */
    @Override
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void createTable(String database, String tableName, Map<String, Object> data) {

        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);

            int exist = dynamicTableMapper.executeQueryForCount(databaseDialect.buildTableExistsSql(tableName));
            if (exist == 0) {
                log.info("创建动态表：table={}", tableName);
                autoCreateTableService.createTransferTable(tableName, data, true);
            } else {
                log.info("更新动态表结构：table={}", tableName);
                autoCreateTableService.createTransferTable(tableName, data, false);
            }
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
        }
    }

    /**
     * 根据 DynamicForm 实体创建或更新数据库表
     * <p>
     * 先通过 {@link DatabaseDialect#buildTableExistsSql} 检查表是否存在，
     * 不存在则执行建表，已存在则执行改表。
     * </p>
     *
     * @param database    数据源名称
     * @param dynamicForm 动态表单实体
     */
    @DS("#database")
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public void createTableByDynamicForm(String database, DynamicForm dynamicForm) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);

            String tableCode = dynamicForm.getTableCode();
            int exist = dynamicTableMapper.executeQueryForCount(databaseDialect.buildTableExistsSql(tableCode));
            if (exist == 0) {
                log.info("创建动态表（来自表单定义）：table={}", tableCode);
                autoCreateTableService.createDynamicTable(dynamicForm, true);
            } else {
                log.info("更新动态表结构（来自表单定义）：table={}", tableCode);
                autoCreateTableService.createDynamicTable(dynamicForm, false);
            }
            // 子表（sub-table 组件）独立数据库表同步：不存在则创建（主表编码_子表编码），存在则增量追加字段
            dynamicSubTableService.ensureSubTables(database, tableCode, dynamicForm.getMetaData());
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
        }
    }

    /**
     * 删除动态表单的物理表（主表 + 子表），表内业务数据随之删除。
     * 表名经 ${} 拼接 DDL，先过标识符白名单；DROP TABLE IF EXISTS 保证重复删除不报错。
     */
    @DS("#database")
    @Override
    public void dropTableByDynamicForm(String database, DynamicForm dynamicForm) {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnore(true);

            String tableCode = dynamicForm.getTableCode();
            if (StringUtils.isBlank(tableCode)) {
                return;
            }
            // DDL 会隐式提交事务，主表删除成功后再删子表，避免子表失败时主表也回滚不了
            dropTableIfValid(tableCode);
            for (DynamicSubTableService.SubTableDef subTable
                    : dynamicSubTableService.resolveSubTables(tableCode, dynamicForm.getMetaData())) {
                dropTableIfValid(subTable.getChildTable());
            }
        } finally {
            OrganizationContextHolder.setIgnore(oldIgnore);
        }
    }

    /**
     * 按白名单校验表名后执行 DROP TABLE IF EXISTS，非法表名跳过并记日志
     */
    private void dropTableIfValid(String tableName) {
        if (StringUtils.isBlank(tableName) || !IDENTIFIER_PATTERN.matcher(tableName).matches()) {
            log.warn("跳过非法表名的物理表删除：{}", tableName);
            return;
        }
        log.info("删除动态表（表单删除联动）：table={}", tableName);
        dynamicTableMapper.executeDDL(databaseDialect.buildDropTableSql(tableName));
    }
}