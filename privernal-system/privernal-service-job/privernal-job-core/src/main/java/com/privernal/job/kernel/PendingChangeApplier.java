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

package com.privernal.job.kernel;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.mapper.ScheduleTaskCoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 在途变更应用器（task-upgrade P1-4）。
 *
 * <p>任务处于 TRIGGERED/RUNNING 时编辑配置或启停，不直接改行（§7.5），而是登记到
 * {@code pending_change}；本轮回写完成、行回到 READY/PAUSED 后由此应用并清空。
 * 此前只回显"下一轮生效"却没有任何落地机制，改动会静默丢失。</p>
 *
 * <p>调用点：执行链回写之后、对账回收之后、池满回退之后——即"行刚从在途状态落回"的时刻。
 * 重复调用安全（无暂存即返回）。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
public class PendingChangeApplier {

    @Autowired
    private ScheduleTaskCoreMapper coreMapper;

    @Autowired
    private TriggerCalculator calculator;

    /**
     * 应用一行暂存变更。
     *
     * @return 是否真的应用了（false 表示无暂存、仍在途、或行状态已变）
     */
    public boolean apply(String taskId) {
        SystemScheduleTask row = coreMapper.selectById(taskId);
        if (row == null || isBlank(row.getPendingChange())) {
            return false;
        }
        if (!ScheduleConstants.STATUS_READY.equals(row.getStatus())
                && !ScheduleConstants.STATUS_PAUSED.equals(row.getStatus())
                && !ScheduleConstants.STATUS_DONE.equals(row.getStatus())) {
            // 仍在途（或被别的轮次重新抢占）——下一轮回写时再应用
            return false;
        }
        SystemScheduleTask pending;
        try {
            pending = JSONUtil.toBean(row.getPendingChange(), SystemScheduleTask.class);
        } catch (Exception e) {
            log.warn("[schedule] pending_change 解析失败，丢弃 taskId={} raw={}", taskId, row.getPendingChange(), e);
            coreMapper.clearPendingChange(taskId);
            return false;
        }
        boolean applied = false;
        if (!isBlank(pending.getTriggerType())) {
            // 配置面变更：与 save 同一套合并口径，upsertOverwrite 里带 pending_change = NULL
            applyConfig(pending, row, row.getUpdatedBy());
            applied = coreMapper.upsertOverwrite(row) > 0;
        }
        String wantedStatus = pending.getStatus();
        if (!isBlank(wantedStatus) && !wantedStatus.equals(row.getStatus())) {
            Date next = null;
            if (ScheduleConstants.STATUS_READY.equals(wantedStatus)) {
                next = calculator.buildInitialNext(row);
            }
            coreMapper.changeStatus(row.getId(), wantedStatus, next);
            coreMapper.clearPendingChange(row.getId());
            applied = true;
        }
        if (!applied && ScheduleConstants.STATUS_DONE.equals(row.getStatus())) {
            // 一次性任务已终态：配置类暂存不再有落地时机（不擅自复活任务），丢弃并留痕
            coreMapper.clearPendingChange(row.getId());
            log.info("[schedule] 任务已终态，丢弃登记的在途变更 taskId={}", row.getId());
        }
        if (applied) {
            log.info("[schedule] 在途变更已应用 taskId={} newStatus={}", row.getId(),
                    isBlank(wantedStatus) ? row.getStatus() : wantedStatus);
        }
        return applied;
    }

    /** 配置面合并（save 与在途应用共用同一字段集，避免两处漂移） */
    public void applyConfig(SystemScheduleTask form, SystemScheduleTask existing, String operator) {
        existing.setTitle(form.getTitle());
        existing.setTriggerType(form.getTriggerType());
        existing.setCronExpr(form.getCronExpr());
        existing.setIntervalSeconds(form.getIntervalSeconds());
        existing.setPeriodSeconds(form.getPeriodSeconds());
        existing.setJitterSeconds(form.getJitterSeconds() == null ? 0 : form.getJitterSeconds());
        existing.setMisfirePolicy(form.getMisfirePolicy());
        // misfire_window_seconds 列 NOT NULL：编辑表单不携带该字段时保留库中原值，
        // 否则置空会在 upsert 时触发 SQLIntegrityConstraintViolationException
        existing.setMisfireWindowSeconds(form.getMisfireWindowSeconds() == null
                ? existing.getMisfireWindowSeconds() : form.getMisfireWindowSeconds());
        existing.setConcurrentPolicy(form.getConcurrentPolicy());
        existing.setTimeoutSeconds(form.getTimeoutSeconds() == null
                ? existing.getTimeoutSeconds() : form.getTimeoutSeconds());
        existing.setMaxRetry(form.getMaxRetry() == null ? existing.getMaxRetry() : form.getMaxRetry());
        existing.setActionType(form.getActionType());
        existing.setActionConfig(form.getActionConfig());
        existing.setBizContext(form.getBizContext());
        // database_name 列 NOT NULL（默认 master）：显式置空会压过列默认值直接报约束违例
        existing.setDatabaseName(form.getDatabaseName() == null
                ? existing.getDatabaseName() : form.getDatabaseName());
        existing.setRemark(form.getRemark());
        existing.setUpdatedBy(operator);
        // ONCE 编辑可改触发时刻；周期任务按新表达式从现在重算
        existing.setNextFireTime(ScheduleConstants.TRIGGER_ONCE.equals(form.getTriggerType())
                ? form.getNextFireTime() : calculator.buildInitialNext(existing));
        existing.setPendingNextFireTime(null);
    }

    /** 启停登记到已有暂存（若有）之上，避免"改完配置再点暂停"把配置变更冲掉 */
    public static String withStatus(final String existingJson, final String status) {
        JSONObject obj = isBlank(existingJson) ? new JSONObject() : JSONUtil.parseObj(existingJson);
        obj.set("status", status);
        return obj.toString();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
