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

package com.privernal.dynamic.advanced.engine;

import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * 联动引擎内部的规则级事件（规则视角的事件载荷，event_type 已归一为 CREATE/UPDATE/DELETE/EFFECTIVE）。
 * <p>
 * depth/event_id/organizationId/databaseName/sourceTag 全部随对象显式传参，
 * 序列化进任何异步任务载荷——不依赖 ThreadLocal（ASYNC 换线程即丢，§7.5.3）。
 */
@Data
public class LinkageEvent {

    /** CREATE/UPDATE/DELETE/EFFECTIVE */
    private String eventType;

    /** 逻辑表编码（= 物理表名） */
    private String tableCode;

    private String formId;

    private String dataId;

    /** before 快照（列名为下划线口径；CREATE 为 null；DELETE 时是被删行） */
    private Map<String, Object> before;

    /** after 快照（列名为下划线口径；DELETE 为 null） */
    private Map<String, Object> after;

    private String organizationId;

    private String databaseName;

    /** NORMAL/OPENAPI/PUBLIC_LINK/IMPORT/LINKAGE */
    private String sourceTag;

    /** 级联深度：业务直接写入=0，联动动作产生的写入=上游+1 */
    private int depth;

    /** 稳定幂等键（§7.5.1 发布点生成） */
    private String eventId;

    private String operatorId;

    private Date occurredAt;

    /** TIMER/DATE_FIELD 的计划触发时刻（参与 event_id 与日志 fire_time，§7.5.1） */
    private Date fireTime;

    public static LinkageEvent of(String eventType, String tableCode, String dataId,
                                  Map<String, Object> before, Map<String, Object> after) {
        LinkageEvent event = new LinkageEvent();
        event.setEventType(eventType);
        event.setTableCode(tableCode);
        event.setDataId(dataId);
        event.setBefore(before);
        event.setAfter(after);
        return event;
    }
}
