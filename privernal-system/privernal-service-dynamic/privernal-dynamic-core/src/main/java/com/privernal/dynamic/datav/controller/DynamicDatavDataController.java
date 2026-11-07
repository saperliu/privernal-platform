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

package com.privernal.dynamic.datav.controller;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.service.DatavScreenAccess;
import com.privernal.dynamic.datav.service.DynamicDatavDataService;
import com.privernal.dynamic.datav.service.IDynamicDatavScreenService;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据大屏运行态取数 Controller
 * <p>
 * 与 /back/report/data/preview 的分工是有意的：预览接受任意未保存的图表配置，所以要
 * report:dataset:query；这里只接受「已落库大屏里的组件 id」，配置来自库里而非请求里，
 * 因此用大屏权限收口。设计态改配置时的实时预览仍走 preview 接口，这里不重复开一个
 * 「可以传任意 config」的口子——那等于把大屏权限变成数据集权限的旁路。
 * <p>
 * 大屏编码不是访问凭据：查不到、越权都按同一句「大屏不存在」回，避免用报错差异枚举别人的屏。
 *
 * @author saperliu
 */
@Tag(name = "数据大屏取数接口")
@RestController
@Slf4j
@RequestMapping("/back/datav/data")
public class DynamicDatavDataController extends BaseController {

    @Autowired
    private IDynamicDatavScreenService screenService;

    @Autowired
    private DynamicDatavDataService datavDataService;

    /**
     * 单组件取数
     */
    @Operation(summary = "大屏组件取数", description = "body: {screenCode, widgetId, params?, drillLevel?}")
    @PreAuthorize(hasPermi = "datav:screen:query")
    @PostMapping("/query")
    public CommonResult<Object> query(@RequestBody Map<String, Object> body) {
        String widgetId = StringUtils.trimToEmpty((String) body.get("widgetId"));
        if (StringUtils.isBlank(widgetId)) {
            return CommonResult.fail("widgetId 不能为空");
        }
        DynamicDatavScreen screen = requireScreen((String) body.get("screenCode"));
        JSONObject params = paramsOf(body);
        String database = SecurityUtils.getDatabaseName();
        return CommonResult.ok(datavDataService.queryWidget(database, screen, widgetId, params,
                levelOf(body.get("drillLevel"))));
    }

    /**
     * 整屏批量取数：预览与投放首屏用它，逐组件结果各自带成功标志
     */
    @Operation(summary = "整屏批量取数", description = "body: {screenCode, params?, drills?}，drills 为 widgetId → 下钻层级")
    @PreAuthorize(hasPermi = "datav:screen:query")
    @PostMapping("/batch")
    public CommonResult<Object> batch(@RequestBody Map<String, Object> body) {
        DynamicDatavScreen screen = requireScreen((String) body.get("screenCode"));
        JSONObject params = paramsOf(body);
        String database = SecurityUtils.getDatabaseName();
        return CommonResult.ok(datavDataService.queryScreen(database, screen, params, drillsOf(body)));
    }

    private DynamicDatavScreen requireScreen(String rawScreenCode) {
        String screenCode = StringUtils.trimToEmpty(rawScreenCode);
        DynamicDatavScreen screen = StringUtils.isBlank(screenCode)
                ? null : screenService.findByCode(screenCode);
        // 查不到与无权限同一句话：数据权限切面把越权行筛成 null，区分两种 null 就能被用来枚举别人的屏
        if (screen == null || screen.getSchemaJson() == null) {
            throw new ServiceException("大屏不存在或无权访问");
        }
        // 有模块权限不等于能取这块屏：按自动菜单那条 datav:screen:view:<code> 逐块收口（M7.4）
        DatavScreenAccess.requireViewable(screen);
        if (Integer.valueOf(1).equals(screen.getStatus())) {
            // 已发布的大屏只认发布快照：编辑草稿、自动保存都不会再影响线上投放（M2.11）。
            // 没有快照（历史数据在快照机制上线前发布）回落实时 schema，行为与旧版一致。
            String published = screenService.findPublishedSchemaJson(screenCode);
            if (StringUtils.isNotBlank(published)) {
                screen.setSchemaJson(JSONObject.parseObject(published));
            }
        }
        return screen;
    }

    /**
     * Spring 用 Jackson 绑定 body，嵌套对象是 LinkedHashMap，必须显式转 fastjson2 JSONObject
     */
    private JSONObject paramsOf(Map<String, Object> body) {
        Object params = body.get("params");
        return params == null ? new JSONObject() : JSONObject.from(params);
    }

    /** drills 由 Jackson 绑成 LinkedHashMap，非 Map 的脏输入直接当没有 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> drillsOf(Map<String, Object> body) {
        Object drills = body.get("drills");
        return drills instanceof Map ? (Map<String, Object>) drills : Map.of();
    }

    /** 层级只认非负整数；数字字符串容忍是因为前端状态可能从 URL 或 JSON 里带出来 */
    private static int levelOf(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (raw instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
