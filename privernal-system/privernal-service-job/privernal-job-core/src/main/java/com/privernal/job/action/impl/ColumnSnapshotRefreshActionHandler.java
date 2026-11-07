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

package com.privernal.job.action.impl;

import com.privernal.dynamic.report.domain.DynamicDataset;
import com.privernal.dynamic.report.mapper.DynamicDatasetMapper;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.privernal.job.api.action.ActionResult;
import com.privernal.job.api.action.ScheduleAction;
import com.privernal.job.api.action.ScheduleActionHandler;
import com.privernal.job.api.action.TaskExecutionContext;
import com.privernal.job.api.dto.ScheduleTaskSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统动作：数据集列快照定期校验刷新（对 缺口的承接）。
 *
 * <p>表单列变更后数据集快照靠"保存时解析"跟不上，SYSTEM 任务 column-snapshot-refresh
 * （建议每日）遍历全部数据集调 refreshDataset（已保存图表引用被删字段时 refreshDataset
 * 报错——按设计不做静默兼容，逐条容错记数，最后汇总摘要）。</p>
 *
 * <p>前缀阻断已解除：refreshDataset 内部的 decorate 解析经 §8.3 第4项重载
 * （currentPrefix 先读 OrganizationContextHolder，调度线程执行前已装配组织）。</p>
 *
 * @author saperliu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduleAction("column-snapshot-refresh")
public class ColumnSnapshotRefreshActionHandler implements ScheduleActionHandler {

    private final DynamicDatasetMapper datasetMapper;

    private final IDynamicDatasetService datasetService;

    @Override
    public String actionType() {
        return "BEAN";
    }

    @Override
    public ActionResult doAction(ScheduleTaskSnapshot task, TaskExecutionContext context) {
        List<DynamicDataset> all = datasetMapper.selectList(null);
        int refreshed = 0;
        int failed = 0;
        for (DynamicDataset dataset : all) {
            try {
                datasetService.refreshDataset(dataset.getDatasetCode());
                refreshed++;
            } catch (Exception e) {
                // 图表引用已删字段等冲突：计数不中断（设计要求可读报错而非静默兼容）
                failed++;
                log.warn("[schedule] dataset snapshot refresh failed datasetCode={}",
                        dataset.getDatasetCode(), e);
            }
        }
        return ActionResult.ok("datasets=" + all.size() + " refreshed=" + refreshed + " failed=" + failed);
    }
}
