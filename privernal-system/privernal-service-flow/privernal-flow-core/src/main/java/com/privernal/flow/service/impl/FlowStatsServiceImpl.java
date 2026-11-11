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

package com.privernal.flow.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.flow.mapper.FlowStatsMapper;
import com.privernal.flow.service.IFlowStatsService;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程绩效统计服务实现（R2-17）
 * <p>预测口径：剩余时长 = 当前各待办节点历史平均停留（同定义同节点 PASS 样本）求和；
 * 纯统计规则，不引入模型。样本量与通过率一并返回供前端提示置信口径。</p>
 *
 * @author saperliu
 */
@Service
@DS("master")
public class FlowStatsServiceImpl implements IFlowStatsService {

    /** 负载榜展示名解析上限（防大组织 Feign 风暴） */
    private static final int NAME_RESOLVE_MAX = 50;

    @Autowired
    private FlowStatsMapper statsMapper;

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    public Map<String, Object> overview(String timeStart, String timeEnd) {
        Map<String, Object> result = new HashMap<>(statsMapper.selectOverview(timeStart, timeEnd));
        Double avgDuration = statsMapper.selectAvgDuration(timeStart, timeEnd);
        result.put("avgDurationSeconds", avgDuration == null ? 0L : Math.round(avgDuration));
        return result;
    }

    @Override
    public List<Map<String, Object>> definitionStats(String timeStart, String timeEnd, int limit) {
        return statsMapper.selectDefinitionStats(timeStart, timeEnd, limit);
    }

    @Override
    public List<Map<String, Object>> nodeStats(String definitionId) {
        if (StringUtils.isEmpty(definitionId)) {
            throw new ServiceException("definitionId 不能为空");
        }
        return statsMapper.selectNodeStats(definitionId);
    }

    @Override
    public List<Map<String, Object>> handlerWorkload(int limit) {
        List<Map<String, Object>> rows = statsMapper.selectHandlerWorkload(limit);
        // 权限标识三类：裸用户id（引擎转换后）/user:{id}/role:|dept: 等未展开标识——
        // 裸 id 与 user: 前缀尝试解析昵称，其余原样展示
        Set<String> resolveIds = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String flag = String.valueOf(row.get("processedBy"));
            String userId = flag.startsWith("user:") ? flag.substring(5) : flag;
            if (userId.matches("\\d+") && resolveIds.size() < NAME_RESOLVE_MAX) {
                resolveIds.add(userId);
            }
        }
        Map<String, String> names = new HashMap<>();
        for (String userId : resolveIds) {
            try {
                CommonResult<SystemUserDTO> result = remoteUserService.findUserById(userId, "");
                if (result != null && result.getData() != null && StringUtils.isNotEmpty(result.getData().getNickName())) {
                    names.put(userId, result.getData().getNickName());
                }
            } catch (Exception ignored) {
                // 解析失败保留原标识
            }
        }
        List<Map<String, Object>> enriched = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String flag = String.valueOf(row.get("processedBy"));
            String userId = flag.startsWith("user:") ? flag.substring(5) : flag;
            Map<String, Object> item = new HashMap<>(row);
            item.put("displayName", names.getOrDefault(userId, flag));
            item.put("isUserFlag", names.containsKey(userId));
            enriched.add(item);
        }
        return enriched;
    }

    @Override
    public Map<String, Object> predict(Long instanceId) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance == null) {
            throw new ServiceException("流程实例不存在：" + instanceId);
        }
        String definitionId = instance.getDefinitionId() == null ? null : String.valueOf(instance.getDefinitionId());
        Map<String, Object> result = new HashMap<>();
        result.put("instanceId", String.valueOf(instanceId));
        result.put("flowName", instance.getFlowName());
        result.put("businessId", instance.getBusinessId());
        List<Task> todoTasks = FlowEngine.taskService().getByInsId(instanceId);
        boolean running = todoTasks != null && !todoTasks.isEmpty();
        result.put("running", running);
        List<Map<String, Object>> nodes = new ArrayList<>();
        long predictedSeconds = 0L;
        long sampledSeconds = 0L;
        if (running && StringUtils.isNotEmpty(definitionId)) {
            for (Task task : todoTasks) {
                Map<String, Object> node = new HashMap<>();
                node.put("taskId", String.valueOf(task.getId()));
                node.put("nodeCode", task.getNodeCode());
                node.put("nodeName", task.getNodeName());
                Map<String, Object> dwell = statsMapper.selectNodeAvgDwell(definitionId, task.getNodeCode());
                long avgSeconds = 0L;
                long sampleCount = 0L;
                if (dwell != null) {
                    Object avg = dwell.get("avgDwellSeconds");
                    Object count = dwell.get("sampleCount");
                    if (avg != null) {
                        avgSeconds = Math.round(Double.parseDouble(String.valueOf(avg)));
                    }
                    sampleCount = count == null ? 0L : Long.parseLong(String.valueOf(count));
                }
                Map<String, Object> passRate = statsMapper.selectNodePassRate(definitionId, task.getNodeCode());
                long passCount = passRate == null || passRate.get("passCount") == null
                        ? 0L : Long.parseLong(String.valueOf(passRate.get("passCount")));
                long rejectCount = passRate == null || passRate.get("rejectCount") == null
                        ? 0L : Long.parseLong(String.valueOf(passRate.get("rejectCount")));
                node.put("avgDwellSeconds", avgSeconds);
                node.put("sampleCount", sampleCount);
                node.put("passCount", passCount);
                node.put("rejectCount", rejectCount);
                nodes.add(node);
                predictedSeconds += avgSeconds;
                sampledSeconds += sampleCount > 0 ? avgSeconds : 0L;
            }
        }
        result.put("nodeCount", nodes.size());
        result.put("predictedSeconds", predictedSeconds);
        result.put("predictedTime", new Date(System.currentTimeMillis() + predictedSeconds * 1000L));
        result.put("sampledSeconds", sampledSeconds);
        result.put("sampled", sampledSeconds > 0L);
        result.put("nodes", nodes);
        return result;
    }
}
