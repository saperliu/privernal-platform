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
 * 公共组[用户或角色]对象 system_common_group
 *
 * @author saperliu
 * @date 2024-03-05
 * @Description 公共组[用户或角色]的实体
 */
@TableName(value = "system_common_group", autoResultMap = true)
@Data
public class SystemCommonGroup extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 组名
     */
    private String name;
    /**
     * 组编码
     */
    private String code;
    /**
     * 模块类型
     */
    private String moduleType;
    /**
     * 描述
     */
    private String description;
    /**
     * 类型
     */
    private String groupType;
    /**
     * 组织ID
     */
    private String organizationId;
    /**
     * 部门ID
     */
    private String departmentId;


}
