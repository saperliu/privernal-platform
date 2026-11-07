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
 * 用户收藏的常用功能菜单对象 system_user_app_menu
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 用户收藏的常用功能菜单的实体
 */
@TableName(value = "system_user_app_menu", autoResultMap = true)
@Data
public class SystemUserAppMenu extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 用户的ID
     */
    private String userId;
    /**
     * 关联的菜单
     */
    private String menuId;
    /**
     * 状态
     */
    private Long status;
    /**
     * 组织ID
     */
    private String organizationId;


}
