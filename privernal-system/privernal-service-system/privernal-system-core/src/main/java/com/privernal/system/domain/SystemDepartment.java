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
 * 部门对象 system_department
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门的实体
 */
@Data
public class SystemDepartment extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 部门编码
     */
    private String deptCode;
    /**
     * 部门名称
     */
    private String deptName;
    /**
     * 排序
     */
    private Long orderNum;
    /**
     * 主管的ID
     */
    private String leaderId;

    /**
     * 状态;是否禁用 0是否，1是是
     */
    private Long status;

    /**
     * 组织ID;所属组织
     */
    private String organizationId;

    /**
     * 部门的系统类型 NORMAL 普通   SYSTEM  系统
     */
    private String systemType;

    /**
     * 同步标识，1是同步，0非同步
     */
    private Long synFlag;

    /**
     * 同步第三方的ID
     */
    private String synId;

    /**
     * 主管的名称
     */
    private transient String leaderName;

    /**
     * 上级组织ID
     */
    private transient String parentId;

}
