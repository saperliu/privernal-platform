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

package com.privernal.dynamic.report.service.impl;

import com.privernal.common.core.constant.MenuConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.report.domain.DynamicDashboard;
import com.privernal.dynamic.report.domain.DynamicDashboardItem;
import com.privernal.dynamic.report.domain.DynamicReport;
import com.privernal.dynamic.report.mapper.DynamicDashboardItemMapper;
import com.privernal.dynamic.report.mapper.DynamicDashboardMapper;
import com.privernal.dynamic.report.mapper.DynamicReportMapper;
import com.privernal.dynamic.report.service.IDynamicDashboardService;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.dto.SystemMenuDTO;
import com.privernal.system.feign.RemoteUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 报表仪表盘服务实现
 * <p>
 * 新增仪表盘时自动注册菜单（与动态表单同构）：
 * C 菜单 menu_code=report_{code}、path=/report/{code}、component=view.report、
 * permission=report:{code}:view；F 按钮 report:{code}:list/add/edit/remove。
 * 删除时按 menu_code 级联清理菜单（角色/组织/应用授权与收藏由菜单删除接口联动清理）。
 *
 * @author saperliu
 */
@Service
public class DynamicDashboardServiceImpl implements IDynamicDashboardService {

    @Autowired
    private DynamicDashboardMapper dashboardMapper;

    @Autowired
    private DynamicDashboardItemMapper dashboardItemMapper;

    @Autowired
    private DynamicReportMapper reportMapper;

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDashboard saveDashboard(DynamicDashboard dashboard, List<DynamicDashboardItem> items) {
        if (dashboard == null || StringUtils.isBlank(dashboard.getDashboardCode())) {
            throw new ServiceException("仪表盘编码不能为空");
        }

        DynamicDashboard exists = findByCode(dashboard.getDashboardCode());
        boolean created = exists == null;
        if (created) {
            dashboard.setId(null);
            dashboard.setCreatedBy(SecurityUtils.getUserId());
            dashboard.setCreatedTime(new Date());
            dashboard.setOrganizationId(SecurityUtils.getOrganizationId());
            dashboard.setDepartmentId(SecurityUtils.getDepartmentId());
            dashboardMapper.insert(dashboard);
        } else {
            dashboard.setId(exists.getId());
            dashboard.setOrganizationId(exists.getOrganizationId());
            dashboard.setDepartmentId(exists.getDepartmentId());
            dashboard.setUpdatedBy(SecurityUtils.getUserId());
            dashboard.setUpdatedTime(new Date());
            LambdaPrivernalWrapper<DynamicDashboard> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(DynamicDashboard::getId, dashboard.getId());
            dashboardMapper.update(dashboard, wrapper);
        }

        // 卡片全量重建（先删后插，幂等）
        LambdaPrivernalWrapper<DynamicDashboardItem> clearWrapper = new LambdaPrivernalWrapper<>();
        clearWrapper.eq(DynamicDashboardItem::getDashboardCode, dashboard.getDashboardCode());
        dashboardItemMapper.delete(clearWrapper);
        if (items != null) {
            int order = 0;
            for (DynamicDashboardItem item : items) {
                item.setId(null);
                item.setDashboardCode(dashboard.getDashboardCode());
                item.setOrderNum(item.getOrderNum() != null ? item.getOrderNum() : order);
                item.setCreatedBy(SecurityUtils.getUserId());
                item.setCreatedTime(new Date());
                item.setOrganizationId(SecurityUtils.getOrganizationId());
                item.setDepartmentId(SecurityUtils.getDepartmentId());
                dashboardItemMapper.insert(item);
                order++;
            }
        }

        if (created) {
            registerMenus(dashboard);
        }
        return dashboard;
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicDashboard findByCode(String dashboardCode) {
        if (StringUtils.isBlank(dashboardCode)) {
            return null;
        }
        LambdaPrivernalWrapper<DynamicDashboard> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDashboard::getDashboardCode, dashboardCode);
        return dashboardMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardDetail findDetail(String dashboardCode) {
        DynamicDashboard dashboard = findByCode(dashboardCode);
        if (dashboard == null) {
            return null;
        }
        LambdaPrivernalWrapper<DynamicDashboardItem> itemWrapper = new LambdaPrivernalWrapper<>();
        itemWrapper.eq(DynamicDashboardItem::getDashboardCode, dashboardCode);
        itemWrapper.orderByAsc(DynamicDashboardItem::getOrderNum);
        List<DynamicDashboardItem> items = dashboardItemMapper.selectList(itemWrapper);

        List<DynamicReport> reports = new ArrayList<>();
        for (DynamicDashboardItem item : items) {
            LambdaPrivernalWrapper<DynamicReport> reportWrapper = new LambdaPrivernalWrapper<>();
            reportWrapper.eq(DynamicReport::getReportCode, item.getReportCode());
            DynamicReport report = reportMapper.selectOne(reportWrapper);
            if (report != null) {
                reports.add(report);
            }
        }
        DashboardDetail detail = new DashboardDetail();
        detail.setDashboard(dashboard);
        detail.setItems(items);
        detail.setReports(reports);
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicDashboard> listByApp(String appId) {
        LambdaPrivernalWrapper<DynamicDashboard> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDashboard::getAppId, appId);
        wrapper.orderByDesc(DynamicDashboard::getCreatedTime);
        return dashboardMapper.selectList(wrapper);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteByCode(String dashboardCode) {
        DynamicDashboard dashboard = findByCode(dashboardCode);
        if (dashboard == null) {
            return 0;
        }
        LambdaPrivernalWrapper<DynamicDashboardItem> itemWrapper = new LambdaPrivernalWrapper<>();
        itemWrapper.eq(DynamicDashboardItem::getDashboardCode, dashboardCode);
        dashboardItemMapper.delete(itemWrapper);
        // 先改名释放 dashboard_code 唯一索引，便于同名仪表盘重建
        DynamicDashboard rename = new DynamicDashboard();
        rename.setDashboardCode(dashboardCode + "_deleted_" + dashboard.getId());
        LambdaPrivernalWrapper<DynamicDashboard> renameWrapper = new LambdaPrivernalWrapper<>();
        renameWrapper.eq(DynamicDashboard::getId, dashboard.getId());
        dashboardMapper.update(rename, renameWrapper);
        int rows = dashboardMapper.deleteFillById(dashboard.getId());
        // 菜单按 code 级联清理（子按钮 + 角色/组织/应用授权 + 收藏）
        remoteUserService.deleteSystemMenuByCode("report_" + dashboardCode);
        return rows;
    }

    /**
     * 自动注册菜单：C 菜单（运行时入口）+ 4 个 F 按钮（设计态管理）。
     * 权限码与 DynamicReportAuthUtil 的组码口径一致：report:{code}:{operation}。
     */
    private void registerMenus(DynamicDashboard dashboard) {
        SystemAppInfoDTO systemAppInfo = remoteUserService.findAppInfoById(dashboard.getAppId());
        if (systemAppInfo == null) {
            throw new ServiceException("应用不存在：" + dashboard.getAppId());
        }
        String appCode = systemAppInfo.getCode().toLowerCase();
        SystemMenuDTO appDirMenu = remoteUserService.findSystemMenuByCode(appCode);
        if (appDirMenu == null) {
            appDirMenu = createAppDirMenu(systemAppInfo, appCode);
        }

        SystemMenuDTO menu = new SystemMenuDTO();
        menu.setMenuName(dashboard.getDashboardName());
        menu.setMenuCode("report_" + dashboard.getDashboardCode());
        menu.setMenuType(MenuConstants.TYPE_MENU);
        menu.setComponent("view.report");
        menu.setPath("/report/" + dashboard.getDashboardCode());
        menu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
        menu.setParentId(appDirMenu.getId());
        menu.setCreatedBy(SecurityUtils.getUserId());
        menu.setCreatedTime(new Date());
        menu.setOrderNum(20L);
        menu.setVisible(1);
        menu.setPermission("report:" + dashboard.getDashboardCode() + ":view");
        menu.setAppMenu(0);
        menu.setAppId(dashboard.getAppId());
        menu.setOrganizationId(SecurityUtils.getOrganizationId());
        SystemMenuDTO menuTop = remoteUserService.insertSystemMenu(menu);

        saveMenuButton(dashboard.getDashboardName() + "列表", menuTop.getId(), dashboard.getDashboardCode(), "list", 4, dashboard.getAppId());
        saveMenuButton(dashboard.getDashboardName() + "新增", menuTop.getId(), dashboard.getDashboardCode(), "add", 3, dashboard.getAppId());
        saveMenuButton(dashboard.getDashboardName() + "修改", menuTop.getId(), dashboard.getDashboardCode(), "edit", 2, dashboard.getAppId());
        saveMenuButton(dashboard.getDashboardName() + "删除", menuTop.getId(), dashboard.getDashboardCode(), "remove", 1, dashboard.getAppId());
    }

    private SystemMenuDTO createAppDirMenu(SystemAppInfoDTO systemAppInfo, String appCode) {
        SystemMenuDTO dirMenu = new SystemMenuDTO();
        dirMenu.setMenuName(systemAppInfo.getAppName());
        dirMenu.setMenuCode(appCode);
        dirMenu.setMenuType(MenuConstants.TYPE_DIR);
        dirMenu.setComponent("basic");
        dirMenu.setPath("/" + appCode);
        dirMenu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
        dirMenu.setParentId("0");
        dirMenu.setCreatedBy(SecurityUtils.getUserId());
        dirMenu.setCreatedTime(new Date());
        dirMenu.setOrderNum(20L);
        dirMenu.setVisible(1);
        dirMenu.setPermission("system:" + appCode + ":list");
        dirMenu.setAppMenu(0);
        dirMenu.setAppId(systemAppInfo.getId());
        dirMenu.setOrganizationId(SecurityUtils.getOrganizationId());
        return remoteUserService.insertSystemMenu(dirMenu);
    }

    private void saveMenuButton(String name, String parentId, String code, String operation, long order, String appId) {
        SystemMenuDTO button = new SystemMenuDTO();
        button.setMenuName(name);
        button.setMenuCode("");
        button.setMenuType(MenuConstants.TYPE_BUTTON);
        button.setComponent("");
        button.setPath("");
        button.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
        button.setParentId(parentId);
        button.setCreatedBy(SecurityUtils.getUserId());
        button.setCreatedTime(new Date());
        button.setOrderNum(order);
        button.setVisible(1);
        button.setPermission("report:" + code + ":" + operation);
        button.setOrganizationId(SecurityUtils.getOrganizationId());
        button.setAppId(appId);
        remoteUserService.insertSystemMenu(button);
    }
}
