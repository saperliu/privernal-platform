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

/**
 * 用户对象 system_user
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户对象 system_user
 */
@Tag(name = "SystemUserDTO", description = "用户对应的DTO")
@Data
public class SystemUserDTO extends BaseDTO {
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
     * F4 挑战登录凭证：AES-GCM 封存的线摘要（null=未迁移）
     */
    @Schema(description = "封存口令摘要")
    private String passwordV2;

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
     * 所属组织ID
     */
    @Schema(description = "所属组织ID")
    private String organizationId;

    /**
     * 所属部门ID[主部门:必须存在]
     */
    @Schema(description = "所属部门ID[主部门:必须存在]")

    private String departmentId;

    /**
     * 同步第三方的ID
     * 例如：钉钉的userId，企微的open_id，微信的open_id
     */
    private String openId;

    /**
     * 同步第三方的账号
     * 例如：钉钉的员工号，企微的userid[账号]，微信的微信号
     */
    private String thirdAccount;

    /**
     * 同步第三方的全局ID
     */
    private String thirdUnionId;

    /**
     * 保存用户的服务组织时传参使用
     */
    private transient Long dataScope;

    /**
     * 组织名称
     */
    private transient String organizationName;
}
