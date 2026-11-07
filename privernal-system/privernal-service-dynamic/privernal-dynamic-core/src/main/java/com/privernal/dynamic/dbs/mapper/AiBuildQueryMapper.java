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

package com.privernal.dynamic.dbs.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 开发一档通道专用查询 Mapper。
 * <p>
 * 类注解 {@code ignoreDepartment = true} 命中数据范围切面的短路分支（无部门/组织附加条件、
 * 不查会话用户）——无登录态的 /openapi 线程里，普通注解 Mapper 的基础方法会因
 * "注解存在但取不到会话用户"被改写为 WHERE 1=4（实测踩坑）。表单编码全局唯一
 * （含组织前缀），此处不带组织过滤与既有保存链的查重口径一致。
 */
@PrivernalDataScope(permissionMain = "dynamic:form", ignoreDepartment = true)
public interface AiBuildQueryMapper {

    @Select("SELECT COUNT(*) FROM dynamic_form WHERE deleted_flag = 'N' AND table_code = #{tableCode} "
            + "AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countFormByTableCode(@Param("tableCode") String tableCode, @Param("excludeId") String excludeId);
}
