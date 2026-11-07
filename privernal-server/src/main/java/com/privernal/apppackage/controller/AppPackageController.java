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

package com.privernal.apppackage.controller;

import com.privernal.apppackage.dto.AppPackageDTO;
import com.privernal.apppackage.dto.AppPackageImportResultDTO;
import com.privernal.apppackage.service.AppPackageService;
import com.privernal.common.log.annotation.Log;
import com.privernal.common.log.enums.OperationType;
import com.privernal.common.security.annotation.PreAuthorize;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 应用包Controller：应用级元数据的导出与导入，用于把搭建成果从一个环境复制到另一个环境。
 *
 * @author saperliu
 */
@Tag(name = "应用包相关接口")
@RestController
@Slf4j
public class AppPackageController extends BaseController {

    @Resource
    private AppPackageService appPackageService;

    /**
     * 导出应用包
     */
    @Operation(summary = "导出应用包", description = "导出单个应用的表单、列表配置、流程定义与节点权限元数据")
    @PreAuthorize(hasPermi = "system:appPackage:export")
    @Log(title = "应用包", operationType = OperationType.EXPORT)
    @GetMapping("/back/system/appPackage/export")
    public CommonResult<AppPackageDTO> export(String appId) {
        return CommonResult.ok(appPackageService.exportApp(appId));
    }

    /**
     * 导入应用包
     */
    @Operation(summary = "导入应用包", description = "按应用编码复用或新建应用，重建缺失的表单、流程与节点权限；overwrite=true 时更新已存在项")
    @PreAuthorize(hasPermi = "system:appPackage:import")
    @Log(title = "应用包", operationType = OperationType.IMPORT)
    @PostMapping("/back/system/appPackage/import")
    public CommonResult<Object> appPackageImport(MultipartFile file, boolean overwrite) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            AppPackageDTO pkg = JSON.parseObject(content, AppPackageDTO.class);
            AppPackageImportResultDTO result = appPackageService.importApp(pkg, overwrite);
            return CommonResult.ok(result);
        } catch (Exception e) {
            log.warn("导入应用包失败", e);
            return CommonResult.fail("导入应用包失败：" + e.getMessage());
        }
    }
}
