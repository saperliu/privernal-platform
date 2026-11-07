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
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.dynamic.advanced.domain.DynamicFormIoConfig;
import com.privernal.dynamic.advanced.engine.DataWriteAttributes;
import com.privernal.dynamic.advanced.engine.LinkageDispatcher;
import com.privernal.dynamic.advanced.engine.LinkageEvent;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.mapper.DynamicFormIoConfigMapper;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.mapper.DynamicFormMapper;
import com.privernal.system.api.WebhookEventData;
import com.privernal.system.api.WebhookInternalListener;
import com.privernal.system.constant.WebhookEventTypes;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 联动事件订阅器（§3.3/§7.2.1）：挂 {@code WebhookEventService.publish} 的同一 afterCommit
 * 时机（先于、独立于 webhook 订阅扇出），把 dynamic.data.* 事件转成规则事件分发。
 * <p>
 * 不读 outbox 行（那是有订阅才落的投递队列）；导入路径按 io_config.trigger_mode 决定是否
 * 分发（默认 SUPPRESS，§4.2）。
 */
@Slf4j
@Component
public class DataChangeEventSubscriber implements WebhookInternalListener {

    @Autowired
    private LinkageDispatcher linkageDispatcher;

    @Autowired
    private LinkageSupport linkageSupport;

    @Autowired
    private DynamicFormIoConfigMapper ioConfigMapper;

    @Autowired
    private DynamicFormMapper dynamicFormMapper;

    @Autowired
    private com.privernal.dynamic.advanced.engine.AdvancedKillSwitch killSwitch;

    @Override
    public void onEventCommitted(WebhookEventData event) {
        try {
            if (!killSwitch.linkageEnabled()) {
                log.info("联动能力已被平台关停（kill switch），跳过事件分发 tableCode={}", event.getTableCode());
                return;
            }
            String linkEventType = mapEventType(event.getEventType());
            if (linkEventType == null || StringUtils.isEmpty(event.getOrganizationId())) {
                return;
            }
            if (DataWriteAttributes.SOURCE_IMPORT.equals(event.getSourceTag())
                    && importSuppressed(event.getTableCode())) {
                log.info("导入路径联动抑制（io_config.trigger_mode=SUPPRESS）tableCode={} bizId={}",
                        event.getTableCode(), event.getBizId());
                return;
            }
            LinkageEvent linkageEvent = LinkageEvent.of(linkEventType, event.getTableCode(), event.getBizId(),
                    toMap(event.getBeforeData()), mergeAfter(event));
            linkageEvent.setOrganizationId(event.getOrganizationId());
            linkageEvent.setDatabaseName(StringUtils.isEmpty(event.getDatabaseName())
                    ? linkageSupport.resolveDatabase(event.getOrganizationId()) : event.getDatabaseName());
            linkageEvent.setSourceTag(StringUtils.isEmpty(event.getSourceTag())
                    ? DataWriteAttributes.SOURCE_NORMAL : event.getSourceTag());
            linkageEvent.setDepth(event.getDepth());
            linkageEvent.setEventId(event.getEventId());
            linkageEvent.setOperatorId(event.getOperatorId());
            linkageEvent.setOccurredAt(event.getOccurredAt());
            linkageDispatcher.dispatchAsync(linkageEvent);
        } catch (Exception e) {
            // 监听器异常只记日志：不能影响 webhook 扇出，也不影响已提交业务（WebhookEventService 同约定）
            log.error("联动事件订阅处理失败 eventType={} tableCode={} bizId={}",
                    event.getEventType(), event.getTableCode(), event.getBizId(), e);
        }
    }

    private String mapEventType(String webhookEventType) {
        if (WebhookEventTypes.DATA_CREATED.equals(webhookEventType)) {
            return "CREATE";
        }
        if (WebhookEventTypes.DATA_UPDATED.equals(webhookEventType)) {
            return "UPDATE";
        }
        if (WebhookEventTypes.DATA_DELETED.equals(webhookEventType)) {
            return "DELETE";
        }
        return null;
    }

    /** 导入默认不发联动（§4.2）：io_config 未配置或 SUPPRESS 都抑制 */
    private boolean importSuppressed(String tableCode) {
        DynamicForm form = dynamicFormMapper.selectOne(
                new LambdaPrivernalWrapper<DynamicForm>().eq(DynamicForm::getTableCode, tableCode));
        if (form == null) {
            return true;
        }
        DynamicFormIoConfig config = ioConfigMapper.selectOne(
                Wrappers.<DynamicFormIoConfig>lambdaQuery().eq(DynamicFormIoConfig::getFormId, form.getId()));
        return config == null || !"FIRE".equals(config.getTriggerMode());
    }

    private Map<String, Object> toMap(JSONObject json) {
        return json == null ? null : json;
    }

    /**
     * after 视图 = before 全行 ∪ 本次提交键（提交值优先）。部分更新（只改一列）的提交 Map
     * 不带其余字段，条件/模板若直接读提交 Map 会看到"字段无值"——按 §7.2.1
     * "基于本次写入前的读取值"口径合并出写入后的完整行视图。
     */
    private Map<String, Object> mergeAfter(WebhookEventData event) {
        Map<String, Object> submitted = toMap(event.getData());
        if (submitted == null) {
            return toMap(event.getBeforeData());
        }
        Map<String, Object> before = toMap(event.getBeforeData());
        if (before == null) {
            return submitted;
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(before);
        merged.putAll(submitted);
        return merged;
    }
}
