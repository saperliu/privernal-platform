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

package com.privernal.system.api;

import com.alibaba.fastjson2.JSONObject;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据变更事件的富载体（增量扩展）。
 * <p>
 * 旧版 {@link WebhookPublisher#publish(String, String, String, String, JSONObject)} 只带
 * after 快照；联动引擎（内部监听器）需要 before 快照（watchFields 对比 / EFFECTIVE 跃迁判定 /
 * DELETE 动作引用被删行）、来源标记、业务库名、操作人与稳定幂等键。这些字段只给内部消费者用，
 * <b>不改变对外 Webhook 请求体结构</b>——Webhook 扇出仍按旧口径只取 eventType/tableCode/bizId/data。
 * <p>
 * {@code eventId} 由服务层收敛点在发布时生成（§7.5.1 口径，如
 * {@code sha1(sourceTag|database|tableCode|dataId|写入毫秒)}），同一事件的多重投递拿到同一值，
 * 幂等键不能每次重新生成。
 */
public class WebhookEventData implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 事件类型，取值 {@code WebhookEventTypes} */
    private String eventType;
    /** 事件所属组织，必须显式传入（异步线程与机器调用没有会话上下文） */
    private String organizationId;
    /** 动态表编码（= 物理表名） */
    private String tableCode;
    /** 业务数据 ID */
    private String bizId;
    /** 组织业务库名，免登/异步链路重放上下文用（@DS("#database") 显式入参） */
    private String databaseName;
    /** after 快照（提交的数据；语义上接近落库后行，DEL 时为 null） */
    private JSONObject data;
    /** before 快照：写入前按主键读回的行；CREATE 为 null（§7.2.1 前后快照口径） */
    private JSONObject beforeData;
    /** 操作人 ID，可空（匿名/机器调用） */
    private String operatorId;
    /** 来源标记：NORMAL/OPENAPI/PUBLIC_LINK/IMPORT/LINKAGE（§7.5.3） */
    private String sourceTag;
    /** 级联深度：0=业务直接写入；联动动作产生的写入带上游深度+1（§7.5.3） */
    private int depth;
    /** 稳定幂等键，发布点生成（§7.5.1）；旧签名入口为 null 时由实现侧兜底生成 */
    private String eventId;
    /** 事件发生时间 */
    private Date occurredAt;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getTableCode() {
        return tableCode;
    }

    public void setTableCode(String tableCode) {
        this.tableCode = tableCode;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public JSONObject getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(JSONObject beforeData) {
        this.beforeData = beforeData;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getSourceTag() {
        return sourceTag;
    }

    public void setSourceTag(String sourceTag) {
        this.sourceTag = sourceTag;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Date getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt) {
        this.occurredAt = occurredAt;
    }
}
