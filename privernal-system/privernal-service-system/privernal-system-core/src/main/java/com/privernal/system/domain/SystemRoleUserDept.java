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
 * 角色用户与部门关系对象 system_role_user_dept
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色用户与部门关系的实体
 */
@Data
public class SystemRoleUserDept extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 角色用户表的ID;角色用户表的ID
     */
    private String roleUserId;
    /**
     * 组织ID;所属组织
     */
    private String organizationId;
    /**
     * 部门ID;服务的部门
     */
    private String departmentId;

    /**
     * 数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）
     */
    private Long dataScope;

    /**
     * 手动授权【1】/自动授权【2】
     *  默认手动授权
     */
    private int authWay = 1;

    /**
     * 是否已经存在，非存储字段
     */
    private transient boolean exits;
}
