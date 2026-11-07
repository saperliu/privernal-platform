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

/**
 * 应用可以访问的路径对象 system_app_path
 *
 * @author liuguanglu
 * @date 2024-03-05
 * @Description 应用可以访问的路径的过滤器
 */
@Tag(name = "SystemAppPathFilterDTO", description = "应用可以访问的路径对应的查询过滤器")
@Data
public class SystemAppPathFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 应用
     */
    @Schema(description = "应用")
    private String appId;


    /**
     * path
     */
    @Schema(description = "path")
    private String path;


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
