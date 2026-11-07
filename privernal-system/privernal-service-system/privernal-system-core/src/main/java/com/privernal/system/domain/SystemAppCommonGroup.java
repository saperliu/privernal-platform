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
 * 应用关联的公共组对象 system_app_common_group
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 应用关联的公共组的实体
 */
@TableName(value = "system_app_common_group", autoResultMap = true)
@Data
public class SystemAppCommonGroup extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * primary key id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 应用的ID
     */
    private String appId;
    /**
     * 关联的公共组
     */
    private String commonGroupId;
    /**
     * 状态
     */
    private Long status;
    /**
     * 组织ID
     */
    private String organizationId;


}
