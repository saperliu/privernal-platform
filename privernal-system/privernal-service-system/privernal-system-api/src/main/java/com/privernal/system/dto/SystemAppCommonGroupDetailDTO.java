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
 * 应用关联的公共组对象 system_app_common_group
 *
 * @author liuguanglu
 * @date 2024-03-05
 * @Description 应用关联的公共组对象 system_app_common_group
 */
@Tag(name = "SystemAppCommonGroupDetailDTO", description = "应用关联的公共组对应的详情DTO")
@Data
public class SystemAppCommonGroupDetailDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * primary key id
     */
    @Schema(description = "primary key id")

    private String id;

    /**
     * 应用的ID
     */
    @Schema(description = "应用的ID")

    private String appId;

    /**
     * 关联的公共组
     */
    @Schema(description = "关联的公共组")

    private String commonGroupId;

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
