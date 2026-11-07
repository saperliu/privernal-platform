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

package com.privernal.flow.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作流动态审批人脚本规则
 * 用于 permissionFlag 形如 script:INITIATOR_DEPT_LEADER 的运行时解析，
 * 结合流程实例上下文（发起人、表单数据）计算具体审批人
 * （对齐旧 privernal-service-wfs WorkflowRuleScriptEnum 的核心子集）
 *
 * @author saperliu
 */
@Getter
@AllArgsConstructor
public enum FlowScriptRuleEnum {

    /** 发起人本人 */
    INITIATOR("INITIATOR", "发起人本人"),

    /** 发起人部门主管 */
    INITIATOR_DEPT_LEADER("INITIATOR_DEPT_LEADER", "发起人部门主管"),

    /** 发起人组织主管 */
    INITIATOR_ORG_LEADER("INITIATOR_ORG_LEADER", "发起人组织主管"),

    /** 发起人部门下指定角色成员（值格式：角色id） */
    INITIATOR_DEPT_ROLE("INITIATOR_DEPT_ROLE", "发起人部门+角色"),

    /** 发起人所属部门全员（设计器「人员部门控件-所属部门」） */
    INITIATOR_DEPT("INITIATOR_DEPT", "发起人所属部门全员"),

    /** 表单数据所属部门主管（表单字段 organizationId/departmentId） */
    DATA_DEPT_LEADER("DATA_DEPT_LEADER", "数据所属部门主管"),

    /** 表单字段值指定审批人（字段值为用户id，多个逗号分隔） */
    FORM_FIELD_USER("FORM_FIELD_USER", "表单字段指定用户"),

    /** 表单字段值指定角色编码成员（字段值为角色编码） */
    FORM_FIELD_ROLE("FORM_FIELD_ROLE", "表单字段指定角色"),

    /** 表单部门组件选中的部门下的全员（字段值为部门id，多个逗号分隔或 JSON 数组） */
    FORM_FIELD_DEPT("FORM_FIELD_DEPT", "表单部门组件成员"),

    /** 数据拥有者（取流程变量 created_by / createdBy，即这条业务数据的创建人） */
    DATA_OWNER("DATA_OWNER", "数据拥有者"),

    /**
     * 部门角色查找：锚点部门内持有指定角色的成员。
     * 值格式：DEPT_ROLE:initiator:{roleId} | DEPT_ROLE:user:{userId}:{roleId} | DEPT_ROLE:dept:{deptId}:{roleId}
     */
    DEPT_ROLE("DEPT_ROLE", "部门角色查找"),

    /**
     * 逐层经理查找：以锚点部门为起点向上数 N 级，只取该级那一个部门的负责人，
     * 不含锚点部门自身，也不累加中间各层级的负责人。
     * 例：锚点部门 A 的上级为 B、上上级为 C，则 LEVEL_MANAGER:{anchor}:1 取 B 的负责人，:2 取 C 的负责人。
     * 实现见 FlowUserResolveServiceImpl#levelManagerIds（祖先链取 system_dept_relation，mapper 已按 level 升序）。
     * 值格式：LEVEL_MANAGER:initiator:2 | LEVEL_MANAGER:user:{userId}:2 | LEVEL_MANAGER:dept:{deptId}:2
     */
    LEVEL_MANAGER("LEVEL_MANAGER", "逐层经理查找");

    private final String code;

    private final String desc;

    public static FlowScriptRuleEnum ofCode(String code) {
        for (FlowScriptRuleEnum rule : values()) {
            if (rule.getCode().equalsIgnoreCase(code)) {
                return rule;
            }
        }
        return null;
    }
}
