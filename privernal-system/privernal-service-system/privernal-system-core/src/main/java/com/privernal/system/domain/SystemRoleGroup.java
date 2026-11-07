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

package com.privernal.system.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 角色组对象 system_role_group
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 角色组的实体
 */
@Data
public class SystemRoleGroup extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 组名
     */
    private String groupName;
    /**
     * 组织ID;默认的组织
     */
    private String organizationId;
    /**
     * 组的类型;SYSTEM 系统默认 CUSTOM 用户创建
     */
    private String groupType;
    /**
     * 部门;所属部门
     */
    private String departmentId;

}
