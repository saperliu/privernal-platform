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

package com.privernal.dynamic.advanced.engine.handler;

import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.engine.ActionConfig;
import com.privernal.dynamic.advanced.engine.ActionResult;
import com.privernal.dynamic.advanced.engine.ConditionEvaluator;
import com.privernal.dynamic.advanced.engine.InnerMessageSender;
import com.privernal.dynamic.advanced.engine.LinkageActionHandler;
import com.privernal.dynamic.advanced.engine.LinkageContext;
import com.privernal.dynamic.dba.utils.JsonValueCodec;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MSG_INNER 站内信动作（§7.4）：P0 默认通知动作，复用 ISystemMessageService 桥接端口。
 * 收件人=指定用户 / 人员字段（事件数据）；内容模板支持占位字段引用。
 */
@Slf4j
@Component
public class MsgInnerAction implements LinkageActionHandler {

    private static final Pattern TEMPLATE_REF = Pattern.compile("\\{\\{([A-Za-z0-9_]+)}}");

    @Autowired
    private ObjectProvider<InnerMessageSender> innerMessageSender;

    @Override
    public String type() {
        return "MSG_INNER";
    }

    @Override
    public ActionResult perform(ActionConfig actionConfig, LinkageContext ctx) {
        long begin = System.currentTimeMillis();
        JSONObject config = actionConfig.getConfig();
        InnerMessageSender sender = innerMessageSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("站内信桥接未部署（privernal-server 缺 InnerMessageSender 实现）");
        }
        List<String> receivers = resolveReceivers(config, ctx);
        if (receivers.isEmpty()) {
            throw new IllegalArgumentException("MSG_INNER 未解析到任何收件人");
        }
        String title = renderTemplate(config.getString("title"), ctx);
        String content = renderTemplate(config.getString("content"), ctx);
        int sent = sender.send(receivers, title, content);
        return ActionResult.success(type(), "站内信已发送给 " + sent + " 人", System.currentTimeMillis() - begin);
    }

    @Override
    public JSONObject dryRun(ActionConfig actionConfig, LinkageContext ctx) {
        JSONObject config = actionConfig.getConfig();
        JSONObject preview = new JSONObject();
        preview.put("receivers", resolveReceivers(config, ctx));
        preview.put("title", renderTemplate(config.getString("title"), ctx));
        preview.put("content", renderTemplate(config.getString("content"), ctx));
        return preview;
    }

    /** receiverType: USERS 固定用户列表 / FIELD 事件数据里的人员字段（数组或逗号分号分隔） */
    private List<String> resolveReceivers(JSONObject config, LinkageContext ctx) {
        List<String> receivers = new ArrayList<>();
        String receiverType = config.getString("receiverType");
        if ("FIELD".equals(receiverType)) {
            String fieldCode = config.getString("fieldCode");
            Object value = fieldCode == null ? null
                    : ConditionEvaluator.lookup(ctx.conditionData(), fieldCode);
            if (value instanceof JSONArray array) {
                for (int i = 0; i < array.size(); i++) {
                    addReceiver(receivers, array.getString(i));
                }
            } else if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    addReceiver(receivers, item == null ? null : String.valueOf(item));
                }
            } else if (value != null) {
                // 人员字段可能是统一信封 {"v":["u1","u2"]} 或裸数组，必须先拆成成员再逐个加入；
                // 否则会把整段 JSON 当成一个 userId 传下去，导致通知静默发不出去
                String text = String.valueOf(value);
                List<String> members = JsonValueCodec.values(text);
                if (members.size() > 1 || JsonValueCodec.isEnvelope(text)) {
                    for (String item : members) {
                        addReceiver(receivers, item);
                    }
                } else if (text.contains(",")) {
                    for (String part : text.split("[,;，；]")) {
                        addReceiver(receivers, part);
                    }
                } else {
                    addReceiver(receivers, text);
                }
            }
        } else {
            JSONArray userIds = config.getJSONArray("userIds");
            if (userIds != null) {
                for (int i = 0; i < userIds.size(); i++) {
                    addReceiver(receivers, userIds.getString(i));
                }
            }
        }
        return receivers;
    }

    private void addReceiver(List<String> receivers, String userId) {
        if (StringUtils.isNotEmpty(userId) && !receivers.contains(userId.trim())) {
            receivers.add(userId.trim());
        }
    }

    /** 模板占位渲染：{{字段编码}} 取事件数据（条件数据同一口径），未知占位保留原文 */
    public String renderTemplate(String template, LinkageContext ctx) {
        if (StringUtils.isEmpty(template)) {
            return template;
        }
        Map<String, Object> data = ctx.conditionData();
        Matcher matcher = TEMPLATE_REF.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String field = matcher.group(1);
            Object value = data == null ? null : ConditionEvaluator.lookup(data, field);
            String rendered = value == null ? matcher.group(0) : displayOf(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(rendered));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 字段值 → 站内信里可读的文本。
     * 只对统一信封 / 裸数组形态做多值展开与标签回译（优先用信封自带的 src 缓存，取不到就显示原值），
     * 普通文本原样返回，避免把"备注：a,b"这类内容误改写成顿号。
     */
    private String displayOf(Object value) {
        String text = String.valueOf(value);
        if (JsonValueCodec.isEnvelope(text) || text.trim().startsWith("[")) {
            String formatted = JsonValueCodec.formatForDisplay(text, JsonValueCodec.cachedLabels(text));
            return formatted.isEmpty() ? text : formatted;
        }
        return text;
    }
}
