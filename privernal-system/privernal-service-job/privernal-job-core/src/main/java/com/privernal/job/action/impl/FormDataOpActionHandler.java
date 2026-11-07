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

package com.privernal.job.action.impl;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.dbs.service.DynamicTableService;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 表单数据操作动作（FORM_DATA_OP）。
 *
 * <p>复用 DynamicTableService（库名作为参数显式传入，@DS("#database") 取方法入参，
 * 不存在数据源上下文可设，§8.3 第3项）。以 biz_context.dataId 定位；
 * 数据已删则静默成功并记 SKIPPED 摘要。表名必须是物理名（含组织前缀，§8.3 第4项）。</p>
 *
 * <p>config: { operation: "DELETE"|"NOOP", tableName(物理名), dataId }</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormDataOpActionHandler implements ScheduleActionHandler {

    private final DynamicTableService dynamicTableService;

    @Override
    public String actionType() {
        return "FORM_DATA_OP";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        Map<String, Object> config = context.getConfig();
        String tableName = str(config.get("tableName"));
        String dataId = str(config.get("dataId"));
        String operation = str(config.get("operation"), "DELETE");
        if (StringUtils.isEmpty(tableName) || StringUtils.isEmpty(dataId)) {
            return ActionResult.fail("tableName/dataId 缺失");
        }
        if (context.getDatabase() == null) {
            return ActionResult.fail("业务库名解析失败（组织未登记业务库且任务无快照）");
        }
        if ("NOOP".equalsIgnoreCase(operation)) {
            return ActionResult.ok("SKIPPED: operation=NOOP");
        }
        if (!"DELETE".equalsIgnoreCase(operation)) {
            return ActionResult.fail("不支持的 operation: " + operation);
        }
        try {
            dynamicTableService.deleteById(context.getDatabase(), tableName, dataId);
            return ActionResult.ok("deleted " + tableName + "/" + dataId);
        } catch (Exception e) {
            log.error("[schedule] form data op failed taskId={} table={}", task.getId(), tableName, e);
            return ActionResult.fail(e.getMessage());
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String str(Object value, String fallback) {
        String s = str(value);
        return StringUtils.isEmpty(s) ? fallback : s;
    }
}
