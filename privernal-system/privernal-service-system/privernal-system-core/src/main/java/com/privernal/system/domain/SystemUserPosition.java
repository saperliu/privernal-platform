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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户的岗位对象 system_user_position
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 用户的岗位的实体
 */
@Data
public class SystemUserPosition extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 岗位ID
     */
    private String positionId;
//    /**
//     * 组织ID;此数据所属组织
//     */
//    @Excel(name = "组织ID;此数据所属组织")
//    private String organizationId;
//    /**
//     * 部门ID;所属部门
//     */
//    @Excel(name = "部门ID;所属部门")
//    private String departmentId;

    /**
     * 是否主岗位(0 否，1是)，目前主岗位放在了user表里
     */
    @Schema(description = "是否主岗位(0 否，1是)，目前主岗位放在了user表里")
    private Long isMain;

    /**
     * 是否已经存在，非存储字段
     */
    private transient boolean exits;
}
