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

import com.privernal.dynamic.advanced.domain.DynamicLinkageRule;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 联动规则 Mapper（master 元数据库）。
 * <p>
 * 按表编码匹配规则用 join dynamic_form（规则的锚点是 form_id，事件的锚点是 tableCode）。
 * 查询发生在联动触发路径上，组织过滤依赖调用方先把事件里的 organizationId 灌进
 * {@code OrganizationContextHolder}——缺组织上下文时不应触发任何规则。
 */
@PrivernalDataScope(permissionMain = "dynamic:form")
public interface DynamicLinkageRuleMapper extends PrivernalMapper<DynamicLinkageRule> {

    @Select("SELECT r.* FROM dynamic_linkage_rule r "
            + "JOIN dynamic_form f ON r.form_id = f.id AND f.deleted_flag = 'N' "
            + "WHERE f.table_code = #{tableCode} AND r.deleted_flag = 'N' "
            + "AND r.enabled = 'Y' AND r.event_type = #{eventType} "
            + "ORDER BY r.created_time ASC")
    List<DynamicLinkageRule> selectEnabledByTableAndEvent(@Param("tableCode") String tableCode,
                                                          @Param("eventType") String eventType);
}
