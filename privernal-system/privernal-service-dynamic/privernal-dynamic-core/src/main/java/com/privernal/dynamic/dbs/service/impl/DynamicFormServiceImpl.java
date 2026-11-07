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

import com.privernal.common.core.constant.MenuConstants;
import com.privernal.common.core.constant.DataConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.domain.DynamicFormLog;
import com.privernal.dynamic.dbs.mapper.DynamicFormColumnMapper;
import com.privernal.dynamic.dbs.mapper.DynamicFormLogMapper;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import com.privernal.dynamic.dbs.service.DynamicFormRelationService;
import com.privernal.dynamic.dbs.service.DynamicRelationDataService;
import com.privernal.dynamic.dbs.service.DynamicTableCodePrefixService;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.dto.DynamicFormDTO;
import com.privernal.dynamic.dto.DynamicFormDetailDTO;
import com.privernal.dynamic.filter.DynamicFormFilterDTO;
import com.privernal.flow.feign.RemoteFlowService;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.dto.SystemMenuDTO;
import com.privernal.system.feign.RemoteUserService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.List;

/**
 * 前端form配置的Service业务层处理
 *
 * @author saperliu
 * @date 2025-02-22
 * @Description 前端form配置的Service业务层处理
 */
@Slf4j
@Service
public class DynamicFormServiceImpl implements IDynamicFormService {
    @Autowired
    private DynamicFormMapper dynamicFormMapper;

    @Autowired
    DynamicTableService dynamicTableService;

    @Autowired
    RemoteUserService remoteUserService;

    @Autowired
    private DynamicFormLogMapper dynamicFormLogMapper;

    @Autowired
    private DynamicFormColumnMapper dynamicFormColumnMapper;

    @Autowired
    private RemoteFlowService remoteFlowService;

    @Autowired
    private DynamicFormRelationService dynamicFormRelationService;

    @Autowired
    private DynamicTableCodePrefixService dynamicTableCodePrefixService;

    @org.springframework.context.annotation.Lazy
    @Autowired
    private com.privernal.job.api.ScheduleTaskClient scheduleTaskClient;

    /** 高级设置四块配置的级联清理（advanced 包，ObjectProvider 防止与本服务的 bean 环） */
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.privernal.dynamic.advanced.service.AdvancedConfigCleaner> advancedConfigCleanerProvider;

    private void advancedConfigCleanerProxy(String formId) {
        com.privernal.dynamic.advanced.service.AdvancedConfigCleaner cleaner = advancedConfigCleanerProvider.getIfAvailable();
        if (cleaner != null) {
            cleaner.deleteByFormId(formId);
        }
    }

    /**
     * 查询前端form配置
     *
     * @param id 前端form配置主键
     * @return 前端form配置
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicForm selectDynamicFormById(String id) {
        return dynamicFormMapper.selectById(id);
    }

    /**
     * 查询前端form配置
     *
     * @param id 前端form配置主键
     * @return 前端form配置
     */
    @Transactional(readOnly = true)
    @Override
    public DynamicFormDetailDTO selectDynamicFormDetailDTOById(String id) {
        DynamicForm dynamicForm = dynamicFormMapper.selectById(id);
        DynamicFormDetailDTO dynamicFormDetailDTO = new DynamicFormDetailDTO();
        if (dynamicForm != null) {
            BeanUtils.copyProperties(dynamicForm, dynamicFormDetailDTO, DataConstants.IGNORED_ON_COPY);
        }
        return dynamicFormDetailDTO;
    }

    /**
     * 查询前端form配置列表
     *
     * @param dynamicForm 前端form配置
     * @return 前端form配置
     */

    @Transactional(readOnly = true)
    @Override
    public List<DynamicFormDTO> selectDynamicFormList(DynamicFormFilterDTO dynamicForm) {
        return dynamicFormMapper.selectDynamicFormList(dynamicForm);
    }

    /**
     * 新增前端form配置
     *
     * @param dynamicForm 前端form配置
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicForm insertDynamicForm(DynamicForm dynamicForm) {

        // 各组织创建的表统一带上企业组织编码前缀（table_code 就是物理表名），建表、菜单、权限标识都用带前缀的编码
        dynamicForm.setTableCode(dynamicTableCodePrefixService.decorate(dynamicForm.getTableCode()));

        // 表单编码唯一（dynamic_form.code_unque），插入前先给出业务提示，避免数据库唯一键裸异常
        if (StringUtils.isNotBlank(dynamicForm.getTableCode())) {
            LambdaPrivernalWrapper<DynamicForm> codeWrapper = new LambdaPrivernalWrapper<>();
            codeWrapper.eq(DynamicForm::getTableCode, dynamicForm.getTableCode());
            Long count = dynamicFormMapper.selectCount(codeWrapper);
            if (count != null && count > 0) {
                throw new ServiceException("表单编码已存在：" + dynamicForm.getTableCode());
            }
        }

        // 设计器字段编码沿用组件类型（如 color-picker_xxx，含连字符），先规范化为合法列名，
        // 使持久化的 metaData 字段与建表列名、数据读写键保持一致（编辑回显按 metaData 字段取值）
        normalizeFormFieldCodes(dynamicForm.getMetaData());

        dynamicForm.setCreatedBy(SecurityUtils.getUserId());
        dynamicForm.setCreatedTime(new Date());
        dynamicForm.setOrganizationId(SecurityUtils.getOrganizationId());
        dynamicForm.setDepartmentId(SecurityUtils.getDepartmentId());
        dynamicFormMapper.insert(dynamicForm);

        // 登记本表单引用了哪些被关联表单，关联显示数据的读取授权按这张登记表判定
        dynamicFormRelationService.syncFormRelations(dynamicForm);

        // 创建数据库表
        String database = SecurityUtils.getDatabaseName();
        dynamicTableService.createTableByDynamicForm(database, dynamicForm);

        ensureFormMenus(dynamicForm);
        return dynamicForm;
    }

    /**
     * 登记表单菜单与 6 个功能按钮（幂等）：menu_code = builder_{tableCode} 已存在时跳过。
     * 新建走 insertDynamicForm；「新建草稿」的表单在人工发布（applyDefinitionFromLog）时首次登记。
     */
    private void ensureFormMenus(DynamicForm dynamicForm) {
        SystemAppInfoDTO systemAppInfo = remoteUserService.findAppInfoById(dynamicForm.getAppId());

        Assert.notNull(systemAppInfo, "应用不存在");

        String builderMenuCode = "builder_" + dynamicForm.getTableCode();
        if (remoteUserService.findSystemMenuByCode(builderMenuCode) != null) {
            return;
        }

        String appCode = systemAppInfo.getCode().toLowerCase();
        SystemMenuDTO appDirMenu = remoteUserService.findSystemMenuByCode(appCode);
        if (appDirMenu == null) {
            // 应用创建时未生成目录菜单（历史应用），此处兜底创建，保证表单菜单有父级
            appDirMenu = createAppDirMenu(systemAppInfo, appCode);
        }

        //将给此表单添加一个菜单
        SystemMenuDTO systemMenu = new SystemMenuDTO();
        systemMenu.setMenuName(dynamicForm.getTableName());
        systemMenu.setMenuCode(builderMenuCode);
        systemMenu.setMenuType(MenuConstants.TYPE_MENU);
        // 组件透传解析到低代码列表页（view.builder → views/builder/index.vue），路由名必须与 menu_code 一致且唯一
        systemMenu.setComponent("view.builder");
        // 表单编码通过路径段传入列表页（MetaVo 无 query，菜单参数无法带到前端）
        systemMenu.setPath("/builder/" + dynamicForm.getTableCode());
        systemMenu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
        systemMenu.setParentId(appDirMenu.getId());
        systemMenu.setCreatedBy(SecurityUtils.getUserId());
        systemMenu.setCreatedTime(new Date());
        systemMenu.setOrderNum(20L);
        systemMenu.setVisible(1);
        systemMenu.setPermission("dynamic:" + dynamicForm.getTableCode() + ":list");
        systemMenu.setAppMenu(0);
        systemMenu.setAppId(dynamicForm.getAppId());
        systemMenu.setOrganizationId(SecurityUtils.getOrganizationId());
        SystemMenuDTO systemMenuTop =  remoteUserService.insertSystemMenu(systemMenu);

        // 添加6个功能按钮
        saveSystemMenuButton(dynamicForm.getTableName()+"列表", systemMenuTop.getId(), dynamicForm.getTableCode(), "list",6, dynamicForm.getAppId());
        saveSystemMenuButton(dynamicForm.getTableName()+"新增", systemMenuTop.getId(), dynamicForm.getTableCode(), "add",5, dynamicForm.getAppId());
        saveSystemMenuButton(dynamicForm.getTableName()+"修改", systemMenuTop.getId(), dynamicForm.getTableCode(), "edit",4, dynamicForm.getAppId());
        saveSystemMenuButton(dynamicForm.getTableName()+"删除", systemMenuTop.getId(), dynamicForm.getTableCode(), "remove",3, dynamicForm.getAppId());
        saveSystemMenuButton(dynamicForm.getTableName()+"导入", systemMenuTop.getId(), dynamicForm.getTableCode(), "import",2, dynamicForm.getAppId());
        saveSystemMenuButton(dynamicForm.getTableName()+"导出", systemMenuTop.getId(), dynamicForm.getTableCode(), "export",1, dynamicForm.getAppId());
    }

    /**
     * 新建草稿：只落 dynamic_form 行——前缀装饰、编码唯一、appId 前置校验、字段编码规范化，
     * 不建物理表、不登记关联/菜单/权限。发布（applyDefinitionFromLog）时才建表并 ensureFormMenus。
     */
    private DynamicForm insertFormRowWithoutTable(DynamicForm dynamicForm) {
        dynamicForm.setTableCode(dynamicTableCodePrefixService.decorate(dynamicForm.getTableCode()));
        if (StringUtils.isNotBlank(dynamicForm.getTableCode())) {
            LambdaPrivernalWrapper<DynamicForm> codeWrapper = new LambdaPrivernalWrapper<>();
            codeWrapper.eq(DynamicForm::getTableCode, dynamicForm.getTableCode());
            Long count = dynamicFormMapper.selectCount(codeWrapper);
            if (count != null && count > 0) {
                throw new ServiceException("表单编码已存在：" + dynamicForm.getTableCode());
            }
        }
        Assert.notNull(remoteUserService.findAppInfoById(dynamicForm.getAppId()), "应用不存在");
        normalizeFormFieldCodes(dynamicForm.getMetaData());
        dynamicForm.setCreatedBy(SecurityUtils.getUserId());
        dynamicForm.setCreatedTime(new Date());
        dynamicForm.setOrganizationId(SecurityUtils.getOrganizationId());
        dynamicForm.setDepartmentId(SecurityUtils.getDepartmentId());
        if (dynamicForm.getStatus() == null) {
            dynamicForm.setStatus(DataConstants.DATA_STATUS_YES);
        }
        dynamicFormMapper.insert(dynamicForm);
        return dynamicForm;
    }

    /**
     * 应用目录菜单缺失时兜底创建（与应用创建时的逻辑保持一致：menu_code = 应用编码）
     */
    private SystemMenuDTO createAppDirMenu(SystemAppInfoDTO systemAppInfo, String appCode) {
        SystemMenuDTO dirMenu = new SystemMenuDTO();
        dirMenu.setMenuName(systemAppInfo.getAppName());
        dirMenu.setMenuCode(appCode);
        dirMenu.setMenuType(MenuConstants.TYPE_DIR);
        dirMenu.setComponent("basic");
        dirMenu.setPath("/" + appCode);
        dirMenu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
        dirMenu.setParentId("0");
        dirMenu.setCreatedBy(SecurityUtils.getUserId());
        dirMenu.setCreatedTime(new Date());
        dirMenu.setOrderNum(20L);
        dirMenu.setVisible(1);
        dirMenu.setPermission("system:" + appCode + ":list");
        dirMenu.setAppMenu(0);
        dirMenu.setAppId(systemAppInfo.getId());
        dirMenu.setOrganizationId(SecurityUtils.getOrganizationId());
        return remoteUserService.insertSystemMenu(dirMenu);
    }

    private void saveSystemMenuButton(String name, String parentId, String code,String operation, long order, String appId){
        SystemMenuDTO systemMenuButton = new SystemMenuDTO();
        systemMenuButton.setMenuName(name);
        systemMenuButton.setMenuCode("");
        systemMenuButton.setMenuType(MenuConstants.TYPE_BUTTON);
        systemMenuButton.setComponent("");
        systemMenuButton.setPath("");
        systemMenuButton.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
        systemMenuButton.setParentId(parentId);
        systemMenuButton.setCreatedBy(SecurityUtils.getUserId());
        systemMenuButton.setCreatedTime(new Date());
        systemMenuButton.setOrderNum(order);
        systemMenuButton.setVisible(1);
        systemMenuButton.setPermission("dynamic:" + code + ":"+operation);
        systemMenuButton.setOrganizationId(SecurityUtils.getOrganizationId());
        systemMenuButton.setAppId(appId);
        remoteUserService.insertSystemMenu(systemMenuButton);
    }

    /**
     * 修改前端form配置
     *
     * @param dynamicForm 前端form配置
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicForm updateDynamicForm(DynamicForm dynamicForm) {

        // 与新增保持一致：字段编码规范化后再更新 metaData 与表结构
        normalizeFormFieldCodes(dynamicForm.getMetaData());

//        // 构建更新条件，排除 table_code 字段
        LambdaPrivernalWrapper<DynamicForm> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicForm::getId, dynamicForm.getId());
        // 只更新除了 table_code 以外的字段
        DynamicForm updateForm = new DynamicForm();
        if (StringUtils.isNotNull(dynamicForm.getStatus())) {
            updateForm.setStatus(dynamicForm.getStatus());
        }
        if (StringUtils.isNotNull(dynamicForm.getAppId())) {
            updateForm.setAppId(dynamicForm.getAppId());
        }
        if(StringUtils.isNotNull(dynamicForm.getIsWorkflow())){
            updateForm.setIsWorkflow(dynamicForm.getIsWorkflow());
            // 工作流编码随启用状态一并回写（启用/停用工作流时保持一致）
            updateForm.setWorkflowCode(dynamicForm.getWorkflowCode());
        }
        if(StringUtils.isNotNull(dynamicForm.getTableName())){
            updateForm.setTableName(dynamicForm.getTableName());
        }

        updateForm.setMetaData(dynamicForm.getMetaData());
        updateForm.setUpdatedBy(SecurityUtils.getUserId());
        updateForm.setUpdatedTime(new Date());
        // 设置其他需要更新的字段，但不包括 table_code

        dynamicFormMapper.update(updateForm, wrapper);
//
//        dynamicFormMapper.updateById(dynamicForm);

        // 更新成功后写入版本快照（dynamic_form_log），补齐动态表单定义的版本留痕
        insertFormVersionLog(dynamicForm);

        // 关联引用登记：meta_data 变了引用关系就可能变；table_code 不在本接口的更新范围内，按库里的表单登记
        DynamicForm stored = DynamicRelationDataService.withoutDataScope(
                () -> dynamicFormMapper.selectById(dynamicForm.getId()));
        dynamicFormRelationService.syncFormRelations(stored != null ? stored : dynamicForm);

        // 创建数据库表
        String database = SecurityUtils.getDatabaseName();
        dynamicTableService.createTableByDynamicForm(database, dynamicForm);

        return dynamicForm;
    }

    /**
     * 表单定义变更版本快照：version 按该表单已有快照数递增
     */
    private void insertFormVersionLog(DynamicForm dynamicForm) {
        try {
            LambdaPrivernalWrapper<DynamicFormLog> logWrapper = new LambdaPrivernalWrapper<>();
            logWrapper.eq(DynamicFormLog::getFormId, dynamicForm.getId());
            Long count = dynamicFormLogMapper.selectCount(logWrapper);
            DynamicFormLog formLog = new DynamicFormLog();
            formLog.setFormId(dynamicForm.getId());
            formLog.setVersion(String.valueOf((count == null ? 0L : count) + 1));
            // title 列记表单名（设计器口径为 tableName），发布/回滚要能还原表名
            formLog.setTitle(resolveFormName(dynamicForm, dynamicForm));
            formLog.setStatus(dynamicForm.getStatus());
            formLog.setMetaData(dynamicForm.getMetaData());
            formLog.setIsWorkflow(dynamicForm.getIsWorkflow());
            formLog.setWorkflowCode(dynamicForm.getWorkflowCode());
            formLog.setOrganizationId(SecurityUtils.getOrganizationId());
            formLog.setDepartmentId(SecurityUtils.getDepartmentId());
            formLog.setCreatedBy(SecurityUtils.getUserId());
            formLog.setCreatedTime(new Date());
            dynamicFormLogMapper.insert(formLog);
        } catch (Exception e) {
            // 快照写入失败不阻塞表单更新主链路
            log.error("表单版本快照写入失败 formId={}", dynamicForm.getId(), e);
        }
    }

    /**
     * 批量删除前端form配置
     *
     * @param ids 需要删除的前端form配置主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicFormByIds(String[] ids) {
        for (String id : ids) {
            deleteDynamicFormById(id);
        }
        return ids.length;
    }

    /**
     * 删除前端form配置信息
     * <p>
     * 表单删除联动清理新增时创建的全部关联数据（顺序：先 DML 后 DDL，DDL 隐式提交事务必须放最后）：
     * <ol>
     * <li>table_code 改名释放唯一索引，便于同名表单重建</li>
     * <li>dynamic_form 逻辑删除</li>
     * <li>dynamic_form_relation 引用登记（本表单引用的 + 别的表单引用本表单的）</li>
     * <li>dynamic_form_column 列配置、dynamic_form_log 版本快照</li>
     * <li>菜单（builder_{code}）与 6 个按钮，级联清理角色/组织/应用授权与用户收藏</li>
     * <li>system_app_workflow 应用-流程表单绑定</li>
     * <li>开启流程时：流程定义/实例/待办/表单配置（按 workflowCode）</li>
     * <li>物理表（主表 + 子表），表内业务数据随之删除</li>
     * </ol>
     *
     * @param id 前端form配置主键
     * @return 结果
     */

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public int deleteDynamicFormById(String id) {
        DynamicForm form = dynamicFormMapper.selectById(id);
        if (form == null) {
            return 0;
        }
        String tableCode = form.getTableCode();

        // 逻辑删除后行仍占用 table_code 的唯一索引（code_unque），删除前先改名释放编码，便于同名表单重建
        if (StringUtils.isNotBlank(tableCode)) {
            LambdaPrivernalWrapper<DynamicForm> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(DynamicForm::getId, id);
            // 后缀含主键保证唯一，编码部分超长时截断（table_code varchar(100)）
            String suffix = "_deleted_" + id;
            String deletedCode = tableCode.length() + suffix.length() > 100
                    ? tableCode.substring(0, 100 - suffix.length()) + suffix
                    : tableCode + suffix;
            DynamicForm rename = new DynamicForm();
            rename.setTableCode(deletedCode);
            dynamicFormMapper.update(rename, wrapper);
        }
        int rows = dynamicFormMapper.deleteFillById(id);

        // 引用登记两个方向都清：本表单引用别人的（form_id），别的表单引用本表单的（target_table_code，
        // 残留会继续给引用方放行已删表的关联显示数据）
        dynamicFormRelationService.clearFormRelations(id);
        dynamicFormRelationService.clearRelationsByTargetTableCode(tableCode);

        // 列配置与版本快照按 form_id 逻辑删除
        LambdaPrivernalWrapper<DynamicFormColumn> columnWrapper = new LambdaPrivernalWrapper<>();
        columnWrapper.eq(DynamicFormColumn::getFormId, id);
        dynamicFormColumnMapper.delete(columnWrapper);
        LambdaPrivernalWrapper<DynamicFormLog> logWrapper = new LambdaPrivernalWrapper<>();
        logWrapper.eq(DynamicFormLog::getFormId, id);
        dynamicFormLogMapper.delete(logWrapper);

 // 高级设置四块配置随表单删除一并清理（不进版本快照，但随级联删）。
        // 联动规则的调度任务取消在下方 cancelScheduleTasks 统一处理（前缀含 link:/linkdate:）；
        // 外链访问日志/回执码映射与联动执行日志物理删除（append-only 大表，P0 不做归档搬迁）
        advancedConfigCleanerProxy(id);

        // 菜单与按钮级联删除（删除接口内部联动清理角色/组织/应用授权与用户收藏）
        remoteUserService.deleteSystemMenuByCode("builder_" + tableCode);
        // 应用与该流程表单的绑定
        remoteUserService.deleteSystemAppWorkflowByFormId(id);

        // 开启了流程的表单，按 workflowCode 清掉全部版本的流程（实例/待办/已办/表单配置）
        if (Long.valueOf(1L).equals(form.getIsWorkflow()) && StringUtils.isNotBlank(form.getWorkflowCode())) {
            remoteFlowService.removeByFlowCode(form.getWorkflowCode());
        }

 // 定时任务级联取消（①）：必须插在物理 DDL 之前——DDL 隐式提交事务，
        // 放它后面取消失败就脱离事务保护。取消失败只记错误不阻断删表（悬挂任务好过删表失败）
        cancelScheduleTasks(tableCode, form);
        // 联动规则的调度任务（bizKey 前缀 link:{tableCode}: / linkdate:{tableCode}:）已被上方
        // cancelScheduleTasks 的 DESIGNER/DESIGNER_DATA 前缀取消覆盖（high-setting §7.7.4）

        // 物理表（主表 + 子表）最后删：DDL 隐式提交事务，失败时前面的元数据清理已生效，仅剩孤儿表可手工处理
        String database = SecurityUtils.getDatabaseName();
        dynamicTableService.dropTableByDynamicForm(database, form);

        return rows;
    }

    /** 按表单编码前缀取消设计器注册的定时任务（§9.5；显式取 orgId，缺上下文时告警不静默） */
    private void cancelScheduleTasks(String tableCode, DynamicForm form) {
        try {
            String orgId = StringUtils.isNotBlank(form.getOrganizationId())
                    ? form.getOrganizationId() : SecurityUtils.getOrganizationId();
            if (StringUtils.isBlank(orgId)) {
                log.warn("表单删除时取不到组织上下文，定时任务未级联取消 tableCode={}", tableCode);
                return;
            }
            // biz_key 形状与注册侧同源（P0-2/P0-3）：TIMER=link:{tableCode}:{ruleCode}、
            // DATE_FIELD=linkdate:{tableCode}:{ruleCode}:{dataId}；前缀必须带协议段+表名，
            // 否则恒命中 0 行（漏取消）或退化成裸 "linkdate:" 误伤全组织
            scheduleTaskClient.cancelByPrefix(orgId,
                    com.privernal.dynamic.advanced.service.LinkageBizKeys.SOURCE_TIMER,
                    com.privernal.dynamic.advanced.service.LinkageBizKeys.timerByTable(tableCode));
            scheduleTaskClient.cancelByPrefix(orgId,
                    com.privernal.dynamic.advanced.service.LinkageBizKeys.SOURCE_DATE_FIELD,
                    com.privernal.dynamic.advanced.service.LinkageBizKeys.dateExpiryByTable(tableCode));
        } catch (Exception e) {
            log.error("表单删除级联取消定时任务失败 tableCode={}", tableCode, e);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public DynamicForm selectDynamicFormByTableCode(String tableCode) {
        LambdaPrivernalWrapper<DynamicForm> queryWrapper = new LambdaPrivernalWrapper<>();
        queryWrapper.eq(DynamicForm::getTableCode, tableCode);
        return dynamicFormMapper.selectOne(queryWrapper);
    }

    /**
     * 保存表单设计草稿：只写 DRAFT 快照留痕，不改生效定义、不 ALTER 物理表、不动关联登记。
     * 同一表单仅保留最新一条未发布草稿，重复保存覆盖旧草稿。
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicFormLog saveFormDraft(DynamicForm dynamicForm) {
        if (dynamicForm == null) {
            throw new ServiceException("表单内容不能为空");
        }
        DynamicForm form;
        if (StringUtils.isBlank(dynamicForm.getId())) {
            // 新建草稿（AI 产出全部停在草稿，人点发布才建表/建菜单）——
            // 只落 dynamic_form 行，不建物理表、不登记菜单权限；appId 在此前置校验（P19 教训：不能等发布才发现）
            form = insertFormRowWithoutTable(dynamicForm);
        } else {
            form = dynamicFormMapper.selectById(dynamicForm.getId());
        }
        if (form == null) {
            throw new ServiceException("表单不存在或已删除：" + dynamicForm.getId());
        }

        // 草稿内字段编码同样规范化，保证发布时快照 metaData 与建表列名直接对齐
        JSONObject draftMetaData = dynamicForm.getMetaData() != null ? dynamicForm.getMetaData() : form.getMetaData();
        normalizeFormFieldCodes(draftMetaData);

        clearFormDrafts(form.getId());

        // 新建草稿时把规范化后的 metaData 同步回 dynamic_form 行，列表/设计器回显与草稿一致
        if (StringUtils.isBlank(dynamicForm.getId()) && draftMetaData != null) {
            DynamicForm sync = new DynamicForm();
            sync.setId(form.getId());
            sync.setMetaData(draftMetaData);
            dynamicFormMapper.updateById(sync);
        }

        DynamicFormLog draft = new DynamicFormLog();
        draft.setFormId(form.getId());
        // 草稿统一占 v0，正式生效后的版本号仍从现有快照数递增
        draft.setVersion("0");
        draft.setTitle(resolveFormName(dynamicForm, form));
        draft.setStatus(form.getStatus());
        draft.setMetaData(draftMetaData);
        draft.setIsWorkflow(dynamicForm.getIsWorkflow() != null ? dynamicForm.getIsWorkflow() : form.getIsWorkflow());
        draft.setWorkflowCode(StringUtils.isNotBlank(dynamicForm.getWorkflowCode())
                ? dynamicForm.getWorkflowCode() : form.getWorkflowCode());
        draft.setLogType(DynamicFormLog.LOG_TYPE_DRAFT);
        draft.setOrganizationId(SecurityUtils.getOrganizationId());
        draft.setDepartmentId(SecurityUtils.getDepartmentId());
        draft.setCreatedBy(SecurityUtils.getUserId());
        draft.setCreatedTime(new Date());
        dynamicFormLogMapper.insert(draft);
        return draft;
    }

    @Transactional(readOnly = true)
    @Override
    public DynamicFormLog findFormDraft(String formId) {
        if (StringUtils.isBlank(formId)) {
            return null;
        }
        LambdaPrivernalWrapper<DynamicFormLog> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicFormLog::getFormId, formId);
        wrapper.eq(DynamicFormLog::getLogType, DynamicFormLog.LOG_TYPE_DRAFT);
        wrapper.orderByDesc(DynamicFormLog::getCreatedTime);
        List<DynamicFormLog> drafts = dynamicFormLogMapper.selectList(wrapper);
        return drafts.isEmpty() ? null : drafts.get(0);
    }

    /**
     * 发布指定快照为生效定义；草稿发布后即清除。回滚与发布共用 applyDefinitionFromLog 生效链。
     */
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicForm publishFormLog(String logId) {
        DynamicFormLog log = requireFormLog(logId);
        DynamicForm result = applyDefinitionFromLog(log);
        if (DynamicFormLog.LOG_TYPE_DRAFT.equals(log.getLogType())) {
            // 草稿发布即消费掉，历史版本链里已有发布生成的 VERSION 快照
            dynamicFormLogMapper.deleteFillById(log.getId());
        }
        return result;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Override
    public DynamicForm rollbackFormLog(String logId) {
        DynamicFormLog log = requireFormLog(logId);
        if (DynamicFormLog.LOG_TYPE_DRAFT.equals(log.getLogType())) {
            throw new ServiceException("设计草稿不能作为回滚目标，请使用发布操作");
        }
        return applyDefinitionFromLog(log);
    }

    private DynamicFormLog requireFormLog(String logId) {
        if (StringUtils.isBlank(logId)) {
            throw new ServiceException("版本快照主键不能为空");
        }
        DynamicFormLog log = dynamicFormLogMapper.selectById(logId);
        if (log == null) {
            throw new ServiceException("版本快照不存在：" + logId);
        }
        return log;
    }

    /**
     * 把快照内容应用为生效定义：全量覆盖生效字段（快照缺省处沿用现值）、重建关联登记、
     * 同步物理表结构（含子表），并写入新 VERSION 快照——与 updateDynamicForm 的生效链一致，
     * 因此「发布/回滚」本身同样有版本留痕、版本号继续递增。
     */
    private DynamicForm applyDefinitionFromLog(DynamicFormLog log) {
        DynamicForm form = dynamicFormMapper.selectById(log.getFormId());
        if (form == null) {
            throw new ServiceException("表单不存在或已删除：" + log.getFormId());
        }

        JSONObject metaData = log.getMetaData();
        normalizeFormFieldCodes(metaData);

        DynamicForm effective = new DynamicForm();
        effective.setId(form.getId());
        effective.setTableName(StringUtils.isNotBlank(log.getTitle()) ? log.getTitle() : form.getTableName());
        effective.setMetaData(metaData != null ? metaData : form.getMetaData());
        effective.setIsWorkflow(log.getIsWorkflow() != null ? log.getIsWorkflow() : form.getIsWorkflow());
        effective.setWorkflowCode(StringUtils.isNotBlank(log.getWorkflowCode())
                ? log.getWorkflowCode() : form.getWorkflowCode());
        effective.setUpdatedBy(SecurityUtils.getUserId());
        effective.setUpdatedTime(new Date());
        dynamicFormMapper.updateById(effective);

        DynamicForm stored = DynamicRelationDataService.withoutDataScope(() -> dynamicFormMapper.selectById(form.getId()));
        dynamicFormRelationService.syncFormRelations(stored);

        String database = SecurityUtils.getDatabaseName();
        dynamicTableService.createTableByDynamicForm(database, stored);

        // 新建草稿首次发布时补登记菜单五件套（幂等，常规表单已有菜单会跳过）
        ensureFormMenus(stored);

        insertFormVersionLog(stored);
        return stored;
    }

    /**
     * 清除表单全部未发布草稿（逻辑删除），保存草稿前调用，保证草稿唯一
     */
    private void clearFormDrafts(String formId) {
        LambdaPrivernalWrapper<DynamicFormLog> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicFormLog::getFormId, formId);
        wrapper.eq(DynamicFormLog::getLogType, DynamicFormLog.LOG_TYPE_DRAFT);
        dynamicFormLogMapper.delete(wrapper);
    }

    /**
     * 取表单名：设计器提交的是 tableName，历史数据兜底 title
     */
    private String resolveFormName(DynamicForm submitted, DynamicForm stored) {
        if (submitted != null && StringUtils.isNotBlank(submitted.getTableName())) {
            return submitted.getTableName();
        }
        if (submitted != null && StringUtils.isNotBlank(submitted.getTitle())) {
            return submitted.getTitle();
        }
        return StringUtils.isNotBlank(stored.getTableName()) ? stored.getTableName() : stored.getTitle();
    }

    /**
     * 规范化表单元数据中的字段编码：设计器字段编码沿用组件类型（如 color-picker_xxx），
     * 连字符等字符不能作为数据库列名。这里递归把输入型字段的 field（及与之相等的 id）
     * 替换为合法列名（FormTransferTable.toDbColumnName），使 metaData 字段、建表列名、
     * 数据读写键三者保持一致。
     */
    private void normalizeFormFieldCodes(JSONObject metaData) {
        if (metaData == null) {
            return;
        }
        normalizeNode(metaData);
    }

    private void normalizeNode(Object node) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Object field = obj.get("field");
            if (field != null && Boolean.TRUE.equals(obj.getBoolean("input"))) {
                String raw = String.valueOf(field);
                String normalized = FormTransferTable.toDbColumnName(raw);
                if (!raw.equals(normalized)) {
                    obj.put("field", normalized);
                    // 设计器中输入组件的 id 与 field 相同，保持二者一致
                    Object id = obj.get("id");
                    if (id != null && raw.equals(String.valueOf(id))) {
                        obj.put("id", normalized);
                    }
                }
            }
            normalizeFieldRules(obj);
            for (Object value : obj.values()) {
                normalizeNode(value);
            }
        } else if (node instanceof JSONArray) {
            for (Object item : (JSONArray) node) {
                normalizeNode(item);
            }
        }
    }

    /**
     * 规则归一化（2026-10-08 实测修复"课时填了数字仍报必填"）：校验规则缺 type 时
     * async-validator 缺省按 string 校验，数字/数组值直接判失败（设计器自产规则
     * 按 ERuleEditor 的 ruleType 带 type，AI 产出的没有）。按组件类型补齐：
     * number/slider/rate→number，cascader/checkbox→array。
     * required 统一写成字符串 "true"——FormTransferTable.java:330 的 DDL 判空是
     * "true".equals(required)，布尔 true 会让物理列丢 NOT NULL（平台既有契约，skill §3.3）。
     */
    private void normalizeFieldRules(JSONObject fieldNode) {
        Object rulesObj = fieldNode.get("rules");
        if (!(rulesObj instanceof JSONArray)) {
            return;
        }
        String type = String.valueOf(fieldNode.get("type"));
        String valueType = switch (type) {
            case "number", "slider", "rate" -> "number";
            case "cascader", "checkbox" -> "array";
            default -> null;
        };
        JSONArray rules = (JSONArray) rulesObj;
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof JSONObject rule)) {
                continue;
            }
            Object required = rule.get("required");
            boolean truthyRequired = (required instanceof String str && "true".equalsIgnoreCase(str.trim()))
                    || Boolean.TRUE.equals(required);
            if (truthyRequired) {
                rule.put("required", "true");
            }
            if (rule.get("type") == null && valueType != null) {
                rule.put("type", valueType);
            }
        }
    }
}
