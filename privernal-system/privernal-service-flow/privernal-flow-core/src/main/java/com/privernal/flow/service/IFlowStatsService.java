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

package com.privernal.flow.service;

import java.util.List;
import java.util.Map;

/**
 * 流程绩效统计服务（R2-17 流程绩效看板 + 流程预测）
 *
 * @author saperliu
 */
public interface IFlowStatsService {

    /**
     * 实例总览 + 平均办结时长
     */
    Map<String, Object> overview(String timeStart, String timeEnd);

    /**
     * 按流程定义统计（limit 上限）
     */
    List<Map<String, Object>> definitionStats(String timeStart, String timeEnd, int limit);

    /**
     * 按节点统计历史停留（PASS/REJECT 分布、平均/最大停留秒）
     */
    List<Map<String, Object>> nodeStats(String definitionId);

    /**
     * 在办负载 TOP N（processed_by 解析为展示名，解析失败的保留原标识）
     */
    List<Map<String, Object>> handlerWorkload(int limit);

    /**
     * 流程预测（R2-17）：按历史各节点平均停留时长预测当前实例剩余流转时间与预计完成时间，
     * 附节点通过率风险参考。无历史样本的节点不计入剩余时长（样本不足时前端提示口径）。
     *
     * @return instanceId/flowName/running/nodeCount/predictedSeconds/predictedTime/
     *         sampledSeconds/nodes[{nodeCode,nodeName,avgDwellSeconds,sampleCount,passCount,rejectCount}]
     */
    Map<String, Object> predict(Long instanceId);
}
