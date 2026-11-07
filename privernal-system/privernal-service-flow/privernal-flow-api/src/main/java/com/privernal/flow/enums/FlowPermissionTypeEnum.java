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
 * 工作流办理人权限标识类型
 * 设计器节点 permissionFlag 格式：类型:值，多个用 @@ 分隔
 * 如：user:188666666666@@role:12263522227777777@@deptLeader:1001
 *
 * @author saperliu
 */
@Getter
@AllArgsConstructor
public enum FlowPermissionTypeEnum {

    /** 指定用户 */
    USER("user", "用户"),

    /** 角色成员（值为角色id） */
    ROLE("role", "角色"),

    /** 部门成员（值为部门id） */
    DEPT("dept", "部门"),

    /** 部门主管（值为部门id，取 system_department.leader_id） */
    DEPT_LEADER("deptLeader", "部门主管"),

    /** 组织主管（值为组织id，取 system_organization.leader_id） */
    ORG_LEADER("orgLeader", "组织主管"),

    /** 用户组成员（值为用户组id） */
    USER_GROUP("userGroup", "用户组"),

    /** 动态脚本规则（值为 FlowScriptRuleEnum 的 code） */
    SCRIPT("script", "脚本规则");

    private final String code;

    private final String desc;

    /**
     * 按编码解析枚举
     */
    public static FlowPermissionTypeEnum ofCode(String code) {
        for (FlowPermissionTypeEnum type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
