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

package com.privernal.flow.handler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.flow.utils.FlowNodeExtUtil;
import com.privernal.job.api.ScheduleTaskClient;
import com.privernal.job.api.TaskRef;
import com.privernal.system.dto.SystemUserDTO;
import com.privernal.system.feign.RemoteUserService;
import com.privernal.common.web.domain.CommonResult;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * warm-flow 超时任务同步器（乙方案为主）。
 *
 * <p>每个流转事件（审批/驳/转办/加签/换人/…）后按实例当前待办全量对齐任务表：
 * 配了 wfTimeoutHours&gt;0 的待办 → registerOnce 幂等注册/改期（fire_time=创建时刻+时长，
 * 换人不重置计时由"按当前 create_time 改期"实现——warm-flow 无 last_deal_time，§10.4）；
 * 引擎已不存在的待办 → 取消对应任务行。biz_key='wf:ins:{insId}:task:{taskId}'，
 * 取消按实例前缀精确命中（§10.1）。</p>
 *
 * <p>异步执行：流转方法处在引擎事务与 @DS 上下文里，注册动作丢独立线程池（乙方案
 * 不侵入引擎事务）；FlowDataFillHandler.insertFill 是甲方案兜底哨兵，实例任一待办
 * 插入即延迟触发一次全量对齐，补 §10.2 调用点清单可能遗漏的路径。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class FlowTimeoutTaskSyncer {

    /**
     * @Lazy 注入点：registry 的 @PostConstruct getBeansOfType 沿 handler→syncer→本客户端
     * 解析链会在 scheduleTaskService 仍在创建中时直接抛错（早期引用未就绪），凡注入
 * ScheduleTaskClient 的 bean 一律用 @Lazy 代理打断解析环（关键缺陷第 1 条）。
     */
    @org.springframework.context.annotation.Lazy
    @Autowired
    private ScheduleTaskClient scheduleTaskClient;

    @Autowired
    private RemoteUserService remoteUserService;

    @Autowired
    private FlowCcTaskHandler ccTaskHandler;

    /** 迁移期开关（upgrade_schedule_flow_timeout.sql 说明）：关闭后注册停止，每日兜底仍生效 */
    @Value("${privernal.flow.timeout-sync-enabled:true}")
    private boolean syncEnabled;

    private final ScheduledExecutorService syncExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "flow-timeout-sync");
        thread.setDaemon(true);
        return thread;
    });

    /** 办理人解析的 Feign 调用在同步线程完成（异步线程无请求上下文） */
    public CommonResult<SystemUserDTO> findUser(String userId) {
        return remoteUserService.findUserById(userId, "");
    }

    /**
     * 异步全量对齐：流转事件与 insertFill 哨兵的统一入口（延迟 2s 避开引擎事务提交前窗口）。
     * orgId 在调用线程先反查好再传下去——异步线程发起 Feign 会丢 token。
     */
    public void syncByInstanceAsync(Long instanceId) {
        syncByInstanceAsync(instanceId, null);
    }

    /**
 * 带"重置计时"的全量对齐（D3-B）：resetTaskIds 中的待办
     * fireTime = 同步时刻 + 超时时长（而非 create_time + 时长）——warm-flow 的
     * transfer/depute/加签/减签都保留原任务行（create_time 不变，实测 updateHandler），
     * 办理人变更后必须显式重置计时，否则换人永远不重置。
     */
    public void syncByInstanceAsync(Long instanceId, java.util.Set<Long> resetTaskIds) {
        if (!syncEnabled || instanceId == null) {
            return;
        }
        String orgId = resolveOrgId(instanceId);
        java.util.Set<Long> resets = resetTaskIds == null ? null : java.util.Set.copyOf(resetTaskIds);
        syncExecutor.schedule(() -> doSync(instanceId, orgId, resets), 2, TimeUnit.SECONDS);
    }

    /** 同步全量对齐（每日兜底扫描复用；须在持有引擎可用上下文的线程调用） */
    public void syncByInstance(Long instanceId) {
        if (!syncEnabled || instanceId == null) {
            return;
        }
        doSync(instanceId, resolveOrgId(instanceId), null);
    }

    private void doSync(Long instanceId, String orgId, java.util.Set<Long> resetTaskIds) {
        if (StringUtils.isEmpty(orgId)) {
            log.warn("[flow-timeout] 实例发起人组织解析失败，跳过同步 instanceId={}", instanceId);
            return;
        }
        try {
            List<Task> todoTasks = FlowEngine.taskService().getByInsId(instanceId);
            List<Task> safeTodos = todoTasks == null ? new ArrayList<>() : todoTasks;
            Map<String, Task> currentKeys = new HashMap<>();
            int registered = 0;
            for (Task task : safeTodos) {
                String bizKey = bizKey(instanceId, task.getId());
                currentKeys.put(bizKey, task);
                Node node = ccTaskHandler.findNode(task);
                Map<String, String> ext = FlowNodeExtUtil.parseExt(node == null ? null : node.getExt());
                int hours = FlowNodeExtUtil.resolveTimeoutHours(ext);
                if (hours <= 0) {
                    continue;
                }
                Date now = new Date();
                Date fireTime = resetTaskIds != null && resetTaskIds.contains(task.getId())
                        ? new Date(now.getTime() + hours * 3600_000L)
                        : new Date(task.getCreateTime().getTime() + hours * 3600_000L);
                if (fireTime.before(now)) {
                    // 已超时：30s 后由调度触发，经 handler 复核后立即处置（不必等每日兜底）
                    fireTime = new Date(now.getTime() + 30_000L);
                }
                JSONObject actionConfig = new JSONObject();
                actionConfig.set("action", ext.getOrDefault(FlowNodeExtUtil.KEY_TIMEOUT_ACTION,
                        FlowNodeExtUtil.TIMEOUT_AUTO_PASS));
                JSONObject bizContext = new JSONObject();
                bizContext.set("instanceId", instanceId);
                bizContext.set("taskId", task.getId());
                bizContext.set("nodeCode", task.getNodeCode());
                TaskRef ref = scheduleTaskClient.registerOnce(orgId, "WORKFLOW", bizKey, fireTime,
                        "WORKFLOW_TIMEOUT", actionConfig.toString(), bizContext.toString(), null);
                registered++;
                if (ref.isDeferred()) {
                    log.info("[flow-timeout] 任务在途，延迟生效（下一事件重试）insId={} taskId={}", instanceId, task.getId());
                }
            }
            // 引擎已不存在的待办：取消遗留任务行（按实例前缀精确命中，§10.1）
            String prefix = "wf:ins:" + instanceId + ":task:";
            int cancelled = 0;
            for (String existingKey : scheduleTaskClient.listActiveBizKeys(orgId, "WORKFLOW", prefix)) {
                if (!currentKeys.containsKey(existingKey)) {
                    String taskIdPart = existingKey.substring(prefix.length());
                    scheduleTaskClient.cancel(orgId, "WORKFLOW", existingKey);
                    cancelled++;
                    log.info("[flow-timeout] 待办已不存在，取消超时任务 insId={} taskId={}", instanceId, taskIdPart);
                }
            }
            if (registered > 0 || cancelled > 0) {
                log.info("[flow-timeout] synced instanceId={} todo={} registered={} cancelled={}",
                        instanceId, safeTodos.size(), registered, cancelled);
            }
        } catch (Exception e) {
            log.error("[flow-timeout] 同步失败 instanceId={}", instanceId, e);
        }
    }

    /** 组织反查：实例发起人 → 用户组织（先例 FlowTaskServiceImpl.buildSystemTaskContext） */
    private String resolveOrgId(Long instanceId) {
        try {
            Instance instance = FlowEngine.insService().getById(instanceId);
            if (instance == null || StringUtils.isEmpty(instance.getCreateBy())) {
                return null;
            }
            CommonResult<SystemUserDTO> result = remoteUserService.findUserById(instance.getCreateBy(), "");
            if (result != null && result.getData() != null && StringUtils.isNotEmpty(result.getData().getOrganizationId())) {
                return result.getData().getOrganizationId();
            }
        } catch (Exception e) {
            log.warn("[flow-timeout] 发起人组织反查失败 instanceId={}", instanceId, e);
        }
        return null;
    }

    public static String bizKey(Long instanceId, Long taskId) {
        return "wf:ins:" + instanceId + ":task:" + taskId;
    }

    @PreDestroy
    public void shutdown() {
        syncExecutor.shutdownNow();
    }
}
