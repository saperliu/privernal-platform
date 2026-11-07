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

package com.privernal.dynamic.script.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.script.domain.AiArtifactLedger;
import com.privernal.dynamic.script.mapper.AiArtifactLedgerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 生成物台账服务：record 在生成链路落一行，rollback 按对象类型执行撤销语义。
 * 撤销语义：
 * - FORM：先改名释放 table_code 唯一索引（加 _rb_{尾时刻} 后缀）再走既有删除链（逻辑删，物理表不 DROP——
 *   半成品表的手工清理决策留给用户，符合 §3.8「你不能删表」纪律）；
 * - REPORT：按 code 走报表删除服务（deleteByCode）；
 * - DASHBOARD/FLOW/APP：只登记撤销意图并提示人工处理（对应删除动作有在办数据/关联面，不自动化）。
 */
@Slf4j
@Service
public class AiArtifactLedgerService {

    @Autowired
    private AiArtifactLedgerMapper ledgerMapper;

    @Autowired
    private IDynamicFormService dynamicFormService;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /** 生成链路登记一行台账（幂等：同对象未撤销的重复登记跳过） */
    @Transactional(rollbackFor = Exception.class)
    public AiArtifactLedger record(String sessionId, String objectType, String objectId, String code) {
        Long dup = ledgerMapper.selectCount(Wrappers.<AiArtifactLedger>lambdaQuery()
                .eq(AiArtifactLedger::getObjectType, objectType)
                .eq(AiArtifactLedger::getObjectId, objectId)
                .eq(AiArtifactLedger::getRolledBack, "N"));
        if (dup != null && dup > 0) {
            return null;
        }
        AiArtifactLedger row = new AiArtifactLedger();
        row.setSessionId(StringUtils.isEmpty(sessionId) ? resolveSessionHeader() : sessionId);
        row.setObjectType(objectType);
        row.setObjectId(objectId);
        row.setCode(code);
        row.setRolledBack("N");
        row.setOrganizationId(currentOrgId());
        ledgerMapper.insert(row);
        return row;
    }

    /** 组织 id：登录会话优先，holder 兜底（管理请求线程上 holder 可能为空，实测踩坑） */
    private String currentOrgId() {
        String orgId = com.privernal.common.security.service.SecurityUtils.getOrganizationId();
        if (orgId == null || orgId.isEmpty()) {
            orgId = OrganizationContextHolder.getOrganizationId();
        }
        return orgId;
    }

    /**
     * AI 会话归属：控制器线程上从当前请求读 X-AI-Session 头，
     * 使「这一步撤销掉」能按会话过滤台账；非 HTTP 线程/无头回落 manual。
     */
    private String resolveSessionHeader() {
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
                String header = servletAttrs.getRequest().getHeader("X-AI-Session");
                if (header != null && !header.isBlank()) {
                    return header;
                }
            }
        } catch (Exception ignore) {
            // 非 Web 线程（调度/监听路径）无请求上下文
        }
        return "manual";
    }

    public List<AiArtifactLedger> list(String sessionId, String objectType, String rolledBack) {
        return ledgerMapper.selectList(Wrappers.<AiArtifactLedger>lambdaQuery()
                .eq(StringUtils.isNotEmpty(sessionId), AiArtifactLedger::getSessionId, sessionId)
                .eq(StringUtils.isNotEmpty(objectType), AiArtifactLedger::getObjectType, objectType)
                .eq(StringUtils.isNotEmpty(rolledBack), AiArtifactLedger::getRolledBack, rolledBack)
                .orderByDesc(AiArtifactLedger::getCreatedTime));
    }

    /** 撤销：按对象类型分派（语义见类注释）。已撤销的再撤抛错。 */
    @Transactional(rollbackFor = Exception.class)
    public String rollback(String id) {
        AiArtifactLedger row = ledgerMapper.selectById(id);
        if (row == null) {
            throw new ServiceException("台账记录不存在：" + id);
        }
        if ("Y".equals(row.getRolledBack())) {
            throw new ServiceException("该生成物已撤销");
        }
        String note;
        switch (row.getObjectType()) {
            case AiArtifactLedger.TYPE_FORM -> note = rollbackForm(row);
            case AiArtifactLedger.TYPE_REPORT -> note = rollbackReport(row);
            default -> note = "该类型（" + row.getObjectType()
                    + "）存在在办数据或关联面，撤销需人工处理（本台账只登记意图）";
        }
        AiArtifactLedger update = new AiArtifactLedger();
        update.setId(id);
        update.setRolledBack("Y");
        update.setRollbackNote(note);
        ledgerMapper.updateById(update);
        return note;
    }

    /** 表单撤销：改名释放唯一索引 → 既有删除链（逻辑删，物理表保留交人工清理） */
    private String rollbackForm(AiArtifactLedger row) {
        DynamicForm form = dynamicFormService.selectDynamicFormById(row.getObjectId());
        if (form == null) {
            return "表单已不存在（幂等视为撤销完成）";
        }
        String renamed = form.getTableCode() + "_rb_" + System.currentTimeMillis() % 100000;
        DynamicForm rename = new DynamicForm();
        rename.setId(form.getId());
        rename.setTableCode(renamed);
        // updateDynamicForm 会走建表链做 DDL 对比（AutoCreateTableServiceImpl），必须携带原 metaData
        rename.setMetaData(form.getMetaData());
        dynamicFormService.updateDynamicForm(rename);
        dynamicFormService.deleteDynamicFormByIds(new String[]{form.getId()});
        return "表单已改名（" + form.getTableCode() + " → " + renamed + "）并逻辑删除；物理表 "
                + renamed + " 未 DROP，是否删除由用户决定";
    }

    private String rollbackReport(AiArtifactLedger row) {
        var service = applicationContext.getBean(
                com.privernal.dynamic.report.service.IDynamicReportService.class);
        service.deleteByCode(row.getObjectId());
        return "图表 " + row.getCode() + " 已删除，数据集未动（引用它的仪表盘请人工核对）";
    }
}
