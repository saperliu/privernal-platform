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

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.system.domain.SystemOpenapiCredential;

/**
 * OpenAPI 凭证 Mapper
 * <p>
 * 只做组织内 CRUD，行级部门数据范围不适用（凭证是组织级资源）。
 * ⚠ 2026-10-06 缺陷修复：此前'不加 @PrivernalDataScope'的判断不成立——数据范围切面对无注解
 * Mapper 的 PrivernalMapper 基础方法（selectCount/selectOne 等）一律把 WHERE 改写为 1=2，
 * 导致 generateAccessKey 的编码查重恒空、创建凭证必然撞唯一键。补类注解后基础方法恢复
 * 正常（管理端会话按权限解析，凭证本身仍是组织级）——与 SystemDictionaryDataMapper 同款口径。
 */
@PrivernalDataScope(permissionMain = "system:openapiCredential", ignoreDepartment = true)
public interface SystemOpenapiCredentialMapper extends PrivernalMapper<SystemOpenapiCredential> {
}
