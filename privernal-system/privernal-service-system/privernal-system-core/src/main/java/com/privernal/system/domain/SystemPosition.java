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
import lombok.Data;

/**
 * 岗位对象 system_position
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 岗位的实体
 */
@Data
public class SystemPosition extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 岗位名称
     */
    private String positionName;
    /**
     * 岗位编码
     */
    private String positionCode;
    /**
     * 状态;是否禁用，0是否，1是是
     */
    private Long status;
    /**
     * 排序值
     */
    private Long orderNum;
    /**
     * 组织ID;所属组织
     */
    private String organizationId;
    /**
     * 部门ID;所属部门
     */
    private String departmentId;

}
