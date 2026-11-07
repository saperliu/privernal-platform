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

package com.privernal.common.datascope.aspect;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;

import java.util.Arrays;
import java.util.List;

public class OrganizationHandlerImpl implements OrganizationHandler {

    //不配置多租户功能的表；这里需要注意表名不要写错，是否需要``根据自己所写的sql语句来定
    // todo SAAS模式下，前端登录，system_social_app_config 无法区分是哪个企业，因为还未登录。
    private static final List<String> IGNORE_TABLES = Arrays.asList(
            "system_organization",
            "system_menu",
            "system_dept_relation",
            "system_role_menu",
            "system_role_user",
            "system_role_user_dept",
            "system_role_group_role",
            "system_user_social_user",
            "system_user_group_user",
            "system_user_position",
            "system_message_user",
            // warm-flow 工作流引擎自带表（无 organization_id 列，组织隔离由业务扩展表承载）
            "flow_definition",
            "flow_node",
            "flow_skip",
            "flow_instance",
            "flow_task",
            "flow_his_task",
            "flow_user",
            "flow_form",
            // 开放与集成的流水表：由机器调用（无 sa-token 登录态）与 Webhook 异步线程写入，
            // organization_id 由代码从凭证/订阅显式带入。交给拦截器会在无上下文时写成 NULL，
            // 并把异步线程的 UPDATE 过滤成 0 行（事件状态永远停在 PENDING）。
            "system_openapi_call_log",
            "system_webhook_event",
            "system_webhook_delivery_log",
 // 定时任务模块：调度/执行线程无登录态，organization_id
            // 由代码显式带入；行级范围对内核扫描/CAS 无意义（管理查询走 @PrivernalDataScope
            // 的 Mapper 注解路径，不受此表级豁免影响）。不进本清单时无会话 INSERT 的
            // organization_id 会被写成 NULL（NOT NULL 列直接失败）。
            "system_schedule_task",
            "system_schedule_task_log",
            "system_schedule_log_archive",
            "system_schedule_instance",
            "system_schedule_lock",
            "system_schedule_task_queue",
            "system_schedule_metric_snapshot"
            );

    @Override
    public Expression getOrganizationId() {
        if (StringUtils.isNotEmpty(OrganizationContextHolder.getOrganizationId())) {
            return new StringValue(OrganizationContextHolder.getOrganizationId());
        } else {
            String orgId = SecurityUtils.getOrganizationId();
            if (StringUtils.isEmpty(orgId)) {
                return new NullValue();
            }
            return new StringValue(orgId);
        }
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return DataScopeHelper.getIgnoreOrganization() || IGNORE_TABLES.stream().anyMatch(ignoreTableName -> ignoreTableName.equalsIgnoreCase(tableName));
    }
}
