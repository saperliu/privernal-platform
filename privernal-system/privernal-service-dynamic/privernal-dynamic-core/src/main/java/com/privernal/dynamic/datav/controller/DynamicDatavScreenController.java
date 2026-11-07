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

import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.service.DatavScreenAccess;
import com.privernal.dynamic.datav.service.IDynamicDatavScreenService;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据大屏 Controller
 *
 * @author saperliu
 */
@Tag(name = "数据大屏相关接口")
@RestController
@Slf4j
@RequestMapping("/back/datav/screen")
public class DynamicDatavScreenController extends BaseController {

    @Autowired
    private IDynamicDatavScreenService screenService;

    /**
     * 保存大屏（编码为空即新增，同时落一个历史版本）
     */
    @Operation(summary = "保存大屏")
    @PreAuthorize(hasPermi = "datav:screen:add")
    @Log(title = "数据大屏", operationType = OperationType.UPDATE)
    @PostMapping("/save")
    public CommonResult<Object> save(@RequestBody DynamicDatavScreen screen) {
        return CommonResult.ok(screenService.saveDatavScreen(screen));
    }

    /**
     * 大屏列表（按应用，可不传；不含画布 JSON）
     */
    @Operation(summary = "大屏列表")
    @PreAuthorize(hasPermi = "datav:screen:list")
    @GetMapping("/list")
    public CommonResult<Object> list(@RequestParam(value = "appId", required = false) String appId) {
        return CommonResult.ok(screenService.listScreens(appId));
    }

    /**
     * 版本列表（不含画布 JSON）
     */
    @Operation(summary = "大屏版本列表")
    @PreAuthorize(hasPermi = "datav:screen:query")
    @GetMapping("/versions/{screenCode}")
    public CommonResult<Object> versions(@PathVariable("screenCode") String screenCode) {
        requireViewable(screenCode);
        return CommonResult.ok(screenService.listVersions(screenCode));
    }

    /**
     * 单个版本内容（含画布 JSON），版本对比的取数端点；列表页的版本抽屉选两个版本后各取一次
     */
    @Operation(summary = "大屏版本内容")
    @PreAuthorize(hasPermi = "datav:screen:query")
    @GetMapping("/version/{screenCode}/{versionNo}")
    public CommonResult<Object> versionDetail(@PathVariable("screenCode") String screenCode,
                                              @PathVariable("versionNo") Integer versionNo) {
        requireViewable(screenCode);
        return CommonResult.ok(screenService.findVersion(screenCode, versionNo));
    }

    /**
     * 大屏详情（含画布 JSON）。published=1 且大屏已发布时返回发布快照（预览/投放用），
     * 不带参数始终是实时草稿（设计器用）。
     */
    @Operation(summary = "大屏详情")
    @PreAuthorize(hasPermi = "datav:screen:query")
    @GetMapping("/{screenCode}")
    public CommonResult<Object> detail(@PathVariable("screenCode") String screenCode,
                                       @RequestParam(value = "published", required = false) Integer published) {
        DynamicDatavScreen screen = requireViewable(screenCode);
        if (Integer.valueOf(1).equals(published) && Integer.valueOf(1).equals(screen.getStatus())) {
            String snapshot = screenService.findPublishedSchemaJson(screenCode);
            if (StringUtils.isNotBlank(snapshot)) {
                screen.setSchemaJson(JSONObject.parseObject(snapshot));
            }
        }
        return CommonResult.ok(screen);
    }

    /**
     * 追加 AI 图表 widget（B8-M3-3b）：服务端读改写 schema_json（前端整包覆盖会让并发编辑
     * 互相丢组件），追加到画布空白区右侧顺排并落一次历史版本。权限沿用 datav:screen:add
     * （与保存大屏同一把钥匙）；datasetCode 必须是已落库数据集——AI 图表先走
     * /back/report/chat/save-chart 保存成报表，临时会话里没保存的图不给投屏。
     */
    @Operation(summary = "追加大屏图表组件",
            description = "body: {screenCode|screenName, datasetCode, reportType, reportConfig, title}")
    @PreAuthorize(hasPermi = "datav:screen:add")
    @Log(title = "数据大屏", operationType = OperationType.UPDATE)
    @PostMapping("/append-widget")
    public CommonResult<Object> appendWidget(@RequestBody java.util.Map<String, Object> body) {
        JSONObject reportConfig = body.get("reportConfig") == null ? null : JSONObject.from(body.get("reportConfig"));
        return CommonResult.ok(screenService.appendWidget(
                StringUtils.trimToEmpty((String) body.get("screenCode")),
                StringUtils.trimToEmpty((String) body.get("screenName")),
                StringUtils.trimToEmpty((String) body.get("datasetCode")),
                reportConfig,
                StringUtils.trimToEmpty((String) body.get("reportType")),
                StringUtils.trimToEmpty((String) body.get("title"))));
    }

    /**
     * 读某块屏的内容之前先按屏授权（M7.4）：模块权限只决定「能不能进大屏这个功能」，
     * 能不能看这一块由它自动登记的那条菜单决定。
     * <p>
     * 查不到与无权访问同一句文案——能区分两者就等于允许拿别人的 screenCode 逐条枚举组织里的大屏。
     */
    private DynamicDatavScreen requireViewable(String screenCode) {
        DynamicDatavScreen screen = StringUtils.isBlank(screenCode) ? null : screenService.findByCode(screenCode);
        DatavScreenAccess.requireViewable(screen);
        return screen;
    }

    /**
     * 发布 / 下线 / 转草稿
     */
    @Operation(summary = "变更大屏状态", description = "0 草稿 1 已发布 2 已下线")
    @PreAuthorize(hasPermi = "datav:screen:edit")
    @Log(title = "数据大屏", operationType = OperationType.UPDATE)
    @PutMapping("/publish/{screenCode}")
    public CommonResult<Object> publish(@PathVariable("screenCode") String screenCode,
                                        @RequestParam("status") Integer status) {
        return CommonResult.ok(screenService.changeStatus(screenCode, status));
    }

    /**
     * 复制为新草稿
     */
    @Operation(summary = "复制大屏")
    @PreAuthorize(hasPermi = "datav:screen:add")
    @Log(title = "数据大屏", operationType = OperationType.INSERT)
    @PostMapping("/copy/{screenCode}")
    public CommonResult<Object> copy(@PathVariable("screenCode") String screenCode) {
        // 复制会整份带走源屏的画布 JSON，所以先要求源屏可见：否则「有新增权限」就能读走任何一块屏
        requireViewable(screenCode);
        return CommonResult.ok(screenService.copyScreen(screenCode));
    }

    /**
     * 回滚到指定版本
     */
    @Operation(summary = "回滚大屏版本")
    @PreAuthorize(hasPermi = "datav:screen:edit")
    @Log(title = "数据大屏", operationType = OperationType.UPDATE)
    @PutMapping("/rollback/{screenCode}")
    public CommonResult<Object> rollback(@PathVariable("screenCode") String screenCode,
                                         @RequestParam("versionNo") Integer versionNo) {
        return CommonResult.ok(screenService.rollback(screenCode, versionNo));
    }

    /**
     * 删除大屏
     */
    @Operation(summary = "删除大屏")
    @PreAuthorize(hasPermi = "datav:screen:remove")
    @Log(title = "数据大屏", operationType = OperationType.DELETE)
    @DeleteMapping("/{screenCode}")
    public CommonResult<Object> remove(@PathVariable("screenCode") String screenCode) {
        if (StringUtils.isBlank(screenCode)) {
            return CommonResult.fail("大屏编码不能为空");
        }
        return toAjax(screenService.deleteByCode(screenCode));
    }
}
