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

package com.privernal.advanced.sms;

import cn.hutool.core.util.DesensitizedUtil;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.dynamic.advanced.engine.ActionConfig;
import com.privernal.dynamic.advanced.engine.ActionResult;
import com.privernal.dynamic.advanced.engine.ConditionEvaluator;
import com.privernal.dynamic.advanced.engine.LinkageActionHandler;
import com.privernal.dynamic.advanced.engine.LinkageContext;
import com.privernal.dynamic.advanced.mapper.DynamicLinkageQueryMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsResponse;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import darabonba.core.client.ClientOverrideConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MSG_SMS 短信动作（T4；D 决策：实现）。
 * <p>
 * 模板预登记制：发号凭据（AK/SK、签名）与模板码由平台管理员登记在
 * `system_default_config` 的 `dynamic.advanced.sms.config`（JSON：
 * {accessKeyId, accessKeySecret, signName, endpoint?, templates:{&lt;key&gt;:{templateCode}}}），
 * 规则动作里只引用 templateKey + 收件人 + 参数映射——防滥用（§10）+ 凭据不进规则 JSON。
 * 每动作每次调用即一次发送；日限流（500/规则/动作/日）由后续全局限流补（先记日志可审计）。
 * <p>
 * 本类放 privernal-server：dysmsapi SDK 只在 server 依赖里，而动作 SPI 由
 * ScheduleActionRegistry 从同一 ApplicationContext 收集，server 与 core 同上下文。
 */
@Slf4j
@Component
public class MsgSmsAction implements LinkageActionHandler {

    /** 预登记配置的 config_key */
    public static final String SMS_CONFIG_KEY = "dynamic.advanced.sms.config";

    @Autowired
    private DynamicLinkageQueryMapper linkageQueryMapper;

    private final Map<String, AsyncClient> clients = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "MSG_SMS";
    }

    @Override
    public com.privernal.dynamic.advanced.engine.ActionResult perform(ActionConfig actionConfig, LinkageContext ctx) {
        long begin = System.currentTimeMillis();
        JSONObject config = actionConfig.getConfig();
        String templateKey = config.getString("templateKey");
        JSONObject smsConfig = loadSmsConfig();
        JSONObject template = smsConfig == null || smsConfig.getJSONObject("templates") == null
                ? null : smsConfig.getJSONObject("templates").getJSONObject(templateKey);
        if (template == null || StringUtils.isEmpty(template.getString("templateCode"))) {
            throw new IllegalArgumentException("短信模板未预登记或不存在：" + templateKey);
        }
        List<String> phones = resolvePhones(config, ctx);
        if (phones.isEmpty()) {
            throw new IllegalArgumentException("MSG_SMS 未解析到任何手机号");
        }
        JSONObject params = buildParams(config.getJSONObject("templateParam"), ctx);
        String paramJson = params.toJSONString();
        int sent = 0;
        List<String> errors = new ArrayList<>();
        for (String phone : phones) {
            try {
                sendOnce(smsConfig, template.getString("templateCode"), phone, paramJson);
                sent++;
            } catch (Exception e) {
                log.warn("短信发送失败 phone={} error={}", DesensitizedUtil.mobilePhone(phone), e.getMessage());
                errors.add(phone + ":" + e.getMessage());
            }
        }
        if (sent == 0) {
            return com.privernal.dynamic.advanced.engine.ActionResult.failed(type(),
                    "全部发送失败：" + String.join("; ", errors), System.currentTimeMillis() - begin, 1);
        }
        String summary = "短信已发送 " + sent + "/" + phones.size()
                + (errors.isEmpty() ? "" : "，失败：" + String.join("; ", errors));
        return com.privernal.dynamic.advanced.engine.ActionResult.success(type(), summary, System.currentTimeMillis() - begin);
    }

    @Override
    public com.alibaba.fastjson2.JSONObject dryRun(ActionConfig actionConfig, LinkageContext ctx) {
        JSONObject config = actionConfig.getConfig();
        com.alibaba.fastjson2.JSONObject preview = new com.alibaba.fastjson2.JSONObject();
        preview.put("phones", resolvePhones(config, ctx));
        preview.put("templateKey", config.getString("templateKey"));
        preview.put("templateParam", buildParams(config.getJSONObject("templateParam"), ctx));
        return preview;
    }

    private void sendOnce(JSONObject smsConfig, String templateCode, String phone, String paramJson) throws Exception {
        String accessKeyId = String.valueOf(smsConfig.get("accessKeyId"));
        String accessKeySecret = String.valueOf(smsConfig.get("accessKeySecret"));
        String region = String.valueOf(smsConfig.getOrDefault("region", "cn-hangzhou"));
        String endpoint = String.valueOf(smsConfig.getOrDefault("endpoint", "dysmsapi.aliyuncs.com"));
        String signName = String.valueOf(smsConfig.get("signName"));
        if (StringUtils.isEmpty(accessKeyId) || StringUtils.isEmpty(accessKeySecret)) {
            throw new IllegalStateException("短信凭据未登记（dynamic.advanced.sms.config 缺 accessKeyId/accessKeySecret）");
        }
        com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest request =
                com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest.builder()
                        .phoneNumbers(phone)
                        .signName(signName)
                        .templateCode(templateCode)
                        .templateParam(paramJson)
                        .build();
        darabonba.core.client.ClientOverrideConfiguration override =
                darabonba.core.client.ClientOverrideConfiguration.create()
                        .setEndpointOverride(endpoint)
                        .setConnectTimeout(Duration.ofSeconds(10));
        AsyncClient client = AsyncClient.builder()
                .region(region)
                .credentialsProvider(
                        com.aliyun.auth.credentials.provider.StaticCredentialProvider.create(
                                com.aliyun.auth.credentials.Credential.builder()
                                        .accessKeyId(accessKeyId)
                                        .accessKeySecret(accessKeySecret)
                                        .build()))
                .overrideConfiguration(override)
                .build();
        try {
            CompletableFuture<SendSmsResponse> future = client.sendSms(request);
            SendSmsResponse resp = future.get();
            String code = resp.getBody() == null ? null : resp.getBody().getCode();
            if (!"OK".equals(code)) {
                throw new IllegalStateException("短信网关返回 " + code);
            }
        } finally {
            client.close();
        }
    }

    private List<String> resolvePhones(JSONObject config, LinkageContext ctx) {
        List<String> phones = new ArrayList<>();
        String receiverType = config.getString("receiverType");
        if ("FIELD".equals(receiverType)) {
            Object value = ConditionEvaluator.lookup(ctx.conditionData(), config.getString("fieldCode"));
            addPhone(phones, value);
        } else {
            Object list = config.get("phones");
            if (list instanceof JSONArray array) {
                for (int i = 0; i < array.size(); i++) {
                    addPhone(phones, array.getString(i));
                }
            } else if (list != null) {
                addPhone(phones, String.valueOf(list));
            }
        }
        return phones;
    }

    private void addPhone(List<String> phones, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).replaceAll("\\s", "");
        if (text.isEmpty()) {
            return;
        }
        for (String part : text.split("[,;，；]")) {
            if (part.matches("^1\\d{10}$") && !phones.contains(part)) {
                phones.add(part);
            }
        }
    }

    /** 模板参数：{key: 常量 or {{field}}} */
    private JSONObject buildParams(JSONObject mapping, LinkageContext ctx) {
        JSONObject params = new JSONObject(new LinkedHashMap<>());
        if (mapping == null) {
            return params;
        }
        Map<String, Object> data = ctx.conditionData();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            Object raw = entry.getValue();
            if (raw instanceof String text && text.trim().startsWith("{{") && text.trim().endsWith("}}")) {
                String field = text.trim().substring(2, text.trim().length() - 2).trim();
                Object v = ConditionEvaluator.lookup(data, field);
                params.put(entry.getKey(), v == null ? "" : String.valueOf(v));
            } else {
                params.put(entry.getKey(), raw == null ? "" : String.valueOf(raw));
            }
        }
        return params;
    }

    /** 读预登记配置（master 库 system_default_config；30s 内存缓存由 kill switch 同款策略，此处直读简化） */
    private JSONObject loadSmsConfig() {
        Boolean oldIgnore = OrganizationContextHolder.isIgnore();
        try {
            OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
            DynamicDataSourceContextHolder.push("master");
            List<Map<String, Object>> rows = linkageQueryMapper.selectRows(
                    "SELECT config_value AS configValue FROM system_default_config "
                            + "WHERE config_key = 'dynamic.advanced.sms.config' LIMIT 1",
                    Map.of());
            if (rows.isEmpty() || rows.get(0).get("configValue") == null) {
                return null;
            }
            Object v = rows.get(0).get("configValue");
            return JSON.parseObject(String.valueOf(v));
        } finally {
            DynamicDataSourceContextHolder.poll();
            if (oldIgnore == null) {
                OrganizationContextHolder.clear();
            } else {
                OrganizationContextHolder.setIgnore(Boolean.TRUE.equals(oldIgnore));
            }
        }
    }
}
