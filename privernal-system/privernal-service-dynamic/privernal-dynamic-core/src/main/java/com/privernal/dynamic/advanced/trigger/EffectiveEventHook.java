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

package com.privernal.dynamic.advanced.trigger;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.engine.DataWriteAttributes;
import com.privernal.dynamic.advanced.engine.LinkageDispatcher;
import com.privernal.dynamic.advanced.engine.LinkageEvent;
import com.privernal.dynamic.advanced.engine.LinkageIdemUtils;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * EFFECTIVE 事件钩子（§7.2）：流程完成 status→1 的状态跃迁判定 + 规则分发。
 * <p>
 * 挂点在 {@code RemoteDynamicServiceImpl#updateBusinessStatus}（syncBusinessStatus 是唯一漏斗，
 * 14 处调用全经此汇入，无旁路）。上游会重复回写同状态且无幂等 → 只有 before.status != 1
 * 才算 EFFECTIVE，否则通知会重复发。
 * <p>
 * 口径：EFFECTIVE 只对流程表单的状态回写链路触发（本钩子只被流程回写调用，天然满足；
 * 无流程表单"新建即已完成"不在此列，需要该语义应配 CREATE 规则）。
 */
@Slf4j
@Component
public class EffectiveEventHook {

    @Autowired
    private LinkageDispatcher linkageDispatcher;

    @Autowired
    private LinkageSupport linkageSupport;

    /**
     * 状态回写成功后调用。任何异常只记日志：联动失败不能影响流程状态回写的结果。
     *
     * @param database 业务库名（回写方会话解析，显式传入）
     * @param before   回写前的数据行（列名下划线口径，可空=行不存在）
     * @param after    回写后的数据行
     */
    public void fireOnFinish(String database, String organizationId, String tableCode, String dataId,
                             Map<String, Object> before, Map<String, Object> after) {
        try {
            if (before == null || after == null || StringUtils.isEmpty(organizationId)) {
                return;
            }
            String fromStatus = normalizeStatus(before.get("status"));
            String toStatus = normalizeStatus(after.get("status"));
            if (!"1".equals(toStatus) || "1".equals(fromStatus)) {
                // 非完成态回写、或同状态重复回写：都不是跃迁，不产生 EFFECTIVE（§7.2）
                return;
            }
            String writeTime = after.get("updated_time") == null
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    : String.valueOf(after.get("updated_time"));
            LinkageEvent event = LinkageEvent.of("EFFECTIVE", tableCode, dataId, before, after);
            event.setOrganizationId(organizationId);
            event.setDatabaseName(StringUtils.isEmpty(database)
                    ? linkageSupport.resolveDatabase(organizationId) : database);
            event.setSourceTag(DataWriteAttributes.SOURCE_NORMAL);
            event.setDepth(0);
            event.setEventId(LinkageIdemUtils.effectiveEventId(organizationId, tableCode, dataId,
                    fromStatus, toStatus, writeTime));
            event.setOccurredAt(new Date());
            linkageDispatcher.dispatchAsync(event);
        } catch (Exception e) {
            log.error("EFFECTIVE 联动钩子失败 tableCode={} dataId={}", tableCode, dataId, e);
        }
    }

    private String normalizeStatus(Object status) {
        if (status == null) {
            return null;
        }
        String text = String.valueOf(status).trim();
        if (text.contains(".")) {
            // JDBC 可能回读 1.0 形式的数值
            text = text.substring(0, text.indexOf('.'));
        }
        return text;
    }
}
