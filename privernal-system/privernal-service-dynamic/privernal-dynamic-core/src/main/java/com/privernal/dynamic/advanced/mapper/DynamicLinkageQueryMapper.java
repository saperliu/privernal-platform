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

package com.privernal.dynamic.advanced.mapper;

import com.privernal.common.datascope.annotation.PrivernalDataScope;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 联动动作目标行查询 Mapper：SQL 骨架由 {@code SafeConditionSql} 构建——
 * 标识符（表/列）经正则白名单校验后拼接，值一律 {@code #{params.pN}} 绑定，杜绝自由 SQL（§7.3）。
 * <p>
 * 纯 @Select 接口，不继承 PrivernalMapper：只服务联动引擎的系统级执行。类级注解满足数据范围
 * 切面的兜底要求；动作执行时调用方已显式注入组织上下文并 setIgnore，切面为 no-op——
 * 动作目标集由规则条件定义，不是查看者权限（§7.4）。
 */
@PrivernalDataScope(permissionMain = "dynamic:form")
public interface DynamicLinkageQueryMapper {

    /**
     * 校验目标表列存在性走 {@code DatabaseDialect#buildGetTableColumnsSql}（方言化元数据查询，
     * 经 selectRows 执行；不存在的列在 SafeConditionSql 白名单之后做第二道防线，
     * 防拼进 WHERE 后 SQL 报错难排查）。原先这里硬编码 information_schema 的 @Select
     * 在 SQLite 下不可用且无调用点，已随方言化一并移除。
     */

    /**
     * 执行条件查询，返回命中行（动作执行/试跑预览共用同一条查询，§7.5.5）
     *
     * @param querySql SELECT ... FROM {table} WHERE ...（标识符已白名单校验）
     * @param params   值绑定参数 p1..pN
     */
    List<Map<String, Object>> selectRows(@Param("querySql") String querySql,
                                         @Param("params") Map<String, Object> params);
}
