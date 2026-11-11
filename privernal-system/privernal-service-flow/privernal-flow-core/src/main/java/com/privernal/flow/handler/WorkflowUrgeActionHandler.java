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
import com.privernal.common.redis.service.RedisService;
import com.privernal.flow.notice.FlowUrgeNotifier;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * WORKFLOW_URGE 动作：warm-flow 节点重复催办（R2-17）。
 *
 * <p>由 FlowTimeoutTaskSyncer 在流转事件后按节点 ext.wfUrgeMinutes 注册固定间隔调度任务
 * （biz_key='wf:urge:ins:{insId}:task:{taskId}'），任务办结/消失由同步器按前缀取消。
 * 触发时以引擎当前状态复核待办仍在原节点——已办理/换节点则静默成功记 SKIPPED（动作可重入）。</p>
 *
 * <p>ext.wfUrgeMaxTimes &gt; 0 时限次：计数存 Redis（FLOW_URGE_CNT_{taskId}，30 天滚动），
 * 达上限后取消自身调度任务不再催办。0=不限，直到任务办结。</p>
 *
 * <p>action_config: { maxTimes: int }
 * biz_context: { instanceId, taskId, nodeCode }</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowUrgeActionHandler implements ScheduleActionHandler {

    public static final String ACTION_TYPE = "WORKFLOW_URGE";

    /** 催办计数 Redis 键前缀（任务粒度，30 天滚动过期） */
    private static final String URGE_COUNT_KEY = "FLOW_URGE_CNT_";

    private final FlowUrgeNotifier urgeNotifier;

    private final FlowCcTaskHandler ccTaskHandler;

    /** 同步器注入环（registry @PostConstruct 解析链）参照 FlowTimeoutTaskSyncer 用 @Lazy 代理打断 */
    @org.springframework.context.annotation.Lazy
    @Autowired
    private com.privernal.job.api.ScheduleTaskClient scheduleTaskClient;

    @Autowired
    private RedisService redisService;

    @Override
    public String actionType() {
        return ACTION_TYPE;
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        // 周期注册（registerFixedDelay）无 bizContext：实例/任务定位在 action_config 里（兼容旧 biz_context）
        JSONObject config = context.getConfig() == null ? new JSONObject() : new JSONObject(context.getConfig());
        JSONObject biz = parseBiz(task.getBizContext());
        Long flowTaskId = firstNonNull(config.getLong("taskId"), biz.getLong("taskId"));
        Long instanceId = firstNonNull(config.getLong("instanceId"), biz.getLong("instanceId"));
        String nodeCode = config.getStr("nodeCode") != null ? config.getStr("nodeCode") : biz.getStr("nodeCode");
        if (flowTaskId == null) {
            return ActionResult.fail("调度配置缺少 taskId（action_config/biz_context 均为空）");
        }
        // ===== 触发时以引擎当前状态复核（可重入）=====
        Task flowTask = FlowEngine.taskService().getById(flowTaskId);
        if (flowTask == null) {
            return ActionResult.ok("SKIPPED: 待办已办理或已不存在");
        }
        if (StringUtils.isNotEmpty(nodeCode) && !nodeCode.equals(flowTask.getNodeCode())) {
            return ActionResult.ok("SKIPPED: 待办已流转到其他节点 " + flowTask.getNodeCode());
        }
        int maxTimes = FlowUrgeNotifier.parseMaxTimes(config.get("maxTimes"));
        if (maxTimes > 0 && !tryCountUrge(flowTaskId, maxTimes)) {
            cancelSelf(task);
            return ActionResult.ok("已达催办上限（" + maxTimes + " 次），取消后续催办，flowTask=" + flowTaskId);
        }
        int sent = urgeNotifier.send(flowTask, null);
        // 自链式续注册：WORKFLOW 来源的任务被内核按一次性语义办结（finishOnce），本次催完
        // 重新注册下一次（同 bizKey，DONE 行按 upsert 语义复活）；任务办结/换人由同步器按前缀取消
        if (sent > 0) {
            scheduleNextUrge(task, config);
        }
        if (sent == 0) {
            return ActionResult.ok("SKIPPED: 待办无办理人，flowTask=" + flowTaskId);
        }
        return ActionResult.ok("URGE sent to " + sent + " handler(s), flowTask=" + flowTaskId);
    }

    /**
     * 续注册下一次催办（intervalSeconds 随配置携带；复核失败/达上限/无办理人时不续）
     */
    private void scheduleNextUrge(ScheduleTaskSnapshot task, JSONObject config) {
        try {
            int interval = config.getInt("intervalSeconds", 0);
            if (interval <= 0) {
                return;
            }
            Date next = new Date(System.currentTimeMillis() + interval * 1000L);
            scheduleTaskClient.registerOnce(task.getOrganizationId(), task.getSource(), task.getBizKey(), next,
                    ACTION_TYPE, config.toString(), null, null);
        } catch (Exception e) {
            log.warn("续注册重复催办失败（本轮催办已送达）bizKey={}", task.getBizKey(), e);
        }
    }

    private static Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }

    /**
     * 催办计数（非正确性敏感计数，get-set 窗口最坏多催一次，可接受）
     */
    private boolean tryCountUrge(Long flowTaskId, int maxTimes) {
        String key = URGE_COUNT_KEY + flowTaskId;
        try {
            Long count = redisService.setIncrement(key, 1, 30L * 24 * 3600);
            return count == null || count <= maxTimes;
        } catch (Exception e) {
            log.warn("催办计数失败（放行本次催办）taskId={}", flowTaskId, e);
            return true;
        }
    }

    private void cancelSelf(ScheduleTaskSnapshot task) {
        try {
            scheduleTaskClient.cancel(task.getOrganizationId(), task.getSource(), task.getBizKey());
        } catch (Exception e) {
            log.warn("取消重复催办调度任务失败 bizKey={}", task.getBizKey(), e);
        }
    }

    private JSONObject parseBiz(String bizContext) {
        if (StringUtils.isEmpty(bizContext)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(bizContext);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
