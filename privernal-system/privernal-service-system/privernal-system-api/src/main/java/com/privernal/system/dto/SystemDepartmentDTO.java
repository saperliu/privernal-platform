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

package com.privernal.system.dto;

import com.privernal.common.core.utils.tree.TreeNode;
import com.privernal.common.web.domain.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.List;

/**
 * 部门对象 system_department
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 部门对象 system_department
 */
@Tag(name = "SystemDepartmentDTO", description = "部门对应的DTO")
@Data
public class SystemDepartmentDTO extends BaseDTO implements TreeNode<SystemDepartmentDTO, String> {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 部门编码
     */
    @Schema(description = "部门编码")

    private String deptCode;

    /**
     * 部门名称
     */
    @Schema(description = "部门名称")

    private String deptName;

    /**
     * 排序
     */
    @Schema(description = "排序")

    private Long orderNum;

    /**
     * 主管的ID
     */
    @Schema(description = "主管的ID")

    private String leaderId;

    /**
     * 状态;是否禁用 0是否，1是是
     */
    @Schema(description = "状态;是否禁用 0是否，1是是")

    private Long status;

    /**
     * 组织ID;所属组织
     */
    @Schema(description = "组织ID;所属组织")

    private String organizationId;

    /**
     * 主管的名称
     */
    private String leaderName;

    /**
     * 部门的系统类型 NORMAL 普通   SYSTEM  系统
     */
    private String systemType;

    /**
     * 子部门
     */
    private List<SystemDepartmentDTO> children;

    /**
     * 上级组织ID
     */
    @Schema(description = "上级组织ID")
    private String parentId;

    /**
     * 同步标识，1是同步，0非同步
     */
    @Schema(description = "同步标识，1是同步，0非同步")
    private Long synFlag;

    /**
     * 同步第三方的ID
     */
    private String synId;

    /**
     * 在通过角色查询用户拥有的部门权限时会有此数据。
     * 用于保存新的部门权限。
     *
     */
    @Schema(description = "角色与用户关系表的ID")
    private transient String roleUserId;
    /**
     * 在通过角色查询用户拥有的部门权限时会有此数据。
     * 用于删除此部门权限
     *
     */
    @Schema(description = "角色与用户关系表的部门表的ID")
    private transient String roleUserDeptId;

    /**
     * 在通过角色查询用户拥有的部门权限时会有此数据。
     * 手动授权【1】/自动授权【2】
     *  默认手动授权
     */
    private int authWay;

    @Override
    public Comparable<?> getOrderNum() {
        return orderNum;
    }
}