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

package com.privernal.flow.context;

import java.util.Map;

/**
 * 流程操作上下文（ThreadLocal）
 * 引擎在创建任务调用 PermissionHandler#convertPermissions 时无法携带实例信息，
 * 由服务入口在调用引擎前写入发起人/组织/流程变量，供脚本规则解析使用
 *
 * @author saperliu
 */
public class FlowContextHolder {

    private static final ThreadLocal<FlowContext> HOLDER = new ThreadLocal<>();

    public static void set(FlowContext context) {
        HOLDER.set(context);
    }

    public static FlowContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 流程上下文
     */
    public static class FlowContext {

        /** 发起人用户id */
        private String initiatorId;

        /** 发起人姓名 */
        private String initiatorName;

        /** 发起人组织id（兜底查找组织主管用） */
        private String organizationId;

        /** 发起人主部门id（网关条件 __initiator_dept 用） */
        private String departmentId;

        /** 流程变量（表单数据） */
        private Map<String, Object> variables;

        public String getInitiatorId() {
            return initiatorId;
        }

        public void setInitiatorId(String initiatorId) {
            this.initiatorId = initiatorId;
        }

        public String getInitiatorName() {
            return initiatorName;
        }

        public void setInitiatorName(String initiatorName) {
            this.initiatorName = initiatorName;
        }

        public String getOrganizationId() {
            return organizationId;
        }

        public void setOrganizationId(String organizationId) {
            this.organizationId = organizationId;
        }

        public String getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(String departmentId) {
            this.departmentId = departmentId;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }

        public void setVariables(Map<String, Object> variables) {
            this.variables = variables;
        }
    }
}
