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

import java.util.List;

/**
 * 角色与菜单关系对象 system_role_menu
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 角色与菜单关系对象 system_role_menu
 */
@Tag(name = "SystemRoleMenuDTO", description = "角色与菜单关系对应的DTO")
@Data
public class SystemRoleMenuDTO extends BaseDTO {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Schema(description = "主键")
    private String id;

    /**
     * 角色ID
     */
    @Schema(description = "角色ID")
    private String roleId;

    /**
     * 菜单ID
     */
    @Schema(description = "菜单ID")
    private String menuId;

    /**
     * 应用ID
     */
    @Schema(description = "应用ID")
    private String appId;

    /**
     * 菜单的权限标识;后端通过标识查询角色授权，然后通过当前用户和角色匹配，找到此用户设置的服务部门，再查询数据。如果没有设置则只查询主部门的
     */
    @Schema(description = "菜单的权限标识;后端通过标识查询角色授权，然后通过当前用户和角色匹配，找到此用户设置的服务部门，再查询数据。如果没有设置则只查询主部门的")
    private String permission;

    /**
     * 数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）
     */
    @Schema(name = "数据权限", description = "50=：全部数据权限,40=：自定数据权限,20=：本部门数据权限,30=：本部门及以下数据权限,10=: 本人数据权限")
    private Long dataScope;

    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")
    private String menuName;


    @Schema(description = "功能及数据范围列表")
    private transient List<SystemRoleMenuScopeDTO> menuDTOList;
}
