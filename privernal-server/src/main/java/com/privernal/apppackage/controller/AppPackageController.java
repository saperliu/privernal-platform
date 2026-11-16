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
import com.privernal.apppackage.dto.AppPackageDryRunResultDTO;
import com.privernal.apppackage.dto.AppPackageImportResultDTO;
import com.privernal.apppackage.service.AppPackageService;
import com.privernal.apppackage.service.PackageSignatures;
import com.privernal.apppackage.service.TemplateCatalog;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 应用包Controller：应用级元数据的导出与导入，用于把搭建成果从一个环境复制到另一个环境。
 * R2-14 模板市场扩展：dry-run 预检、包签名校验、官方模板列表与一键安装。
 *
 * @author saperliu
 */
@Tag(name = "应用包相关接口")
@RestController
@Slf4j
public class AppPackageController extends BaseController {

    @Resource
    private AppPackageService appPackageService;

    @Resource
    private TemplateCatalog templateCatalog;

    @Resource
    private PackageSignatures packageSignatures;

    /**
     * 导出应用包（1.5 起自动签名：主密钥已配置时包带 HMAC 签名与校验和）
     */
    @Operation(summary = "导出应用包", description = "导出单个应用的表单、列表配置、流程定义与节点权限、报表/大屏/字典/角色绑定元数据")
    @PreAuthorize(hasPermi = "system:appPackage:export")
    @Log(title = "应用包", operationType = OperationType.EXPORT)
    @GetMapping("/back/system/appPackage/export")
    public CommonResult<AppPackageDTO> export(String appId) {
        return CommonResult.ok(appPackageService.exportApp(appId));
    }

    /**
     * 导入应用包
     */
    @Operation(summary = "导入应用包", description = "按应用编码复用或新建应用，重建缺失的表单、流程与节点权限；overwrite=true 时更新已存在项；"
            + "已签包验签失败将被拒绝，除非 ignoreSignature=true 显式豁免")
    @PreAuthorize(hasPermi = "system:appPackage:import")
    @Log(title = "应用包", operationType = OperationType.IMPORT)
    @PostMapping("/back/system/appPackage/import")
    public CommonResult<Object> appPackageImport(MultipartFile file, boolean overwrite, boolean ignoreSignature) {
        try {
            AppPackageDTO pkg = parsePackage(file);
            AppPackageImportResultDTO result = appPackageService.importApp(pkg, overwrite, ignoreSignature);
            return CommonResult.ok(result);
        } catch (Exception e) {
            log.warn("导入应用包失败", e);
            return CommonResult.fail("导入应用包失败：" + e.getMessage());
        }
    }

    /**
     * dry-run 预检（R2-14 ①）：解析上传的应用包并与目标环境逐项比对，产出冲突/覆盖/新建报告。
     * 全程只读、不落库，导入前给人工确认。
     */
    @Operation(summary = "应用包导入预检", description = "解析包内容并与目标环境比对：逐项预测 create/update/skip/reuse/conflict，"
            + "附签名/校验和状态与告警清单；不落库")
    @PreAuthorize(hasPermi = "system:appPackage:import")
    @PostMapping("/back/system/appPackage/dryRun")
    public CommonResult<Object> appPackageDryRun(MultipartFile file, boolean overwrite) {
        try {
            AppPackageDTO pkg = parsePackage(file);
            return CommonResult.ok(appPackageService.dryRun(pkg, overwrite));
        } catch (Exception e) {
            log.warn("应用包预检失败", e);
            return CommonResult.fail("应用包预检失败：" + e.getMessage());
        }
    }

    /**
     * 官方模板列表（R2-14 ③）：classpath 内置模板清单，展示面来自 manifest，不解析包体
     */
    @Operation(summary = "官方模板列表", description = "随发布内置的行业模板清单（manifest），私有化部署零外部依赖")
    @PreAuthorize(hasPermi = "system:appPackage:template")
    @GetMapping("/back/system/appPackage/templates")
    public CommonResult<List<TemplateCatalog.TemplateSummary>> templates() {
        return CommonResult.ok(templateCatalog.list());
    }

    /**
     * 模板包详情：返回完整应用包（含表单/流程清单），供安装前预览
     */
    @Operation(summary = "模板包详情", description = "按模板编码返回完整应用包 JSON；模板未登记即拒绝，不提供任意 classpath 读取")
    @PreAuthorize(hasPermi = "system:appPackage:template")
    @GetMapping("/back/system/appPackage/templates/{templateCode}")
    public CommonResult<AppPackageDTO> templateDetail(@PathVariable String templateCode) {
        return CommonResult.ok(templateCatalog.load(templateCode));
    }

    /**
     * 模板安装预检：走与手工上传相同的 dry-run
     */
    @Operation(summary = "模板安装预检", description = "与导入预检同口径：预测 create/update/skip/reuse/conflict，不落库")
    @PreAuthorize(hasPermi = "system:appPackage:import")
    @PostMapping("/back/system/appPackage/dryRunTemplate")
    public CommonResult<Object> templateDryRun(String templateCode, boolean overwrite) {
        try {
            AppPackageDTO pkg = loadAndResign(templateCode);
            return CommonResult.ok(appPackageService.dryRun(pkg, overwrite));
        } catch (Exception e) {
            log.warn("模板预检失败 templateCode={}", templateCode, e);
            return CommonResult.fail("模板预检失败：" + e.getMessage());
        }
    }

    /**
     * 一键安装官方模板（R2-14 ③）：加载包体→本环境现签→标准导入链路（表单/流程/报表/大屏/字典/角色绑定）
     */
    @Operation(summary = "一键安装官方模板", description = "模板来自随发布内置资源（classpath），安装前校验 checksum 并以本环境密钥现签，"
            + "随后走标准导入链路；overwrite 语义与手工导入一致")
    @PreAuthorize(hasPermi = "system:appPackage:import")
    @Log(title = "应用包模板安装", operationType = OperationType.IMPORT)
    @PostMapping("/back/system/appPackage/importTemplate")
    public CommonResult<Object> importTemplate(String templateCode, boolean overwrite) {
        try {
            AppPackageDTO pkg = loadAndResign(templateCode);
            AppPackageImportResultDTO result = appPackageService.importApp(pkg, overwrite, false);
            return CommonResult.ok(result);
        } catch (Exception e) {
            log.warn("模板安装失败 templateCode={}", templateCode, e);
            return CommonResult.fail("模板安装失败：" + e.getMessage());
        }
    }

    /** 上传包 → DTO（charset 固定 UTF-8，导出端也是 UTF-8 写出） */
    private AppPackageDTO parsePackage(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("应用包文件为空");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return JSON.parseObject(content, AppPackageDTO.class);
    }

    /**
     * 模板安装通道：classpath 加载（含 checksum 校验）后用本环境密钥现签——
     * 模板包不预置签名（签名密钥随环境走），现签后走标准导入，报告里的签名状态与手工包一致
     */
    private AppPackageDTO loadAndResign(String templateCode) {
        AppPackageDTO pkg = templateCatalog.load(templateCode);
        packageSignatures.sign(pkg);
        return pkg;
    }
}
