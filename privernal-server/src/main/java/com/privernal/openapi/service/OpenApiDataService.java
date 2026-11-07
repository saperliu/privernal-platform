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

package com.privernal.openapi.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dba.utils.FormTransferTable;
import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.dynamic.dbs.service.IDynamicDatabaseService;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.dto.DynamicFormColumnDTO;
import com.privernal.flow.dto.FlowInstanceStatusDTO;
import com.privernal.flow.dto.FlowStartDTO;
import com.privernal.flow.service.IFlowInstanceService;
import com.privernal.openapi.support.OpenApiContext;
import com.privernal.system.domain.SystemOpenapiCredential;
import com.privernal.system.service.OpenApiCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 的能力面：读动态数据、写动态数据、发起流程。审批动作不开放（口径）。
 * <p>
 * 身份模型是「凭证的组织 + 凭证绑定的用户」：组织写进 OrganizationContextHolder 让行隔离照常生效，
 * 部门级数据范围对机器调用无意义所以忽略；绑定用户只作数据归属（created_by 等），不产生登录态。
 */
@Slf4j
@Service
public class OpenApiDataService {

    private static final String DEFAULT_DATABASE = "master";
    private static final String FIELD_ID = "id";

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private IDynamicDatabaseService dynamicDatabaseService;

    @Autowired
    private IDynamicFormService dynamicFormService;

    @Autowired
    private IDynamicFormColumnService dynamicFormColumnService;

    @Autowired
    private IFlowInstanceService flowInstanceService;

    /**
     * 分页读取：过滤条件的取值形态（区间/多选/模糊）与页面列表共用同一套列类型转换
     */
    public PageResultInfo listData(String tableCode, Map<String, Object> query) {
        SystemOpenapiCredential credential = currentCredential();
        requireGrant(credential, tableCode, OpenApiCredentialService.ACTION_LIST);
        String database = resolveDatabase(credential);
        List<DynamicFormColumnDTO> columns = dynamicFormColumnService.selectTableColumnByCode(tableCode);
        FormTransferTable.dataGetSQL(query, columns);
        query.remove("keywords");
        return dynamicTableService.findByPage(database, tableCode, query);
    }

    public Map<String, Object> readData(String tableCode, String id) {
        SystemOpenapiCredential credential = currentCredential();
        requireGrant(credential, tableCode, OpenApiCredentialService.ACTION_READ);
        requireTable(tableCode);
        Map<String, Object> row = dynamicTableService.findById(resolveDatabase(credential), tableCode, id);
        if (row == null || row.isEmpty()) {
            throw new ServiceException("数据不存在：" + id);
        }
        return row;
    }

    public String addData(String tableCode, Map<String, Object> data) {
        SystemOpenapiCredential credential = currentCredential();
        requireGrant(credential, tableCode, OpenApiCredentialService.ACTION_ADD);
        requireTable(tableCode);
        applyOwnership(data, credential);
        String id = dynamicTableService.saveOrUpdate(resolveDatabase(credential), tableCode, data);
        OpenApiContext.markBizId(id);
        return id;
    }

    public String editData(String tableCode, Map<String, Object> data) {
        SystemOpenapiCredential credential = currentCredential();
        requireGrant(credential, tableCode, OpenApiCredentialService.ACTION_EDIT);
        requireTable(tableCode);
        String id = stringValue(data, FIELD_ID);
        if (StringUtils.isEmpty(id)) {
            throw new ServiceException("编辑必须携带主键 id");
        }
        data.put("updatedBy", credential.getBoundUserId());
        id = dynamicTableService.saveOrUpdate(resolveDatabase(credential), tableCode, data);
        OpenApiContext.markBizId(id);
        return id;
    }

    public void removeData(String tableCode, String id) {
        SystemOpenapiCredential credential = currentCredential();
        requireGrant(credential, tableCode, OpenApiCredentialService.ACTION_REMOVE);
        requireTable(tableCode);
        dynamicTableService.deleteById(resolveDatabase(credential), tableCode, id);
        OpenApiContext.markBizId(id);
    }

    /**
     * 列定义：外部系统据此构造数据，避免把不存在的列名带进写接口
     */
    public List<DynamicFormColumnDTO> columns(String tableCode) {
        currentCredential();
        requireTable(tableCode);
        return dynamicFormColumnService.selectTableColumnByCode(tableCode);
    }

    /**
     * 发起流程：tableCode 在请求体里，所以表级授权在这一步判（过滤器判不到）
     */
    @SuppressWarnings("unchecked")
    public FlowInstanceStatusDTO startFlow(Map<String, Object> body) {
        SystemOpenapiCredential credential = currentCredential();
        String tableCode = stringValue(body, "tableCode");
        String businessId = stringValue(body, FIELD_ID);
        if (StringUtils.isEmpty(businessId)) {
            businessId = stringValue(body, "businessId");
        }
        if (StringUtils.isEmpty(tableCode)) {
            throw new ServiceException("tableCode 不能为空");
        }
        if (StringUtils.isEmpty(businessId)) {
            throw new ServiceException("businessId 不能为空");
        }
        requireGrant(credential, tableCode, OpenApiCredentialService.ACTION_START);
        OpenApiContext.Call call = OpenApiContext.get();
        if (call != null) {
            call.tableCode = tableCode;
            call.bizId = businessId;
        }
        if (StringUtils.isEmpty(credential.getBoundUserId())) {
            throw new ServiceException("凭证未绑定用户，无法作为流程发起人");
        }
        FlowStartDTO startDTO = new FlowStartDTO();
        startDTO.setTableCode(tableCode);
        startDTO.setBusinessId(businessId);
        startDTO.setHandler(credential.getBoundUserId());
        Object variables = body.get("variables");
        startDTO.setVariables(variables instanceof Map ? (Map<String, Object>) variables : new HashMap<>());
        return flowInstanceService.start(startDTO);
    }

    private SystemOpenapiCredential currentCredential() {
        SystemOpenapiCredential credential = OpenApiContext.credential();
        if (credential == null) {
            throw new ServiceException("缺少调用凭证");
        }
        return credential;
    }

    /**
     * 业务库按组织解析（与登录链路同一个入口），未登记业务库的组织回落到主数据源
     */
    private String resolveDatabase(SystemOpenapiCredential credential) {
        DynamicDatabaseDTO config = dynamicDatabaseService.selectDynamicDatabase(credential.getOrganizationId());
        String database = config == null ? null : config.getDatabaseName();
        return StringUtils.isEmpty(database) ? DEFAULT_DATABASE : database;
    }

    private void requireTable(String tableCode) {
        DynamicForm form = dynamicFormService.selectDynamicFormByTableCode(tableCode);
        if (form == null) {
            throw new ServiceException("表单不存在：" + tableCode);
        }
    }

    private void requireGrant(SystemOpenapiCredential credential, String tableCode, String action) {
        if (StringUtils.isEmpty(tableCode)
                || !OpenApiCredentialService.grantAllows(credential, tableCode, action)) {
            String message = "该表未授权此动作：" + tableCode + " " + action;
            OpenApiContext.markFailed(message);
            throw new ServiceException(message);
        }
    }

    /**
     * 动态表的 created_by/department_id 是 NOT NULL，而机器调用没有会话可取；
     * 用凭证绑定的用户/部门补上，调用方显式传值时尊重传值
     */
    private void applyOwnership(Map<String, Object> data, SystemOpenapiCredential credential) {
        if (StringUtils.isEmpty(credential.getBoundUserId())) {
            throw new ServiceException("凭证未绑定用户，无法写入数据");
        }
        if (StringUtils.isEmpty(stringValue(data, "createdBy"))) {
            data.put("createdBy", credential.getBoundUserId());
        }
        if (StringUtils.isEmpty(stringValue(data, "departmentId"))) {
            data.put("departmentId", credential.getBoundDepartmentId());
        }
    }

    private String stringValue(Map<String, Object> data, String field) {
        if (data == null) {
            return null;
        }
        Object value = data.get(field);
        return value == null || StringUtils.isEmpty(value.toString()) ? null : value.toString();
    }
}
