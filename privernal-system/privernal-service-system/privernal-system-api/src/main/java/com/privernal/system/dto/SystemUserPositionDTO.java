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
 * 用户的岗位对象 system_user_position
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的岗位对象 system_user_position
 */
@Tag(name = "SystemUserPositionDTO", description = "用户的岗位对应的DTO")
@Data
public class SystemUserPositionDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")

    private String userId;

    /**
     * 岗位ID
     */
    @Schema(description = "岗位ID")

    private String positionId;

//    /**
//     * 组织ID;此数据所属组织
//     */
//    @Schema(description = "组织ID;此数据所属组织")
//    @Excel(name = "组织ID;此数据所属组织")
//
//    private String organizationId;
//
//    /**
//     * 部门ID;所属部门
//     */
//    @Schema(description = "部门ID;所属部门")
//    @Excel(name = "部门ID;所属部门")
//
//    private String departmentId;

    /**
     * 是否主岗位(0 否，1是)，目前主岗位放在了user表里
     */
    @Schema(description = "是否主岗位(0 否，1是)，目前主岗位放在了user表里")
    private Long isMain;

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

    @Schema(description = "人员列表")
    private transient List<SystemUserDTO> userDTOList;
}
