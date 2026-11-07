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

import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.datav.service.IDynamicDatavShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏外链分享管理接口
 * <p>
 * 权限复用 datav:screen:edit（谁能发布大屏谁就能分享它，多一套权限码只会多一处漏配）。
 * 签名只在创建响应里出现一次：丢了就禁用旧的再建一个，不提供「重新查看签名」——
 * 列表接口批量吐签名等于把吊销手段交给了任何拿到列表的人。
 *
 * @author saperliu
 */
@Tag(name = "大屏外链分享管理接口")
@RestController
@Slf4j
@RequestMapping("/back/datav/share")
public class DynamicDatavShareController extends BaseController {

    @Autowired
    private IDynamicDatavShareService shareService;

    @Operation(summary = "创建分享链接", description = "返回 shareCode 与只此一次的 sign")
    @PreAuthorize(hasPermi = "datav:screen:edit")
    @Log(title = "大屏外链", operationType = OperationType.INSERT)
    @PostMapping("/create")
    public CommonResult<Object> create(@RequestParam("screenCode") String screenCode,
                                       @RequestParam(value = "expireHours", required = false) Integer expireHours,
                                       @RequestParam(value = "refererWhitelist", required = false) String refererWhitelist) {
        return CommonResult.ok(shareService.createShare(screenCode, expireHours, refererWhitelist));
    }

    @Operation(summary = "分享列表", description = "某块大屏的外链记录（不含 sign）")
    @PreAuthorize(hasPermi = "datav:screen:edit")
    @GetMapping("/list/{screenCode}")
    public CommonResult<Object> list(@PathVariable("screenCode") String screenCode) {
        return CommonResult.ok(shareService.listShares(screenCode));
    }

    @Operation(summary = "禁用分享（吊销）")
    @PreAuthorize(hasPermi = "datav:screen:edit")
    @Log(title = "大屏外链", operationType = OperationType.UPDATE)
    @PostMapping("/disable/{shareCode}")
    public CommonResult<Object> disable(@PathVariable("shareCode") String shareCode) {
        shareService.disableShare(shareCode);
        return CommonResult.ok();
    }
}
