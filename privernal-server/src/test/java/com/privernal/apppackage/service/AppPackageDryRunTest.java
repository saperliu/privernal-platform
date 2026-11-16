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

package com.privernal.apppackage.service;

import com.privernal.apppackage.dto.AppPackageDTO;
import com.privernal.apppackage.dto.AppPackageDryRunResultDTO;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.service.IDynamicFormColumnService;
import com.privernal.dynamic.dbs.service.IDynamicFormService;
import com.privernal.dynamic.report.service.IDynamicDashboardService;
import com.privernal.dynamic.report.service.IDynamicDatasetService;
import com.privernal.dynamic.report.service.IDynamicReportService;
import com.privernal.flow.dto.FlowFormVersionDTO;
import com.privernal.flow.service.IFlowDefinitionService;
import com.privernal.flow.service.IFlowFormVersionService;
import com.privernal.job.mapper.ScheduleTaskMapper;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.filter.SystemAppInfoFilterDTO;
import com.privernal.system.service.ISystemAppInfoService;
import com.privernal.system.service.ISystemDictionaryDataService;
import com.privernal.system.service.ISystemDictionaryTypeService;
import com.privernal.system.service.ISystemMenuService;
import com.privernal.system.service.ISystemRoleMenuService;
import com.privernal.system.service.ISystemRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * dry-run 预检（R2-14 ①）的预测口径测试：报告里每一项的 predicted 必须与真实导入动作一一对应——
 * 预检说 create 导入却 skip（或反过来）会让预检失去存在意义。这里用 mock 目标环境，
 * 断言「已存在/不存在 × overwrite 开/关」四种组合的预测方向。
 *
 * @author saperliu
 */
@DisplayName("应用包 dry-run 预检测试")
class AppPackageDryRunTest {

    private AppPackageService service;

    private ISystemAppInfoService systemAppInfoService;

    private IDynamicFormService dynamicFormService;

    private IDynamicFormColumnService dynamicFormColumnService;

    private IFlowDefinitionService flowDefinitionService;

    private IFlowFormVersionService flowFormVersionService;

    private IDynamicDatasetService datasetService;

    private IDynamicReportService reportService;

    private IDynamicDashboardService dashboardService;

    private com.privernal.dynamic.datav.service.IDynamicDatavScreenService datavScreenService;

    private com.privernal.dynamic.datav.service.IDynamicDatavAssetService datavAssetService;

    private ScheduleTaskMapper scheduleTaskMapper;

    private com.privernal.dynamic.advanced.mapper.DynamicDocTemplateMapper docTemplateMapper;

    private com.privernal.dynamic.report.service.DynamicReportTableResolver reportTableResolver;

    private ISystemDictionaryTypeService dictionaryTypeService;

    private ISystemDictionaryDataService dictionaryDataService;

    private ISystemRoleService systemRoleService;

    private ISystemMenuService systemMenuService;

    private ISystemRoleMenuService systemRoleMenuService;

    private com.privernal.system.service.ISystemAppMenuService systemAppMenuService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new AppPackageService();
        systemAppInfoService = mock(ISystemAppInfoService.class);
        dynamicFormService = mock(IDynamicFormService.class);
        dynamicFormColumnService = mock(IDynamicFormColumnService.class);
        flowDefinitionService = mock(IFlowDefinitionService.class);
        flowFormVersionService = mock(IFlowFormVersionService.class);
        datasetService = mock(IDynamicDatasetService.class);
        reportService = mock(IDynamicReportService.class);
        dashboardService = mock(IDynamicDashboardService.class);
        datavScreenService = mock(com.privernal.dynamic.datav.service.IDynamicDatavScreenService.class);
        datavAssetService = mock(com.privernal.dynamic.datav.service.IDynamicDatavAssetService.class);
        scheduleTaskMapper = mock(ScheduleTaskMapper.class);
        docTemplateMapper = mock(com.privernal.dynamic.advanced.mapper.DynamicDocTemplateMapper.class);
        reportTableResolver = mock(com.privernal.dynamic.report.service.DynamicReportTableResolver.class);
        dictionaryTypeService = mock(ISystemDictionaryTypeService.class);
        dictionaryDataService = mock(ISystemDictionaryDataService.class);
        systemRoleService = mock(ISystemRoleService.class);
        systemMenuService = mock(ISystemMenuService.class);
        systemRoleMenuService = mock(ISystemRoleMenuService.class);
        systemAppMenuService = mock(com.privernal.system.service.ISystemAppMenuService.class);

        setField("systemAppInfoService", systemAppInfoService);
        setField("dynamicFormService", dynamicFormService);
        setField("dynamicFormColumnService", dynamicFormColumnService);
        setField("flowDefinitionService", flowDefinitionService);
        setField("flowFormVersionService", flowFormVersionService);
        setField("datasetService", datasetService);
        setField("reportService", reportService);
        setField("dashboardService", dashboardService);
        setField("datavScreenService", datavScreenService);
        setField("datavAssetService", datavAssetService);
        setField("scheduleTaskMapper", scheduleTaskMapper);
        setField("docTemplateMapper", docTemplateMapper);
        setField("reportTableResolver", reportTableResolver);
        setField("dictionaryTypeService", dictionaryTypeService);
        setField("dictionaryDataService", dictionaryDataService);
        setField("systemRoleService", systemRoleService);
        setField("systemMenuService", systemMenuService);
        setField("systemRoleMenuService", systemRoleMenuService);
        setField("systemAppMenuService", systemAppMenuService);
        setField("packageSignatures", new PackageSignatures(""));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AppPackageService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    /** 目标环境：同编码应用已存在 + 表单 old_form 已存在 */
    private void targetEnvWithExisting() {
        SystemAppInfoDTO app = new SystemAppInfoDTO();
        app.setId("app-1");
        app.setCode("demo_app");
        when(systemAppInfoService.selectSystemAppInfoList(any(SystemAppInfoFilterDTO.class)))
                .thenReturn(List.of(app));
        DynamicForm existingForm = new DynamicForm();
        existingForm.setId("form-1");
        existingForm.setTableCode("old_form");
        when(dynamicFormService.selectDynamicFormByTableCode("old_form")).thenReturn(existingForm);
        when(dynamicFormService.selectDynamicFormByTableCode("new_form")).thenReturn(null);
    }

    private static AppPackageDTO.Form pkgForm(String tableCode) {
        AppPackageDTO.Form form = new AppPackageDTO.Form();
        form.setTableCode(tableCode);
        form.setTableName("表 " + tableCode);
        return form;
    }

    private static AppPackageDTO samplePackage() {
        AppPackageDTO pkg = new AppPackageDTO();
        pkg.getApp().setCode("demo_app");
        pkg.getApp().setAppName("演示应用");
        pkg.getForms().add(pkgForm("new_form"));
        pkg.getForms().add(pkgForm("old_form"));
        return pkg;
    }

    @Test
    @DisplayName("已存在表单：overwrite=true 预测 update，=false 预测 skip")
    void existingFormPredictsUpdateOrSkip() throws Exception {
        targetEnvWithExisting();
        // 覆盖不删字段：包列包含目标环境全部列
        DynamicFormColumn column = new DynamicFormColumn();
        column.setFiledCode("name");
        when(dynamicFormColumnService.selectDynamicFormColumnByFormId("form-1")).thenReturn(List.of(column));
        AppPackageDTO.Form oldForm = pkgForm("old_form");
        AppPackageDTO.Column pkgColumn = new AppPackageDTO.Column();
        pkgColumn.setFiledCode("name");
        oldForm.getColumns().add(pkgColumn);

        AppPackageDTO pkg = samplePackage();
        pkg.getForms().set(1, oldForm);

        AppPackageDryRunResultDTO overwriteOn = service.dryRun(pkg, true);
        assertThat(predictOf(overwriteOn, "old_form")).isEqualTo(AppPackageDryRunResultDTO.UPDATE);
        assertThat(predictOf(overwriteOn, "new_form")).isEqualTo(AppPackageDryRunResultDTO.CREATE);
        assertThat(overwriteOn.getAppCode()).isEqualTo("demo_app");

        AppPackageDryRunResultDTO overwriteOff = service.dryRun(pkg, false);
        assertThat(predictOf(overwriteOff, "old_form")).isEqualTo(AppPackageDryRunResultDTO.SKIP);
    }

    @Test
    @DisplayName("覆盖会删除目标环境字段 → 预测 conflict，与真实导入的拒绝口径一致")
    void fieldDropPredictsConflict() throws Exception {
        targetEnvWithExisting();
        // 目标环境有 local_only 列，包里没有 → 覆盖会触发 DROP COLUMN 保护
        DynamicFormColumn column = new DynamicFormColumn();
        column.setFiledCode("local_only");
        when(dynamicFormColumnService.selectDynamicFormColumnByFormId("form-1")).thenReturn(List.of(column));

        AppPackageDTO pkg = samplePackage();
        AppPackageDryRunResultDTO report = service.dryRun(pkg, true);

        assertThat(predictOf(report, "old_form")).isEqualTo(AppPackageDryRunResultDTO.CONFLICT);
        assertThat(report.hasConflict()).isTrue();
        assertThat(report.getItems().stream()
                .filter(item -> "old_form".equals(item.getCode()))
                .findFirst().orElseThrow().getMessage()).contains("local_only");
    }

    @Test
    @DisplayName("无签包：signatureStatus=notSigned，报 warning 不阻断预检")
    void unsignedPackageWarnsButRuns() throws Exception {
        targetEnvWithExisting();
        when(dynamicFormColumnService.selectDynamicFormColumnByFormId(any())).thenReturn(List.of());

        AppPackageDryRunResultDTO report = service.dryRun(samplePackage(), false);

        assertThat(report.getSignatureStatus()).isEqualTo("notSigned");
        assertThat(report.isSignatureKeyConfigured()).isFalse();
        assertThat(report.getWarnings()).anyMatch(line -> line.contains("签名"));
    }

    @Test
    @DisplayName("字典与角色绑定预测：类型存在=reuse；角色缺失=skip+warning")
    void dictAndRolePredictions() throws Exception {
        targetEnvWithExisting();
        when(dynamicFormColumnService.selectDynamicFormColumnByFormId(any())).thenReturn(List.of());

        AppPackageDTO pkg = samplePackage();
        AppPackageDTO.DictTypePackage newDict = new AppPackageDTO.DictTypePackage();
        newDict.setDictType("fresh_dict");
        newDict.setDictName("新字典");
        pkg.getDicts().add(newDict);
        AppPackageDTO.DictTypePackage knownDict = new AppPackageDTO.DictTypePackage();
        knownDict.setDictType("known_dict");
        pkg.getDicts().add(knownDict);
        AppPackageDTO.RoleBinding missingRole = new AppPackageDTO.RoleBinding();
        missingRole.setRoleKey("org_admin");
        missingRole.setRoleName("组织管理员");
        missingRole.getMenuCodes().add("builder_new_form");
        pkg.getRoleBindings().add(missingRole);

        when(dictionaryTypeService.selectSystemDictionaryTypeList(any()))
                .thenAnswer(invocation -> {
                    com.privernal.system.filter.SystemDictionaryTypeFilterDTO filter = invocation.getArgument(0);
                    return "known_dict".equals(filter.getDictType())
                            ? List.of(new com.privernal.system.domain.SystemDictionaryType())
                            : List.of();
                });
        when(systemRoleService.selectSystemRoleList(any())).thenReturn(List.of());

        AppPackageDryRunResultDTO report = service.dryRun(pkg, false);

        assertThat(predictOf(report, "fresh_dict", "dict")).isEqualTo(AppPackageDryRunResultDTO.CREATE);
        assertThat(predictOf(report, "known_dict", "dict")).isEqualTo(AppPackageDryRunResultDTO.REUSE);
        assertThat(predictOf(report, "org_admin", "roleBinding")).isEqualTo(AppPackageDryRunResultDTO.SKIP);
        assertThat(report.getWarnings()).anyMatch(line -> line.contains("org_admin"));
    }

    private static String predictOf(AppPackageDryRunResultDTO report, String code) {
        return predictOf(report, code, "form");
    }

    private static String predictOf(AppPackageDryRunResultDTO report, String code, String type) {
        return report.getItems().stream()
                .filter(item -> type.equals(item.getType()) && code.equals(item.getCode()))
                .findFirst().orElseThrow(() -> new AssertionError("报告缺少 " + type + ":" + code))
                .getPredicted();
    }
}
