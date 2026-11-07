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
 * 应用信息对象 system_app_info
 *
 * @author liuguanglu
 * @date 2024-03-05
 * @Description 应用信息对象 system_app_info
 */
@Tag(name = "SystemAppInfoDetailDTO", description = "应用信息对应的详情DTO")
@Data
public class SystemAppInfoDetailDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * primary key id
     */
    @Schema(description = "primary key id")

    private String id;

    /**
     * 应用KEY
     */
    @Schema(description = "应用KEY")

    private String appKey;

    /**
     * 应用密钥
     */
    @Schema(description = "应用密钥")

    private String appSecret;

    /**
     * 图标
     */
    @Schema(description = "图标")

    private String icon;

    /**
     * 名称
     */
    @Schema(description = "名称")

    private String appName;

    /**
     * 编码
     */
    @Schema(description = "编码")

    private String code;

    /**
     * 类型
     */
    @Schema(description = "类型")

    private String type;

    /**
     * 用户
     */
    @Schema(description = "用户")

    private String userId;

    /**
     * 状态
     */
    @Schema(description = "状态")

    private Long status;

    /**
     * 组织ID
     */
    @Schema(description = "组织ID")

    private String organizationId;

}
