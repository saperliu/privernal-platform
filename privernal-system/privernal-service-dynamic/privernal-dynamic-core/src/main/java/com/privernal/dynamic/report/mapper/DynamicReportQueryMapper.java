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

package com.privernal.dynamic.report.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import com.privernal.common.datascope.mapper.DataScopeMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 报表聚合查询 mapper：行级数据范围复用数据权限切面通道（与动态表同链路）。
 * <p>
 * 类注解 permissionOne="dynamic:"，切面从首参 tableName 组装权限码 dynamic:{tableCode}:list，
 * 与查看者在角色数据范围里的按表配置匹配，DataPermissionInterceptor 自动把
 * department_id IN(...) / created_by=... 追加进聚合 SQL——方案A（查看者须对底层表有 list
 * 且按查看者行级范围过滤）由此自动满足，无需另写权限判定。
 * <p>
 * SQL 骨架由服务层构建：标识符（表/列）经白名单校验后拼接，值一律 {@code #{paramMap.pN}}
 * 绑定，杜绝自由 SQL 与字符串拼接值。
 * <p>
 * mainAlias="t0"（M3-2 跨表关联）：服务层把聚合主表/lookup 目标表恒别名为 t0，切面拼出的
 * 行级条件因此带 t0. 前缀——JOIN 出来的第二张表同名列（department_id/created_by）不再
 * 歧义，行级范围始终只收口在主表上（关联表不单独收口）。
 *
 * @author saperliu
 */
@PrivernalDataScope(permissionOne = "dynamic:", mainAlias = "t0")
public interface DynamicReportQueryMapper extends DataScopeMapper {

    /**
     * 执行聚合查询
     *
     * @param tableName 底层动态表物理表名（首参，供切面组装权限码）
     * @param querySql  服务层构建的聚合 SQL（含 #{paramMap.pN} 值占位）
     * @param paramMap  值绑定参数
     * @return 聚合结果行
     */
    List<Map<String, Object>> aggregate(@Param("tableName") String tableName,
                                        @Param("querySql") String querySql,
                                        @Param("paramMap") Map<String, Object> paramMap);

    /**
     * 关联表显示值批量回查（lookup 维度翻译用），行级范围同样经切面追加。
     * displayField 单列，目标表权限口径与关联显示数据一致。
     */
    List<Map<String, Object>> lookupDisplay(@Param("tableName") String tableName,
                                            @Param("querySql") String querySql,
                                            @Param("paramMap") Map<String, Object> paramMap);
}
