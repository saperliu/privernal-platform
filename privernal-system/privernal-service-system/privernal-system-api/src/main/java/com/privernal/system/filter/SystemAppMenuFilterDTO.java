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
 * 应用关联的菜单对象 system_app_menu
 *
 * @author liuguanglu
 * @date 2024-03-05
 * @Description 应用关联的菜单的过滤器
 */
@Tag(name = "SystemAppMenuFilterDTO", description = "应用关联的菜单对应的查询过滤器")
@Data
public class SystemAppMenuFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;


    /**
     * 应用的ID
     */
    @Schema(description = "应用的ID")
    private String appId;


    /**
     * 关联的菜单
     */
    @Schema(description = "关联的菜单")
    private String menuId;

    private String menuName;


    /**
     * 状态
     */
    @Schema(description = "状态")
    private Integer status;


    /**
     * 组织ID
     */
    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "必须要有的功能")
    private Integer mustMenu;

    /**
     * 应用在组织内授权时不显示的功能，但又必须要有的功能
     * 0不显示，1显示，默认显示。
     */
    @Schema(description = "功能是否显示")
    private Integer visibleMenu;

}
