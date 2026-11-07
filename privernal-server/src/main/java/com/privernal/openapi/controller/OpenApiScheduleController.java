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

package com.privernal.openapi.controller;

import com.privernal.common.log.enums.OperationType;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.job.api.ScheduleTaskClient;
import com.privernal.job.domain.SystemScheduleTask;
import com.privernal.job.service.ScheduleTaskService;
import com.privernal.openapi.support.OpenApiContext;
import com.privernal.system.domain.SystemOpenapiCredential;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

/**
 * 第三方定时任务注册通道（P1c）。
 *
 * <p>路径 /openapi/schedule/**，复用 OpenApiAuthFilter 既有验签/时间戳/nonce/限流/流水；
 * scope：写（注册/改期/取消）需 schedule:write，查询需 schedule:read（未知 action 已由
 * 过滤器兜底拒绝）。组织取自凭证而非请求体——第三方永远只能操作自己组织的任务。</p>
 *
 * <p>动作域收紧（§11）：OPENAPI 来源只允许 HTTP_CALL/MESSAGE_NOTIFY，
 * GROOVY_SCRIPT/BEAN/FORM_DATA_OP 在服务层被拒绝——schedule:write 等于把
 * "未来任意时刻执行平台代码"卖给第三方，脚本与 Bean 能力绝不对开放。</p>
 *
 * @author saperliu
 */
@Slf4j
@RestController
@RequestMapping("/openapi/schedule")
@RequiredArgsConstructor
public class OpenApiScheduleController {

    private static final String SOURCE = "OPENAPI";

    private final ScheduleTaskClient scheduleTaskClient;
    private final ScheduleTaskService scheduleTaskService;

    /** 注册任务（ONCE 或 CRON）。bizKey 必填且在本凭证组织内唯一 */
    @Log(title = "开放接口定时任务", operationType = OperationType.INSERT)
    @PostMapping("/task")
    public CommonResult<Object> register(@RequestBody JSONObject body) {
        String orgId = currentOrg();
        String bizKey = body.getString("bizKey");
        if (bizKey == null || bizKey.isBlank()) {
            return CommonResult.fail("bizKey 必填");
        }
        String triggerType = body.getString("triggerType");
        SystemScheduleTask form = new SystemScheduleTask();
        form.setOrganizationId(orgId);
        form.setSource(SOURCE);
        form.setBizKey(bizKey);
        form.setTaskCode(body.getString("taskCode"));
        form.setTitle(body.getString("title") == null ? "OPENAPI:" + bizKey : body.getString("title"));
        form.setTriggerType(triggerType);
        form.setCronExpr(body.getString("cron"));
        form.setActionType(body.getString("actionType"));
        form.setActionConfig(body.getString("actionConfig"));
        Map<String, Object> saved = scheduleTaskService.save(form);
        return CommonResult.ok(saved);
    }

    /** 改期（仅 READY/PAUSED 生效；TRIGGERED/RUNNING 返回延迟生效语义） */
    @Log(title = "开放接口定时任务", operationType = OperationType.UPDATE)
    @PutMapping("/task/{bizKey}")
    public CommonResult<Object> reschedule(@PathVariable("bizKey") String bizKey,
                                           @RequestBody JSONObject body) {
        String orgId = currentOrg();
        String fireTimeStr = body.getString("fireTime");
        if (fireTimeStr == null || fireTimeStr.isBlank()) {
            return CommonResult.fail("fireTime 必填（yyyy-MM-dd HH:mm:ss[.SSS]）");
        }
        Date fireTime = parseDate(fireTimeStr);
        if (fireTime == null) {
            return CommonResult.fail("fireTime 格式非法");
        }
        boolean ok = scheduleTaskClient.reschedule(orgId, SOURCE, bizKey, fireTime);
        return CommonResult.ok(Map.of("applied", ok,
                "message", ok ? "已改期" : "任务不存在或正在执行（延迟生效，请稍后重试）"));
    }

    /** 取消（软取消；同 bizKey 再注册走复活分支，§6.1 注4） */
    @Log(title = "开放接口定时任务", operationType = OperationType.DELETE)
    @DeleteMapping("/task/{bizKey}")
    public CommonResult<Object> cancel(@PathVariable("bizKey") String bizKey) {
        boolean ok = scheduleTaskClient.cancel(currentOrg(), SOURCE, bizKey);
        return CommonResult.ok(Map.of("applied", ok, "message", ok ? "已取消" : "任务不存在或已是终态"));
    }

    /** 查询任务状态 */
    @GetMapping("/task/{bizKey}")
    public CommonResult<Object> query(@PathVariable("bizKey") String bizKey) {
        Map<String, Object> view = scheduleTaskClient.selectByBizKey(currentOrg(), SOURCE, bizKey);
        return CommonResult.ok(view);
    }

    /** 组织永远取凭证归属——防第三方伪造 organization_id 操作他人任务 */
    private String currentOrg() {
        SystemOpenapiCredential credential = OpenApiContext.credential();
        if (credential == null || credential.getOrganizationId() == null) {
            throw new IllegalStateException("OpenAPI 上下文缺少凭证组织");
        }
        return credential.getOrganizationId();
    }

    private Date parseDate(String value) {
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss"}) {
            try {
                return new java.text.SimpleDateFormat(pattern).parse(value);
            } catch (Exception ignored) {
                // 尝试下一个格式
            }
        }
        return null;
    }
}
