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
import com.privernal.dynamic.datav.domain.DynamicDatavAsset;
import com.privernal.dynamic.datav.service.IDynamicDatavAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏素材接口
 * <p>
 * 文件字节由前端直传平台文件服务（/file/back/file/upload，本地/OSS/MinIO 三后端），
 * 这里只登记与查询元数据。权限复用 datav:screen:*（素材跟随大屏权限面）：
 * 能进设计器的人就能管理素材，多一套权限码只会多一处漏配。
 *
 * @author saperliu
 */
@Tag(name = "大屏素材相关接口")
@RestController
@Slf4j
@RequestMapping("/back/datav/asset")
public class DynamicDatavAssetController extends BaseController {

    @Autowired
    private IDynamicDatavAssetService datavAssetService;

    @Operation(summary = "登记素材", description = "上传完成后登记元数据，返回 assetCode")
    @PreAuthorize(hasPermi = "datav:screen:add")
    @Log(title = "大屏素材", operationType = OperationType.INSERT)
    @PostMapping("/save")
    public CommonResult<DynamicDatavAsset> save(@RequestBody DynamicDatavAsset asset) {
        return CommonResult.ok(datavAssetService.saveAsset(asset));
    }

    @Operation(summary = "素材列表", description = "按分类与关键字过滤")
    @PreAuthorize(hasPermi = "datav:screen:list")
    @GetMapping("/list")
    public CommonResult<List<DynamicDatavAsset>> list(@RequestParam(value = "category", required = false) String category,
                                                      @RequestParam(value = "keyword", required = false) String keyword) {
        return CommonResult.ok(datavAssetService.listAssets(category, keyword));
    }

    @Operation(summary = "删除素材", description = "仍被大屏引用时拒绝删除并提示引用方")
    @PreAuthorize(hasPermi = "datav:screen:remove")
    @Log(title = "大屏素材", operationType = OperationType.DELETE)
    @DeleteMapping("/{assetCode}")
    public CommonResult<Integer> remove(@PathVariable("assetCode") String assetCode) {
        return CommonResult.ok(datavAssetService.deleteByCode(assetCode));
    }
}
