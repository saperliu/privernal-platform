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

import java.util.List;

/**
 * DATA_DELETE 数据删除动作（§7.4，P1）：按动作自带条件定位目标行（上限 1000，超限拒绝）后
 * 逐行软删。保存校验强制条件非空（禁配无条件删除），配置页强制「预览影响行数」——
 * dry-run 与真实执行共用同一条定位查询（§7.5.5）。删除走服务层收敛点：
 * 携带被删行快照的 DELETE 事件照常发布，sourceTag=LINKAGE + depth 递归防护照常生效。
 */
@Slf4j
@Component
public class DataDeleteAction implements LinkageActionHandler {

    @Autowired
    private DynamicTableService dynamicTableService;

    @Autowired
    private LinkageSupport linkageSupport;

    @Override
    public String type() {
        return "DATA_DELETE";
    }

    @Override
    public ActionResult perform(ActionConfig actionConfig, LinkageContext ctx) {
        long begin = System.currentTimeMillis();
        JSONObject config = actionConfig.getConfig();
        DynamicForm target = requireTargetForm(config);
        String database = linkageSupport.normalizeDatabase(ctx.getDatabaseName(), target.getOrganizationId());
        List<String> targetIds = locate(config, database, target, ctx);
        int deleted = 0;
        for (String targetId : targetIds) {
            LinkageSupport.assertLinkageIgnoreScope();
            DataWriteAttributes.Snapshot previous =
                    DataWriteAttributes.push(DataWriteAttributes.SOURCE_LINKAGE, ctx.getDepth(), actorId(ctx), null,
                            ctx.getEvent().getOrganizationId());
            try {
                dynamicTableService.deleteById(database, target.getTableCode(), targetId);
                deleted++;
            } finally {
                DataWriteAttributes.restore(previous);
            }
        }
        return ActionResult.success(type(), "删除 " + deleted + " 行（目标表 " + target.getTableCode() + "）",
                System.currentTimeMillis() - begin);
    }

    @Override
    public JSONObject dryRun(ActionConfig actionConfig, LinkageContext ctx) {
        JSONObject config = actionConfig.getConfig();
        DynamicForm target = requireTargetForm(config);
        String database = StringUtils.isEmpty(ctx.getDatabaseName())
                ? linkageSupport.resolveDatabase(target.getOrganizationId()) : ctx.getDatabaseName();
        List<String> targetIds = locate(config, database, target, ctx);
        JSONObject preview = new JSONObject();
        preview.put("targetTable", target.getTableCode());
        preview.put("matchedCount", targetIds.size());
        preview.put("matchedIds", targetIds);
        return preview;
    }

    private List<String> locate(JSONObject config, String database, DynamicForm target, LinkageContext ctx) {
        JSONObject conditionTree = config.getJSONObject("conditionTree");
        if (conditionTree == null || conditionTree.isEmpty()) {
            // 保存校验已拦截，这里兜底（禁配无条件删除，§7.4）
            throw new IllegalArgumentException("DATA_DELETE 缺少删除条件（§7.4）");
        }
        // 目标定位条件支持 {{事件字段}} 引用（先解析再定位，避免 {{...}} 被当字面值静默命中 0 行）
        conditionTree = linkageSupport.resolveConditionRefs(conditionTree, ctx);
        linkageSupport.assertColumnsExist(database, target.getTableCode(), conditionTree);
        return linkageSupport.locateTargetIds(database, target.getTableCode(), conditionTree);
    }

    private DynamicForm requireTargetForm(JSONObject config) {
        DynamicForm target = linkageSupport.loadForm(config.getString("targetFormId"));
        if (target == null || StringUtils.isEmpty(target.getTableCode())) {
            throw new IllegalArgumentException("DATA_DELETE 目标表单不存在或未配置 targetFormId");
        }
        return target;
    }

    private String actorId(LinkageContext ctx) {
        if (StringUtils.isNotEmpty(ctx.getEvent().getOperatorId())) {
            return ctx.getEvent().getOperatorId();
        }
        if (StringUtils.isNotEmpty(ctx.getRule().getCreatedBy())) {
            return ctx.getRule().getCreatedBy();
        }
        return "system-linkage";
    }
}
