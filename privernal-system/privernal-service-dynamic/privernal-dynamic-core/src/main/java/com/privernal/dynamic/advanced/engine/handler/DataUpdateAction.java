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
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * DATA_UPDATE 数据更新动作（§7.4）：按条件定位目标行（上限 1000，超限拒绝），
 * 逐行 SET 字段 = 常量 / 事件字段 / now。写入走服务层收敛点并压入
 * sourceTag=LINKAGE、depth=事件深度+1（递归防护，§7.5.3）。
 */
@Slf4j
@Component
public class DataUpdateAction implements LinkageActionHandler {

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private LinkageSupport linkageSupport;

    @Override
    public String type() {
        return "DATA_UPDATE";
    }

    @Override
    public ActionResult perform(ActionConfig actionConfig, LinkageContext ctx) {
        long begin = System.currentTimeMillis();
        JSONObject config = actionConfig.getConfig();
        DynamicForm target = requireTargetForm(config);
        String database = resolveTargetDatabase(ctx, target);
        JSONObject conditionTree = config.getJSONObject("conditionTree");
        // 目标定位条件支持 {{事件字段}} 引用（先解析再定位，避免 {{...}} 被当字面值静默命中 0 行）
        conditionTree = linkageSupport.resolveConditionRefs(conditionTree, ctx);
        linkageSupport.assertColumnsExist(database, target.getTableCode(), conditionTree);
        List<String> targetIds = linkageSupport.locateTargetIds(database, target.getTableCode(), conditionTree);
        JSONArray setMappings = config.getJSONArray("set");
        LinkedHashMap<String, Object> setValues = linkageSupport.resolveFieldValues(setMappings, ctx);
        if (setValues.isEmpty()) {
            throw new IllegalArgumentException("DATA_UPDATE 动作未配置任何 SET 字段");
        }
        // SET 字段与条件字段同口径校验列存在性：选错目标字段原来会静默写空列/SQL 报错
        linkageSupport.assertFieldsExist(database, target.getTableCode(), setValues.keySet(), "SET 字段");
        int updated = 0;
        for (String targetId : targetIds) {
            LinkedHashMap<String, Object> data = new LinkedHashMap<>(setValues);
            data.put("id", targetId);
            LinkageSupport.assertLinkageIgnoreScope();
            DataWriteAttributes.Snapshot previous =
                    DataWriteAttributes.push(DataWriteAttributes.SOURCE_LINKAGE, ctx.getDepth(), actorId(ctx), null,
                    ctx.getEvent().getOrganizationId());
            try {
                dynamicTableService.saveOrUpdate(database, target.getTableCode(), data);
                updated++;
            } finally {
                DataWriteAttributes.restore(previous);
            }
        }
        return ActionResult.success(type(), "更新 " + updated + " 行（目标表 " + target.getTableCode() + "）",
                System.currentTimeMillis() - begin);
    }

    @Override
    public JSONObject dryRun(ActionConfig actionConfig, LinkageContext ctx) {
        JSONObject config = actionConfig.getConfig();
        DynamicForm target = requireTargetForm(config);
        String database = resolveTargetDatabase(ctx, target);
        List<String> targetIds = linkageSupport.locateTargetIds(database, target.getTableCode(),
                linkageSupport.resolveConditionRefs(config.getJSONObject("conditionTree"), ctx));
        JSONObject preview = new JSONObject();
        preview.put("targetTable", target.getTableCode());
        preview.put("matchedCount", targetIds.size());
        preview.put("matchedIds", targetIds);
        preview.put("setValues", linkageSupport.resolveFieldValues(config.getJSONArray("set"), ctx));
        return preview;
    }

    private DynamicForm requireTargetForm(JSONObject config) {
        DynamicForm target = linkageSupport.loadForm(config.getString("targetFormId"));
        if (target == null || StringUtils.isEmpty(target.getTableCode())) {
            throw new IllegalArgumentException("DATA_UPDATE 目标表单不存在或未配置 targetFormId");
        }
        return target;
    }

    private String resolveTargetDatabase(LinkageContext ctx, DynamicForm target) {
        // P0 目标表单与触发表单同组织（跨组织动作为 P1 关联表单场景），库取事件库（未注册回落）
        return linkageSupport.normalizeDatabase(ctx.getDatabaseName(), target.getOrganizationId());
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
