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
 * 菜单对象 system_menu
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 菜单的过滤器
 */
@Tag(name = "SystemMenuFilterDTO", description = "菜单对应的查询过滤器")
@Data
public class SystemMenuFilterDTO extends FilterDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;

    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")
    private String menuName;

    /**
     * 菜单编码
     */
    @Schema(description = "菜单编码")
    private String menuCode;

    /**
     * 显示顺序;显示顺序
     */
    @Schema(description = "显示顺序;显示顺序")
    private Long orderNum;

    /**
     * 路由地址;路由地址
     */
    @Schema(description = "路由地址;路由地址")
    private String path;

    /**
     * 组件路径;组件路径
     */
    @Schema(description = "组件路径;组件路径")
    private String component;

    /**
     * 路由参数;路由参数
     */
    @Schema(description = "路由参数;路由参数")
    private String params;

    /**
     * 是否为外链;是否为外链（0是 1否）
     */
    @Schema(description = "是否为外链;是否为外链（0是 1否）")
    private Integer isFrame;

    /**
     * 是否缓存;是否缓存（0缓存 1不缓存）
     */
    @Schema(description = "是否缓存;是否缓存（0缓存 1不缓存）")
    private Integer isCache;

    /**
     * 菜单类型;菜单类型（M目录 C菜单 F按钮）
     */
    @Schema(description = "菜单类型;菜单类型（M目录 C菜单 F按钮）")
    private String menuType;

    /**
     * 菜单状态;菜单状态（0显示 1隐藏）
     */
    @Schema(description = "菜单状态;菜单状态（0显示 1隐藏）")
    private Integer visible;

    /**
     * 菜单状态;菜单状态（0正常 1停用）
     */
    @Schema(description = "菜单状态;菜单状态（0正常 1停用）")
    private Integer status;

    /**
     * 权限标识;权限标识
     */
    @Schema(description = "权限标识;权限标识")
    private String permission;

    /**
     * 父级菜单;父级菜单
     */
    @Schema(description = "父级菜单;父级菜单")
    private String parentId;

    /**
     * 菜单图标;菜单图标
     */
    @Schema(description = "菜单图标;菜单图标")
    private String icon;

    /**
     * 组织ID;所属组织的配置
     */
    private String organizationId;

    @Schema(description = "菜单类型;菜单类型（M目录 C菜单 F按钮）")
    private String[] menuTypes;

    /**
     * 应用的编码
     */
    private String appCode;
}
