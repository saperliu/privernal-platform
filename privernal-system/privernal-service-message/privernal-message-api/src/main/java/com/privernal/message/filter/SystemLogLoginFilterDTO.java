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

package com.privernal.message.filter;

import com.privernal.common.web.domain.FilterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 登录日志对象 system_log_login
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 登录日志的过滤器
 */
@Tag(name = "SystemLogLoginFilterDTO", description = "登录日志对应的查询过滤器")
@Data
public class SystemLogLoginFilterDTO extends FilterDTO {
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
     * 登录IP
     */
    @Schema(description = "登录IP")
    private String loginIp;


    /**
     * 登录地址
     */
    @Schema(description = "登录地址")
    private String loginLocation;


    /**
     * 浏览器
     */
    @Schema(description = "浏览器")
    private String browser;


    /**
     * 操作系统
     */
    @Schema(description = "操作系统")
    private String os;


    /**
     * 平台固件
     */
    @Schema(description = "平台固件")
    private String platform;


    /**
     * 消息
     */
    @Schema(description = "消息")
    private String message;


    /**
     * 状态;登录是否成功（0正常 1异常）
     */
    @Schema(description = "状态;登录是否成功（0正常 1异常）")
    private Long status;


    /**
     * 组织
     */
    @Schema(description = "组织")
    private String organizationId;


    /**
     * 部门
     */
    @Schema(description = "部门")
    private String departmentId;

}
