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
import lombok.Data;

/**
 * 用户的应用
 *
 * @author saperliu
 */
@Data
public class SystemUserAppInfoDTO extends BaseDTO {
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
     * 所属部门ID;所属部门ID[主部门:必须存在]
     */
    private String departmentId;

    private String appId;

    private String appName;

    private String appKey;

}
