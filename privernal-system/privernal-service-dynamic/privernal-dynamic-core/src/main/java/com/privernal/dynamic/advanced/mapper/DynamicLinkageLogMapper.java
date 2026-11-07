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

import com.privernal.dynamic.advanced.domain.DynamicLinkageLog;
import com.privernal.common.mybatis.mapper.PrivernalMapper;
import com.privernal.common.datascope.annotation.PrivernalDataScope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 联动规则执行日志 Mapper（master 元数据库，append-only）。
 */
@PrivernalDataScope(permissionMain = "dynamic:form")
public interface DynamicLinkageLogMapper extends PrivernalMapper<DynamicLinkageLog> {

    /**
     * 幂等快查：同 (rule_id, event_id) 是否已有日志（uk_idem 的前置查询；
     * 并发下的兜底仍靠 insert 撞 DuplicateKeyException）
     */
    @Select("SELECT COUNT(1) FROM dynamic_linkage_log WHERE rule_id = #{ruleId} AND event_id = #{eventId}")
    int countByIdem(@Param("ruleId") String ruleId, @Param("eventId") String eventId);

    /**
     * 联动日志分页（管理端「执行记录」），动态条件由 XML 组装
     */
    List<Map<String, Object>> selectLogPage(@Param("filter") Map<String, Object> filter);

    long countLogPage(@Param("filter") Map<String, Object> filter);

    /**
     * 收尾更新（EXECUTING 占位 → 终态）。不用 updateById：本表无软删列，
     * DeleteFieldFillInterceptor 对无 deleted_flag 实体的 updateById 会算错占位符
     * （Parameter index out of range），显式 SQL 绕开。
     */
    @org.apache.ibatis.annotations.Update("UPDATE dynamic_linkage_log SET status = #{status}, "
            + "action_results = #{actionResults}, error_msg = #{errorMsg}, "
            + "start_time = #{startTime}, cost_ms = #{costMs} WHERE id = #{id}")
    int finalizeLog(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("actionResults") String actionResults,
                    @Param("errorMsg") String errorMsg,
                    @Param("startTime") java.util.Date startTime,
                    @Param("costMs") Integer costMs);
}
