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

package com.privernal.flow.mapper;

import com.privernal.common.datascope.annotation.IgnoreDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 流程绩效统计Mapper（R2-17 流程绩效看板/流程预测）。
 * <p>引擎表按组织拦截器忽略（同 FlowQueryMapper），时间差计算用 databaseId 成对方言片段
 * （DialectSnippets.xml 同模式：mysql=TIMESTAMPDIFF，sqlite=julianday）。</p>
 *
 * @author saperliu
 */
@IgnoreDataScope
@Mapper
public interface FlowStatsMapper {

    /**
     * 实例总览：total/running/finished/terminated（时间范围可空）
     */
    Map<String, Object> selectOverview(@Param("timeStart") String timeStart, @Param("timeEnd") String timeEnd);

    /**
     * 已完成实例平均办结时长（秒）
     */
    Double selectAvgDuration(@Param("timeStart") String timeStart, @Param("timeEnd") String timeEnd);

    /**
     * 按流程定义统计：flowName/total/running/finished/avgDurationSeconds/rejectCount
     */
    List<Map<String, Object>> selectDefinitionStats(@Param("timeStart") String timeStart,
                                                    @Param("timeEnd") String timeEnd,
                                                    @Param("limit") int limit);

    /**
     * 按节点统计历史停留（仅 PASS 记录）：nodeCode/nodeName/passCount/avgDwellSeconds/maxDwellSeconds
     */
    List<Map<String, Object>> selectNodeStats(@Param("definitionId") String definitionId);

    /**
     * 在办负载：processed_by（用户/角色/部门标识原样）→ 待办任务数
     */
    List<Map<String, Object>> selectHandlerWorkload(@Param("limit") int limit);

    /**
     * 节点历史平均停留秒数（PASS 记录，指定定义+节点）
     */
    Map<String, Object> selectNodeAvgDwell(@Param("definitionId") String definitionId,
                                           @Param("nodeCode") String nodeCode);

    /**
     * 节点历史通过/驳回次数（预测风险参考）
     */
    Map<String, Object> selectNodePassRate(@Param("definitionId") String definitionId,
                                           @Param("nodeCode") String nodeCode);
}
