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

package com.privernal.system.filter;

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.List;

/**
 * 用户对象 system_user
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的过滤器
 */
@Tag(name = "SystemUserFilterDTO", description = "用户对应的查询过滤器")
@Data
public class SystemUserFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;


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
     * 用户类型（sys 系统用户）
     */
    @Schema(description = "用户类型（sys 系统用户）")
    private String userType;


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
     * 密码
     */
    @Schema(description = "密码")
    private String password;


    /**
     * 性别;用户性别（0男 1女 2未知）
     */
    @Schema(description = "性别;用户性别（0男 1女 2未知）")
    private String gender;


    /**
     * 头像
     */
    @Schema(description = "头像")
    private String avatar;


    /**
     * 状态;状态（0正常 1停用）
     */
    @Schema(description = "状态;状态（0正常 1停用）")
    private Long status;


    /**
     * 身份证号;身份证号
     */
    @Schema(description = "身份证号;身份证号")
    private String identityNum;


    /**
     * 所属部门ID
     */
    @Schema(description = "所属部门ID")
    private String organizationId;


    /**
     * 所属部门ID;所属部门ID[主部门:必须存在]
     */
    @Schema(description = "所属部门ID;所属部门ID[主部门:必须存在]")
    private String departmentId;

    /**
     * 主键
     */
    @Schema(description = "用户ID，排除自己")
    private String userId;

    @Schema(description = "多个部门")
    private String[] deptIds;
}
