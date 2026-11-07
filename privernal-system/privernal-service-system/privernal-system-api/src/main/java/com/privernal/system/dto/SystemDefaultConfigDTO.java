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
 * 系统配置对象 system_default_config
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 系统配置对象 system_default_config
 */
@Tag(name = "SystemDefaultConfigDTO", description = "系统配置对应的DTO")
@Data
public class SystemDefaultConfigDTO<T> extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")

    private String id;

    /**
     * 组织ID;所属组织的配置
     */
    @Schema(description = "组织ID;所属组织的配置")

    private String organizationId;

    /**
     * 配置的键名
     */
    @Schema(description = "配置的键名")

    private String configKey;

    /**
     * 配置的数据
     */
    @Schema(description = "配置的数据")
    private T configValue;

    /**
     * 配置的名称
     */
    @Schema(description = "配置的名称")
    private String configName;

}
