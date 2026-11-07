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

import com.privernal.common.core.utils.tree.TreeNode;
import com.privernal.common.web.domain.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.util.List;

/**
 * 组关联的权限对象 system_common_group_menu
 *
 * @author saperliu
 * @date 2024-07-10
 * @Description 组关联的权限对象 system_common_group_menu
 */
@Tag(name = "SystemCommonGroupMenuDTO", description = "组关联的权限对应的DTO")
@Data
public class SystemCommonGroupMenuDTO extends BaseDTO implements TreeNode<SystemCommonGroupMenuDTO, String> {
    private static final long serialVersionUID = 1L;

    /**
     * primary key id
     */
    @Schema(description = "primary key id")

    private String id;

    /**
     * 菜单的ID
     */
    @Schema(description = "菜单的ID")

    private String menuId;

    /**
     * 关联的公共组
     */
    @Schema(description = "关联的公共组")

    private String commonGroupId;

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

    /**
     * 组必须要有的功能,0否，1是
     */
    private Integer mustMenu;

    /**
     * 公共组在组织内授权时不显示的功能，但又必须要有的功能
     * 0不显示，1显示，默认显示。
     */
    private Integer visibleMenu;

    private String menuName;

    private String menuType;

    private String permission;

    private String parentId;

    private List<SystemCommonGroupMenuDTO> children;

    @Schema(description = "做为子表时标记操作类型/ DELETE/EDIT/ADD ")
    private String actionTag;

    // 排序的字段
    private Long orderNum;

    @Override
    public Comparable<?> getOrderNum() {
        return orderNum;
    }
}