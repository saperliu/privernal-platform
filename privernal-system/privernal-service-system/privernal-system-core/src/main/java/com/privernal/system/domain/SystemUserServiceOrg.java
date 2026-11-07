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
 * 用户服务组织对象 system_user_service_org
 *
 * @author saperliu
 * @date 2022-10-24
 * @Description 用户服务组织的实体
 */
@Data
public class SystemUserServiceOrg extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
    /**
     * 用户ID;用户ID
     */
    private String userId;
    /**
     * 组织ID;服务的组织ID
     */
    private String organizationId;
    /**
     * 数据权限（50：全部数据权限 40：自定数据权限 20：本部门数据权限 30：本部门及以下数据权限 10: 仅本人数据权限）
     */
    private Long dataScope;
    /**
     * 部门ID
     */
    private String departmentId;

}
