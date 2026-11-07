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

import com.privernal.common.web.domain.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.List;

/**
 * 角色与用户关系对象 system_role_user
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色与用户关系对象 system_role_user
 */
@Tag(name = "SystemRoleUserDTO", description = "角色与用户关系对应的DTO")
@Data
public class SystemRoleUserDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 角色ID
     */
    @Schema(description = "角色ID")

    private String roleId;
    private String roleName;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")

    private String userId;

    /**
     * 登录名
     */
    @Schema(description = "登录名")
    private String loginName;

    /**
     * 昵称
     */
    @Schema(description = "昵称")
    private String nickName;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 手机
     */
    @Schema(description = "手机")
    private String phone;

    /**
     * 状态;状态（0正常 1停用）
     */
    @Schema(description = "状态;状态（0正常 1停用）")
    private Long status;

    @Schema(description = "部门")
    private String departmentName;

    /**
     * 工作流角色里的人员
     */
    @Schema(description = "人员列表")
    private transient List<SystemUserDTO> userDTOList;

    /**
     * 角色查询角色的人员列表时，人员有哪些部门；
     * 部门列表
     */
    private List<SystemDepartmentDTO> departmentDTOList;
}
