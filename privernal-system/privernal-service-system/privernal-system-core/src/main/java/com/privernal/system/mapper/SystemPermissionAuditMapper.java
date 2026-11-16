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

package com.privernal.system.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemRoleUser;
import com.privernal.system.dto.PermissionMatrixRowDTO;
import com.privernal.system.filter.PermissionMatrixFilterDTO;

import java.util.List;

/**
 * 权限治理审计Mapper（R1-8 权限治理②）
 *
 * 跨表台账查询（role_user × user × role × role_menu × app_info）；
 * 组织范围由控制器按角色强制注入（非平台管理员锁定当前组织），故忽略数据范围切面。
 *
 * @author saperliu
 * @date 2026-11-12
 * @Description R1-8 权限治理：权限台账查询
 */
@IgnoreDataScope
public interface SystemPermissionAuditMapper extends PrivernalMapper<SystemRoleUser> {

    /**
     * 人 × 角色 × 应用 × 权限 × 数据范围 台账（仅生效中的授权：角色启用、授权未到期、用户未停用）
     *
     * @param filter 查询条件（organizationId/userId/roleId）
     * @return 台账行集合
     */
    List<PermissionMatrixRowDTO> selectPermissionMatrix(PermissionMatrixFilterDTO filter);
}
