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
 * 组织对象 system_organization
 *
 * @author saperliu
 * @date 2022-10-22
 * @Description 组织的实体
 */
@Data
public class SystemOrganization extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 组织名称
     */
    private String organizationName;
    /**
     * 排序
     */
    private Long orderNum;
    /**
     * 主管的ID;就是用户的ID
     */
    private String leaderId;
    /**
     * 状态;是否禁用，0否，1是
     */
    private Long status;

    /**
     * 组织的编码
     */
    private String organizationCode;

    /**
     * 主管的名称
     */
    private transient String leaderName;

    /**
     * 同步类型; 1同步钉钉，2同步企微，0自管理不同步任何第三方
     */
    private Long synType;
}
