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

package com.privernal.dynamic.datav.service.impl;

import com.privernal.common.core.constant.MenuConstants;
import com.privernal.common.core.enums.WorkflowDataStatus;
import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.IdUtils;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.mybatis.query.LambdaPrivernalWrapper;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.domain.DynamicDatavVersion;
import com.privernal.dynamic.datav.mapper.DynamicDatavScreenMapper;
import com.privernal.dynamic.datav.mapper.DynamicDatavVersionMapper;
import com.privernal.dynamic.datav.service.DatavScreenAccess;
import com.privernal.dynamic.datav.service.IDynamicDatavScreenService;
import com.privernal.system.dto.SystemAppInfoDTO;
import com.privernal.system.dto.SystemMenuDTO;
import com.privernal.system.feign.RemoteUserService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 数据大屏服务实现
 * <p>
 * 校验只放在这一层：schema 是整体提交的 JSON，编辑器与 API 直调都必须被同样约束，
 * 否则一份手写的超限 JSON 就能绕过前端限制落库。
 *
 * @author saperliu
 */
@Slf4j
@Service
public class DynamicDatavScreenServiceImpl implements IDynamicDatavScreenService {

    /** 画布 JSON 上限：一张 40 组件的大屏约 60KB，2MB 足够宽松又能挡住误提交的巨型对象 */
    private static final int MAX_SCHEMA_CHARS = 2 * 1024 * 1024;

    /**
     * 单屏组件数上限。批量取数一次只处理 30 个组件（DynamicDatavDataService.MAX_BATCH_WIDGETS），
     * 超出的组件在预览时就取不到数——与其让画布上出现「永远空白」的组件，不如在保存时就拒绝。
     */
    private static final int MAX_WIDGETS = 120;

    private static final Set<Integer> ALLOWED_STATUS = Set.of(0, 1, 2);

    private static final String DEFAULT_LAYOUT_MODE = "absolute";

    /** 发布快照在 dynamic_datav_version.remark 上的固定标记；下划线开头避免与用户手填备注撞车 */
    public static final String PUBLISH_SNAPSHOT_REMARK = "__publish_snapshot__";

    @Autowired
    private DynamicDatavScreenMapper screenMapper;

    @Autowired
    private DynamicDatavVersionMapper versionMapper;

    @Autowired
    private com.privernal.dynamic.report.service.IDynamicDatasetService datasetService;

    @Autowired
    private RemoteUserService remoteUserService;

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDatavScreen saveDatavScreen(DynamicDatavScreen screen) {
        if (screen == null) {
            throw new ServiceException("大屏内容不能为空");
        }
        if (StringUtils.isBlank(screen.getScreenName())) {
            throw new ServiceException("大屏名称不能为空");
        }
        validateSchema(screen.getSchemaJson());

        DynamicDatavScreen exists = StringUtils.isBlank(screen.getScreenCode())
                ? null : findByCode(screen.getScreenCode());

        if (exists == null) {
            screen.setId(null);
            if (StringUtils.isBlank(screen.getScreenCode())) {
                screen.setScreenCode("datav_" + IdUtils.simpleUUID());
            }
            screen.setCreatedBy(SecurityUtils.getUserId());
            screen.setCreatedTime(new Date());
            screen.setOrganizationId(SecurityUtils.getOrganizationId());
            screen.setDepartmentId(SecurityUtils.getDepartmentId());
            if (screen.getStatus() == null) {
                screen.setStatus(0);
            }
            if (!ALLOWED_STATUS.contains(screen.getStatus())) {
                throw new ServiceException("不支持的大屏状态：" + screen.getStatus());
            }
        } else {
            screen.setId(exists.getId());
            // 组织归属不允许随请求改写，否则一次保存就能把大屏挪到别的租户
            screen.setOrganizationId(exists.getOrganizationId());
            screen.setDepartmentId(exists.getDepartmentId());
            if (StringUtils.isBlank(screen.getAppId())) {
                screen.setAppId(exists.getAppId());
            }
            screen.setUpdatedBy(SecurityUtils.getUserId());
            screen.setUpdatedTime(new Date());
            if (screen.getStatus() == null) {
                screen.setStatus(exists.getStatus());
            }
        }

        if (StringUtils.isBlank(screen.getLayoutMode())) {
            screen.setLayoutMode(DEFAULT_LAYOUT_MODE);
        }
        screen.setWidgetCount(countWidgets(screen.getSchemaJson()));

        if (exists == null) {
            screenMapper.insert(screen);
            ensureScreenMenu(screen);
        } else {
            LambdaPrivernalWrapper<DynamicDatavScreen> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(DynamicDatavScreen::getId, screen.getId());
            screenMapper.update(screen, wrapper);
            syncScreenMenuName(screen);
        }

        recordVersion(screen);
        return screen;
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicDatavScreen findByCode(String screenCode) {
        if (StringUtils.isBlank(screenCode)) {
            return null;
        }
        LambdaPrivernalWrapper<DynamicDatavScreen> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavScreen::getScreenCode, screenCode);
        return screenMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicDatavScreen> listScreens(String appId) {
        LambdaPrivernalWrapper<DynamicDatavScreen> wrapper = new LambdaPrivernalWrapper<>();
        if (StringUtils.isNotBlank(appId)) {
            wrapper.eq(DynamicDatavScreen::getAppId, appId);
        }
        wrapper.orderByDesc(DynamicDatavScreen::getUpdatedTime);
        List<DynamicDatavScreen> list = screenMapper.selectList(wrapper);
        // 列表也按屏收紧：投放与取数早就是「没有这块屏的授权就看不见」，列表原先是组织级全量，
        // 等于把别人屏的名称与编码（screenCode 就是取数入参）摊给任何持有 datav:screen:list 的人看。
        // 超管走 *:*:* 不受影响；创建人自己建的屏一定看得见（见 canSeeInList 的理由）。
        Collection<String> permissions = DatavScreenAccess.loginPermissions();
        String currentUserId = SecurityUtils.getUserId();
        list.removeIf(item -> !DatavScreenAccess.canSeeInList(permissions, currentUserId, item));
        // 列表不带画布 JSON：一屏几十 KB，十几屏就是 MB 级响应，前端列表页也用不到
        list.forEach(item -> item.setSchemaJson(null));
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public DynamicDatavVersion findVersion(String screenCode, Integer versionNo) {
        if (versionNo == null) {
            throw new ServiceException("缺少版本号");
        }
        LambdaPrivernalWrapper<DynamicDatavVersion> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavVersion::getScreenCode, screenCode);
        wrapper.eq(DynamicDatavVersion::getVersionNo, versionNo);
        DynamicDatavVersion version = versionMapper.selectOne(wrapper);
        if (version == null) {
            throw new ServiceException(String.format("版本不存在：%s 的 v%d", screenCode, versionNo));
        }
        return version;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int deleteByCode(String screenCode) {
        DynamicDatavScreen screen = findByCode(screenCode);
        if (screen == null) {
            return 0;
        }
        LambdaPrivernalWrapper<DynamicDatavScreen> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavScreen::getId, screen.getId());
        int rows = screenMapper.delete(wrapper);

        LambdaPrivernalWrapper<DynamicDatavVersion> versionWrapper = new LambdaPrivernalWrapper<>();
        versionWrapper.eq(DynamicDatavVersion::getScreenCode, screenCode);
        versionMapper.delete(versionWrapper);
        // 自动生成的应用菜单随大屏一起清理（deleteSystemMenuByCode 内部级联角色/组织/应用授权）
        remoteUserService.deleteSystemMenuByCode(screenCode);
        return rows;
    }

    /**
     * 大屏保存后自动登记应用菜单（幂等）：menu_code = screenCode，挂在所属应用的目录菜单下，
     * 应用侧栏即可直接打开该大屏。与表单菜单同一机制——component 指向共享预览视图、
     * 编码走路径段（菜单跳转带不上 query），insertSystemMenu 内置组织与 SuperRole 授权并挂 app_menu。
     * 未归属应用的大屏不生成菜单（列表页清空应用筛选仍可见）。
     */
    private void ensureScreenMenu(DynamicDatavScreen screen) {
        if (StringUtils.isBlank(screen.getAppId())) {
            return;
        }
        SystemAppInfoDTO app = remoteUserService.findAppInfoById(screen.getAppId());
        if (app == null || StringUtils.isBlank(app.getCode())) {
            log.warn("大屏 {} 的应用不存在（{}），跳过菜单生成", screen.getScreenCode(), screen.getAppId());
            return;
        }
        if (remoteUserService.findSystemMenuByCode(screen.getScreenCode()) != null) {
            return;
        }
        String appCode = app.getCode().toLowerCase();
        SystemMenuDTO appDir = remoteUserService.findSystemMenuByCode(appCode);
        if (appDir == null) {
            // 应用创建时未生成目录菜单（历史应用），兜底创建，保证大屏菜单有父级
            appDir = createAppDirMenu(app, appCode);
        }
        SystemMenuDTO menu = new SystemMenuDTO();
        menu.setMenuName(screen.getScreenName());
        // 路由名与 menu_code 一致且唯一：screenCode 本身带 datav_ 前缀且全局唯一
        menu.setMenuCode(screen.getScreenCode());
        menu.setMenuType(MenuConstants.TYPE_MENU);
        // 预览页从路径末段读大屏编码，与 builder 表单菜单同一套路
        menu.setComponent("view.datav-preview");
        menu.setPath("/datav-preview/" + screen.getScreenCode());
        menu.setStatus(WorkflowDataStatus.FINISH.getCode().intValue());
        menu.setParentId(appDir.getId());
        menu.setCreatedBy(SecurityUtils.getUserId());
        menu.setCreatedTime(new Date());
        menu.setOrderNum(20L);
        menu.setVisible(1);
        // 侧栏里每块屏都长一个样太单调，也和大屏列表入口区分不开
        menu.setIcon("mdi:view-dashboard-outline");
        // 按屏授权码（datav:screen:view:<screenCode>）：管理员勾哪条菜单就等于授哪块屏，
        // 全用通用的 datav:screen:query 时菜单只是入口藏起来，接口其实对同组织全开
        menu.setPermission(DatavScreenAccess.viewPermission(screen.getScreenCode()));
        menu.setAppMenu(0);
        menu.setAppId(screen.getAppId());
        menu.setOrganizationId(SecurityUtils.getOrganizationId());
        remoteUserService.insertSystemMenu(menu);
    }

    /** 改名时同步菜单名：菜单服务没有单独改名接口，自动菜单无子按钮，重建等价（授权由 insert 重新落） */
    private void syncScreenMenuName(DynamicDatavScreen screen) {
        if (StringUtils.isBlank(screen.getAppId())) {
            return;
        }
        SystemMenuDTO menu = remoteUserService.findSystemMenuByCode(screen.getScreenCode());
        if (menu == null || screen.getScreenName().equals(menu.getMenuName())) {
            return;
        }
        remoteUserService.deleteSystemMenuByCode(screen.getScreenCode());
        ensureScreenMenu(screen);
    }

    /** 应用目录菜单缺失时兜底创建（与应用创建时的逻辑保持一致：menu_code = 应用编码） */
    private SystemMenuDTO createAppDirMenu(SystemAppInfoDTO app, String appCode) {
        SystemMenuDTO dirMenu = new SystemMenuDTO();
        dirMenu.setMenuName(app.getAppName());
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
        dirMenu.setAppId(app.getId());
        dirMenu.setOrganizationId(SecurityUtils.getOrganizationId());
        return remoteUserService.insertSystemMenu(dirMenu);
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDatavScreen changeStatus(String screenCode, Integer status) {
        DynamicDatavScreen screen = findByCode(screenCode);
        if (screen == null) {
            throw new ServiceException("大屏不存在：" + screenCode);
        }
        if (status == null || !ALLOWED_STATUS.contains(status)) {
            throw new ServiceException("不支持的大屏状态：" + status);
        }
        if (Integer.valueOf(1).equals(status) && screen.getWidgetCount() != null && screen.getWidgetCount() == 0) {
            throw new ServiceException("空大屏不能发布");
        }
        screen.setStatus(status);
        screen.setUpdatedBy(SecurityUtils.getUserId());
        screen.setUpdatedTime(new Date());
        LambdaPrivernalWrapper<DynamicDatavScreen> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavScreen::getId, screen.getId());
        screenMapper.update(screen, wrapper);
        if (Integer.valueOf(1).equals(status)) {
            // 发布即冻结：运行态只认发布快照，此后编辑草稿不再影响线上投放（M2.11）。
            // 快照复用版本表，用固定 remark 前缀标记；重新发布会写一条新快照。
            recordVersion(screen, PUBLISH_SNAPSHOT_REMARK);
        }
        return screen;
    }

    @Override
    @Transactional(readOnly = true)
    public String findPublishedSchemaJson(String screenCode) {
        LambdaPrivernalWrapper<DynamicDatavVersion> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavVersion::getScreenCode, screenCode);
        wrapper.eq(DynamicDatavVersion::getRemark, PUBLISH_SNAPSHOT_REMARK);
        wrapper.orderByDesc(DynamicDatavVersion::getVersionNo);
        List<DynamicDatavVersion> versions = versionMapper.selectList(wrapper);
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        // 版本实体的 schemaJson 是 JSONObject，这里按调用方（controller/取数链）要的字符串口径给
        return versions.get(0).getSchemaJson() == null ? null : versions.get(0).getSchemaJson().toJSONString();
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDatavScreen copyScreen(String screenCode) {
        DynamicDatavScreen source = findByCode(screenCode);
        if (source == null) {
            throw new ServiceException("大屏不存在：" + screenCode);
        }
        DynamicDatavScreen copy = new DynamicDatavScreen();
        copy.setScreenCode("datav_" + IdUtils.simpleUUID());
        copy.setScreenName(source.getScreenName() + " 副本");
        copy.setAppId(source.getAppId());
        copy.setLayoutMode(source.getLayoutMode());
        copy.setSchemaJson(source.getSchemaJson());
        copy.setStatus(0);
        copy.setThumbnail(source.getThumbnail());
        return saveDatavScreen(copy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DynamicDatavVersion> listVersions(String screenCode) {
        LambdaPrivernalWrapper<DynamicDatavVersion> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavVersion::getScreenCode, screenCode);
        wrapper.orderByDesc(DynamicDatavVersion::getVersionNo);
        List<DynamicDatavVersion> list = versionMapper.selectList(wrapper);
        // 版本列表同样只给元信息，看内容要按版本号取
        list.forEach(item -> item.setSchemaJson(null));
        return list;
    }

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDatavScreen rollback(String screenCode, Integer versionNo) {
        if (versionNo == null) {
            throw new ServiceException("缺少版本号");
        }
        DynamicDatavScreen screen = findByCode(screenCode);
        if (screen == null) {
            throw new ServiceException("大屏不存在：" + screenCode);
        }
        LambdaPrivernalWrapper<DynamicDatavVersion> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavVersion::getScreenCode, screenCode);
        wrapper.eq(DynamicDatavVersion::getVersionNo, versionNo);
        DynamicDatavVersion version = versionMapper.selectOne(wrapper);
        if (version == null) {
            throw new ServiceException("版本不存在：" + screenCode + " v" + versionNo);
        }
        validateSchema(version.getSchemaJson());
        screen.setSchemaJson(version.getSchemaJson());
        screen.setScreenName(StringUtils.isBlank(version.getScreenName()) ? screen.getScreenName() : version.getScreenName());
        return saveDatavScreen(screen);
    }

    static void validateSchema(JSONObject schema) {
        if (schema == null || schema.isEmpty()) {
            throw new ServiceException("画布内容不能为空");
        }
        String text = schema.toJSONString();
        if (text.length() > MAX_SCHEMA_CHARS) {
            throw new ServiceException("画布内容过大（" + (text.length() / 1024) + "KB），请拆分或清理后重试");
        }
        if (schema.getInteger("schemaVersion") == null) {
            throw new ServiceException("画布缺少 schemaVersion，无法确认兼容性");
        }
        // 这里必须按 Map 判定而不是 JSONObject：请求体由 Jackson 绑定，
        // 嵌套的 canvas 是 LinkedHashMap（只有最外层因为字段类型是 JSONObject 才是 fastjson2 对象），
        // 用 instanceof JSONObject 会把合法画布判成「缺少 canvas 配置」
        if (!(schema.get("canvas") instanceof java.util.Map)) {
            throw new ServiceException("画布缺少 canvas 配置");
        }
        int widgets = countWidgets(schema);
        if (widgets > MAX_WIDGETS) {
            throw new ServiceException("单屏组件数不能超过 " + MAX_WIDGETS + " 个，当前 " + widgets + " 个");
        }
    }

    static int countWidgets(JSONObject schema) {
        if (schema == null) {
            return 0;
        }
        JSONArray widgets = schema.getJSONArray("widgets");
        return widgets == null ? 0 : widgets.size();
    }

    /** AI 投屏支持的 reportType → 大屏图表组件（服务端只白名单放行有 datav 组件对应的四种 echarts 类型） */
    private static final java.util.Map<String, String> CHART_WIDGET_TYPES = java.util.Map.of(
            "bar", "charts.bar", "line", "charts.line", "pie", "charts.pie", "gauge", "charts.gauge");

    /** AI 投屏组件默认尺寸（与 src/packages/components/charts 各 meta 的 defaultSize 同量级） */
    private static final java.util.Map<String, int[]> CHART_WIDGET_SIZES = java.util.Map.of(
            "charts.bar", new int[] {480, 300}, "charts.line", new int[] {480, 300},
            "charts.pie", new int[] {420, 320}, "charts.gauge", new int[] {360, 300});

    /** 投屏组件与既有组件/画布边缘的间距 */
    private static final int APPEND_GAP = 24;

    @Override
    @Transactional(readOnly = false, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public DynamicDatavScreen appendWidget(String screenCode, String screenName, String datasetCode,
                                           JSONObject reportConfig, String reportType, String title) {
        DynamicDatavScreen screen = resolveAppendTarget(screenCode, screenName);
        String widgetType = CHART_WIDGET_TYPES.get(StringUtils.trimToEmpty(reportType).toLowerCase());
        if (widgetType == null) {
            throw new ServiceException("投屏只支持 bar/line/pie/gauge 四类图表，当前类型：" + reportType);
        }
        if (StringUtils.isBlank(datasetCode)) {
            throw new ServiceException("缺少 datasetCode：AI 图表须先保存为报表（拿到已落库数据集）再投屏");
        }
        if (datasetService.findByCode(datasetCode) == null) {
            throw new ServiceException("数据集不存在：" + datasetCode + "（AI 图表须先保存为报表再投屏）");
        }
        JSONObject schema = screen.getSchemaJson();
        if (schema == null || schema.isEmpty()) {
            throw new ServiceException("大屏画布为空，请先在大屏设计器保存一次再投放");
        }
        JSONArray widgets = schema.getJSONArray("widgets");
        if (widgets == null) {
            widgets = new JSONArray();
            schema.put("widgets", widgets);
        }
        widgets.add(buildAppendWidget(schema, widgets, widgetType, datasetCode, reportConfig, title));
        screen.setSchemaJson(schema);
        // 走保存链而不是手写 update：validateSchema（≤120 组件/≤2MB）与历史版本一个不漏
        return saveDatavScreen(screen);
    }

    /** AI 投屏目标定位：screenCode 精确；只给名字时按名称精确匹配（重名拒绝，防投错屏） */
    private DynamicDatavScreen resolveAppendTarget(String screenCode, String screenName) {
        if (StringUtils.isNotBlank(screenCode)) {
            DynamicDatavScreen screen = findByCode(screenCode);
            if (screen == null) {
                throw new ServiceException("大屏不存在：" + screenCode);
            }
            return screen;
        }
        if (StringUtils.isNotBlank(screenName)) {
            LambdaPrivernalWrapper<DynamicDatavScreen> wrapper = new LambdaPrivernalWrapper<>();
            wrapper.eq(DynamicDatavScreen::getScreenName, screenName.trim());
            List<DynamicDatavScreen> matched = screenMapper.selectList(wrapper);
            if (matched.isEmpty()) {
                throw new ServiceException("找不到名为「" + screenName.trim() + "」的大屏，请确认名称或改用大屏编码 screenCode");
            }
            if (matched.size() > 1) {
                throw new ServiceException("大屏名「" + screenName.trim() + "」不唯一（" + matched.size() + " 块），请改用大屏编码 screenCode");
            }
            return matched.get(0);
        }
        throw new ServiceException("screenCode 与 screenName 至少传一个");
    }

    /**
     * 构造 AI 投屏 widget：右侧顺排——新组件 x 取既有组件最右缘+间距、y 对齐最顶组件，
     * 超出画布宽度时换到画布底部之下，不自动重排既有组件。
     */
    private JSONObject buildAppendWidget(JSONObject schema, JSONArray widgets, String widgetType,
                                         String datasetCode, JSONObject reportConfig, String title) {
        int[] size = CHART_WIDGET_SIZES.get(widgetType);
        JSONObject canvas = schema.getJSONObject("canvas");
        int canvasWidth = canvas != null && canvas.getInteger("width") != null ? canvas.getInteger("width") : 1920;
        int maxRight = 0;
        int maxBottom = 0;
        int topY = Integer.MAX_VALUE;
        int maxZ = 0;
        for (int i = 0; i < widgets.size(); i++) {
            JSONObject existing = widgets.getJSONObject(i);
            JSONObject layout = existing == null ? null : existing.getJSONObject("layout");
            if (layout == null) {
                continue;
            }
            int x = layout.getIntValue("x");
            int y = layout.getIntValue("y");
            maxRight = Math.max(maxRight, x + layout.getIntValue("w"));
            maxBottom = Math.max(maxBottom, y + layout.getIntValue("h"));
            topY = Math.min(topY, y);
            maxZ = Math.max(maxZ, layout.getIntValue("z"));
        }
        int newX = maxRight + APPEND_GAP;
        int newY = topY == Integer.MAX_VALUE ? APPEND_GAP : Math.max(topY, 0);
        if (newX + size[0] > canvasWidth) {
            newX = APPEND_GAP;
            newY = maxBottom + APPEND_GAP;
        }
        JSONObject layout = new JSONObject();
        layout.put("x", newX);
        layout.put("y", newY);
        layout.put("w", size[0]);
        layout.put("h", size[1]);
        layout.put("rotate", 0);
        layout.put("z", maxZ + 1);
        layout.put("locked", false);

        JSONObject data = new JSONObject();
        data.put("mode", "dataset");
        data.put("datasetCode", datasetCode);
        data.put("reportConfig", reportConfig == null ? new JSONObject() : reportConfig);
        JSONObject mapping = new JSONObject();
        mapping.put("axis", "dim0");
        mapping.put("value", "value");
        data.put("mapping", mapping);

        JSONObject widget = new JSONObject();
        widget.put("id", "w_" + IdUtils.simpleUUID());
        widget.put("name", StringUtils.isBlank(title) ? "AI 图表" : title.trim());
        widget.put("type", widgetType);
        widget.put("layout", layout);
        widget.put("style", new JSONObject());
        widget.put("data", data);
        Object pages = schema.get("pages");
        // 多页大屏投到第一页；单页/老屏缺省即可（渲染层本来就按「缺省归第一页」处理）
        if (pages instanceof JSONArray pageArr && !pageArr.isEmpty() && pageArr.getJSONObject(0) != null) {
            widget.put("pageId", pageArr.getJSONObject(0).getString("id"));
        }
        return widget;
    }

    private void recordVersion(DynamicDatavScreen screen) {
        recordVersion(screen, null);
    }

    /** remark 为空记普通版本（每次保存），给发布快照传固定标记 */
    private void recordVersion(DynamicDatavScreen screen, String remark) {
        LambdaPrivernalWrapper<DynamicDatavVersion> wrapper = new LambdaPrivernalWrapper<>();
        wrapper.eq(DynamicDatavVersion::getScreenCode, screen.getScreenCode());
        Long used = versionMapper.selectCount(wrapper);
        DynamicDatavVersion version = new DynamicDatavVersion();
        version.setScreenCode(screen.getScreenCode());
        version.setScreenName(screen.getScreenName());
        version.setSchemaJson(screen.getSchemaJson());
        version.setVersionNo((int) (used == null ? 0L : used) + 1);
        version.setOrganizationId(screen.getOrganizationId());
        version.setCreatedBy(SecurityUtils.getUserId());
        version.setCreatedTime(new Date());
        version.setRemark(remark);
        versionMapper.insert(version);
    }
}
