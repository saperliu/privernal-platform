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

package com.privernal.system.domain;

import com.privernal.common.mybatis.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 组关联的权限对象 system_common_group_menu
 *
 * @author saperliu
 * @date 2024-07-10
 * @Description 组关联的权限的实体
 */
@TableName(value = "system_common_group_menu", autoResultMap = true)
@Data
public class SystemCommonGroupMenu extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * primary key id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 菜单的ID
     */
    private String menuId;
    /**
     * 关联的公共组
     */
    private String commonGroupId;
    /**
     * 状态
     */
    private Integer status;
    /**
     * 组织ID
     */
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
}
