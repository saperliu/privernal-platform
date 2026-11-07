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

package com.privernal.dynamic.advanced.engine.handler;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.engine.ActionConfig;
import com.privernal.dynamic.advanced.engine.ActionResult;
import com.privernal.dynamic.advanced.engine.DataWriteAttributes;
import com.privernal.dynamic.advanced.engine.LinkageActionHandler;
import com.privernal.dynamic.advanced.engine.LinkageContext;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

/**
 * DATA_CREATE 数据新增动作（§7.4）：目标表单构造一行，字段映射 常量/事件字段/now。
 * 子表明细逐行复制为 P1 能力，P0 仅主表。
 */
@Slf4j
@Component
public class DataCreateAction implements LinkageActionHandler {

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private LinkageSupport linkageSupport;

    @Override
    public String type() {
        return "DATA_CREATE";
    }

    @Override
    public ActionResult perform(ActionConfig actionConfig, LinkageContext ctx) {
        long begin = System.currentTimeMillis();
        JSONObject config = actionConfig.getConfig();
        DynamicForm target = requireTargetForm(config);
        String database = linkageSupport.normalizeDatabase(ctx.getDatabaseName(), target.getOrganizationId());
        LinkedHashMap<String, Object> data =
                linkageSupport.resolveFieldValues(config.getJSONArray("fields"), ctx);
        if (data.isEmpty()) {
            throw new IllegalArgumentException("DATA_CREATE 动作未配置任何字段映射");
        }
        // 组织列直接取事件携带值：联动线程的组织上下文可能被子调用清掉，
        // 而联动线程忽略了组织拦截器（不会自动回填 organization_id），必须显式落
        if (!data.containsKey("organizationId")
                && com.privernal.common.core.utils.StringUtils.isNotEmpty(ctx.getEvent().getOrganizationId())) {
            data.put("organizationId", ctx.getEvent().getOrganizationId());
        }
        String newId;
        LinkageSupport.assertLinkageIgnoreScope();
        // 代理部门取目标表单归属（动态表 department_id NOT NULL，联动线程无会话部门）
        DataWriteAttributes.Snapshot previous =
                DataWriteAttributes.push(DataWriteAttributes.SOURCE_LINKAGE, ctx.getDepth(), actorId(ctx),
                        StringUtils.isEmpty(target.getDepartmentId()) ? null : target.getDepartmentId(),
                        ctx.getEvent().getOrganizationId());
        try {
            newId = dynamicTableService.saveOrUpdate(database, target.getTableCode(), data);
        } finally {
            DataWriteAttributes.restore(previous);
        }
        return ActionResult.success(type(), "新增 1 行（目标表 " + target.getTableCode() + " id=" + newId + "）",
                System.currentTimeMillis() - begin);
    }

    @Override
    public JSONObject dryRun(ActionConfig actionConfig, LinkageContext ctx) {
        JSONObject config = actionConfig.getConfig();
        DynamicForm target = requireTargetForm(config);
        JSONObject preview = new JSONObject();
        preview.put("targetTable", target.getTableCode());
        preview.put("willCreateFields", linkageSupport.resolveFieldValues(config.getJSONArray("fields"), ctx));
        return preview;
    }

    private DynamicForm requireTargetForm(JSONObject config) {
        DynamicForm target = linkageSupport.loadForm(config.getString("targetFormId"));
        if (target == null || StringUtils.isEmpty(target.getTableCode())) {
            throw new IllegalArgumentException("DATA_CREATE 目标表单不存在或未配置 targetFormId");
        }
        return target;
    }

    /** 联动写入的代理身份：动态表 created_by NOT NULL，落事件操作人/规则创建人/系统标记（§7.5.3） */
    private String actorId(LinkageContext ctx) {
        if (com.privernal.common.core.utils.StringUtils.isNotEmpty(ctx.getEvent().getOperatorId())) {
            return ctx.getEvent().getOperatorId();
        }
        if (com.privernal.common.core.utils.StringUtils.isNotEmpty(ctx.getRule().getCreatedBy())) {
            return ctx.getRule().getCreatedBy();
        }
        return "system-linkage";
    }
}
