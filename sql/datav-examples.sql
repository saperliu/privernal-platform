-- ----------------------------
-- 官方示例大屏：3 块屏，全部用静态样例数据 —— 不依赖任何数据准备，
-- 导入/执行本脚本后在「数据大屏」列表清空应用筛选即可看到，打开即渲染。
--
-- 为什么用静态数据：验收线是「示例大屏可一键导入即用」；绑真实数据集的示例
-- 需要目标环境先有表和数（数据准备脚本依赖业务语义），开箱即用性就破了。
-- 要升级为真实数据屏：进设计器 → 组件属性面板「数据绑定」选数据集即可，
-- 组件开发者手册（docs/datav-component-dev.md）说明了映射口径。
--
-- 幂等：按 screen_code NOT EXISTS 判重，可任意次重跑。
-- 归属：挂在库中第一个组织上（organization_id 缺省的组织拦截器会让它对所有组织不可见）；
--       SQL 插入不经过保存链路，所以不带菜单 —— 进设计器改一次保存即自动登记菜单。
-- ----------------------------

-- ----------------------------
-- 0. 执行顺序守卫（必须放在任何语句之前——放在依赖语句之后就等于没守卫，
--    第一个撞倒的会是别的表，报错看着像别的问题）
--
-- 本文件原名 upgrade_datav_examples.sql，按字母序会抢在 upgrade_datav_screen.sql 之前执行
-- （e < s），结果撞 1146「表不存在」却看不出是自己跑早了——已改名 zexamples 让字母序等于依赖序。
-- 这里再把它变成一句人话：SIGNAL 不能走 PREPARE（1295），所以放进临时存储过程里判完就删。
-- ----------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS datav_examples_guard$$
CREATE PROCEDURE datav_examples_guard()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dynamic_datav_screen') = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '缺 dynamic_datav_screen 表：请先执行 init.sql 或 upgrade_datav_screen.sql，再执行示例屏脚本';
    END IF;
END$$
DELIMITER ;
CALL datav_examples_guard();
DROP PROCEDURE datav_examples_guard;

SET @demo_org := (SELECT id FROM `system_organization` WHERE deleted_flag = 'N' ORDER BY id LIMIT 1);
SET @demo_dept := (SELECT id FROM `system_department` WHERE deleted_flag = 'N' ORDER BY id LIMIT 1);

-- ----------------------------
-- 1/3 运营监控（标题 + 指标矩阵 + 柱/饼 + 排行榜 + 明细翻牌表 + 时钟）
-- ----------------------------
INSERT INTO `dynamic_datav_screen`
  (`id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `department_id`, `organization_id`,
   `screen_code`, `screen_name`, `app_id`, `layout_mode`, `schema_json`, `status`)
SELECT REPLACE(UUID(), '-', ''), 'system-default', 1, 'admin', NOW(), 'N', @demo_dept, @demo_org,
  'datav_demo_operations', '示例·运营监控大屏', NULL, 'absolute', '{
  "schemaVersion": 1,
  "canvas": { "width": 1920, "height": 1080, "fit": "scale", "theme": "dark", "palette": "tech-blue",
    "background": { "type": "color", "value": "datav-canvas-bg" },
    "grid": { "show": false, "size": 10, "snap": true, "alignGuide": true } },
  "params": [],
  "widgets": [
    { "id": "demo_op_title", "name": "标题", "type": "decorates.underline",
      "layout": { "x": 640, "y": 24, "w": 640, "h": 56, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "text": "运营监控中心", "fontSize": 30, "colorToken": "datav-text-primary", "lineToken": "datav-accent", "align": "center" } },
    { "id": "demo_op_clock", "name": "时钟", "type": "informations.clock",
      "layout": { "x": 1700, "y": 24, "w": 180, "h": 56, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "fontSize": 22, "showSeconds": true, "showDate": false } },
    { "id": "demo_op_matrix", "name": "核心指标", "type": "tables.metric-matrix",
      "layout": { "x": 40, "y": 110, "w": 620, "h": 220, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "cols": 3, "valueSize": 30, "labelSize": 13, "decimals": 0, "thousands": true },
      "data": { "mode": "static", "staticRows": [
        { "label": "今日工单", "value": 1328 }, { "label": "已完结", "value": 1206 }, { "label": "进行中", "value": 122 },
        { "label": "超时率(%)", "value": 1.8 }, { "label": "满意度", "value": 96.4 }, { "label": "新增用户", "value": 87 } ] } },
    { "id": "demo_op_bar", "name": "近7日工单趋势", "type": "charts.bar",
      "layout": { "x": 40, "y": 360, "w": 620, "h": 330, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "stack": false, "barWidth": 0, "showLabel": false, "showLegend": false, "maxCategoryChars": 8 },
      "data": { "mode": "static", "mapping": { "axis": "dim0", "value": "value" }, "staticRows": [
        { "dim0": "周一", "value": 118 }, { "dim0": "周二", "value": 143 }, { "dim0": "周三", "value": 96 },
        { "dim0": "周四", "value": 175 }, { "dim0": "周五", "value": 162 }, { "dim0": "周六", "value": 88 }, { "dim0": "周日", "value": 74 } ] } },
    { "id": "demo_op_pie", "name": "工单类型分布", "type": "charts.pie",
      "layout": { "x": 700, "y": 360, "w": 480, "h": 330, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "innerRadius": 55, "showLabel": false, "rose": false },
      "data": { "mode": "static", "mapping": { "axis": "dim0", "value": "value" }, "staticRows": [
        { "dim0": "咨询", "value": 420 }, { "dim0": "报修", "value": 310 }, { "dim0": "投诉", "value": 96 },
        { "dim0": "建议", "value": 148 }, { "dim0": "其他", "value": 64 } ] } },
    { "id": "demo_op_rank", "name": "区域工单排行", "type": "tables.ranking",
      "layout": { "x": 1220, "y": 360, "w": 660, "h": 330, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "fontSize": 14, "topN": 3, "scrollSec": 8, "barShow": true },
      "data": { "mode": "static", "mapping": { "axis": "dim0", "value": "value" }, "staticRows": [
        { "dim0": "华东大区", "value": 386 }, { "dim0": "华南大区", "value": 302 }, { "dim0": "华北大区", "value": 265 },
        { "dim0": "西南大区", "value": 178 }, { "dim0": "东北大区", "value": 122 }, { "dim0": "西北大区", "value": 75 } ] } },
    { "id": "demo_op_flow_h", "name": "分割流光", "type": "decorates.flow-line-h",
      "layout": { "x": 40, "y": 330, "w": 1840, "h": 6, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "colorToken": "datav-accent", "thickness": 2, "speedSec": 4 } },
    { "id": "demo_op_table", "name": "最新工单明细", "type": "tables.flip-table",
      "layout": { "x": 40, "y": 720, "w": 1840, "h": 320, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "pageSize": 6, "intervalSec": 6, "fontSize": 14, "striped": true },
      "data": { "mode": "static", "mapping": { "axis": "dim0", "value": "value" }, "staticRows": [
        { "dim0": "GD-20261018-001", "dim1": "已支付", "value": 328 }, { "dim0": "GD-20261018-002", "dim1": "处理中", "value": 156 },
        { "dim0": "GD-20261018-003", "dim1": "已完结", "value": 512 }, { "dim0": "GD-20261018-004", "dim1": "待分派", "value": 208 },
        { "dim0": "GD-20261018-005", "dim1": "已完结", "value": 96 }, { "dim0": "GD-20261018-006", "dim1": "处理中", "value": 440 },
        { "dim0": "GD-20261018-007", "dim1": "已支付", "value": 377 }, { "dim0": "GD-20261018-008", "dim1": "已完结", "value": 265 },
        { "dim0": "GD-20261018-009", "dim1": "待分派", "value": 130 }, { "dim0": "GD-20261018-010", "dim1": "处理中", "value": 298 } ] } },
    { "id": "demo_op_border", "name": "四角边框", "type": "decorates.border-corner",
      "layout": { "x": 0, "y": 0, "w": 1920, "h": 1080, "rotate": 0, "z": 0, "locked": false, "hidden": false },
      "style": { "cornerSize": 26, "lineWidth": 2, "colorToken": "datav-accent" } }
  ],
  "groups": []
}', 1
WHERE NOT EXISTS (SELECT 1 FROM `dynamic_datav_screen` WHERE `screen_code` = 'datav_demo_operations' AND deleted_flag = 'N');

-- ----------------------------
-- 2/3 趋势分析（阶梯线 + 面积折线 + 双轴 + 散点 + 翻牌）
-- ----------------------------
INSERT INTO `dynamic_datav_screen`
  (`id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `department_id`, `organization_id`,
   `screen_code`, `screen_name`, `app_id`, `layout_mode`, `schema_json`, `status`)
SELECT REPLACE(UUID(), '-', ''), 'system-default', 1, 'admin', NOW(), 'N', @demo_dept, @demo_org,
  'datav_demo_trend', '示例·趋势分析大屏', NULL, 'absolute', '{
  "schemaVersion": 1,
  "canvas": { "width": 1920, "height": 1080, "fit": "scale", "theme": "dark", "palette": "aurora-purple",
    "background": { "type": "color", "value": "datav-canvas-bg" },
    "grid": { "show": false, "size": 10, "snap": true, "alignGuide": true } },
  "params": [],
  "widgets": [
    { "id": "demo_tr_title", "name": "标题", "type": "decorates.underline",
      "layout": { "x": 660, "y": 24, "w": 600, "h": 56, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "text": "趋势分析中心", "fontSize": 30, "colorToken": "datav-text-primary", "lineToken": "datav-accent", "align": "center" } },
    { "id": "demo_tr_flip", "name": "总量翻牌", "type": "informations.flip-counter",
      "layout": { "x": 40, "y": 24, "w": 420, "h": 90, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "decimals": 0, "thousands": true, "prefix": "累计访问 ", "suffix": " 次", "digitStyle": "card" },
      "data": { "mode": "static", "mapping": { "value": "value" }, "staticRows": [ { "value": 1286548 } ] } },
    { "id": "demo_tr_step", "name": "整点并发阶梯", "type": "charts.step-line",
      "layout": { "x": 40, "y": 140, "w": 900, "h": 420, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "stepMode": "middle", "showLabel": false, "showLegend": false, "maxCategoryChars": 8 },
      "data": { "mode": "static", "mapping": { "axis": "dim0", "value": "value" }, "staticRows": [
        { "dim0": "00:00", "value": 120 }, { "dim0": "04:00", "value": 120 }, { "dim0": "08:00", "value": 342 },
        { "dim0": "12:00", "value": 342 }, { "dim0": "16:00", "value": 210 }, { "dim0": "20:00", "value": 288 } ] } },
    { "id": "demo_tr_line", "name": "周活跃趋势", "type": "charts.line",
      "layout": { "x": 980, "y": 140, "w": 900, "h": 420, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "smooth": true, "area": true, "showLabel": false, "showLegend": true, "maxCategoryChars": 8 },
      "data": { "mode": "static", "mapping": { "axis": "dim0", "value": "value" }, "staticRows": [
        { "dim0": "第一周", "value": 5200 }, { "dim0": "第二周", "value": 6800 }, { "dim0": "第三周", "value": 6100 },
        { "dim0": "第四周", "value": 8900 }, { "dim0": "第五周", "value": 10200 }, { "dim0": "第六周", "value": 9600 } ] } },
    { "id": "demo_tr_dual", "name": "量价双轴", "type": "charts.dual-axis",
      "layout": { "x": 40, "y": 600, "w": 900, "h": 440, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "leftType": "bar", "smooth": true, "barWidth": 0, "showLabel": false, "showLegend": true, "maxCategoryChars": 8 },
      "data": { "mode": "static", "mapping": { "axis": "dim0", "value": "value", "value2": "m1" }, "staticRows": [
        { "dim0": "1月", "value": 320, "m1": 78 }, { "dim0": "2月", "value": 410, "m1": 82 },
        { "dim0": "3月", "value": 380, "m1": 80 }, { "dim0": "4月", "value": 520, "m1": 88 },
        { "dim0": "5月", "value": 610, "m1": 91 }, { "dim0": "6月", "value": 580, "m1": 86 } ] } },
    { "id": "demo_tr_scatter", "name": "渠道-转化散点", "type": "charts.scatter",
      "layout": { "x": 980, "y": 600, "w": 900, "h": 440, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "symbolSize": 12, "sizeMin": 6, "sizeMax": 42, "xName": "曝光(千)", "yName": "转化率(%)", "sizeName": "成交" },
      "data": { "mode": "static", "mapping": { "x": "value", "y": "m1", "size": "m2" }, "staticRows": [
        { "dim0": "搜索", "value": 210, "m1": 4.2, "m2": 88 }, { "dim0": "信息流", "value": 360, "m1": 2.8, "m2": 101 },
        { "dim0": "直访", "value": 120, "m1": 6.1, "m2": 73 }, { "dim0": "外链", "value": 480, "m1": 1.9, "m2": 91 },
        { "dim0": "直播", "value": 620, "m1": 3.4, "m2": 210 }, { "dim0": "私域", "value": 90, "m1": 8.8, "m2": 79 } ] } }
  ],
  "groups": []
}', 1
WHERE NOT EXISTS (SELECT 1 FROM `dynamic_datav_screen` WHERE `screen_code` = 'datav_demo_trend' AND deleted_flag = 'N');

-- ----------------------------
-- 3/3 部门展示（图标列表 + 水位 + 仪表盘 + 倒计时 + 遮罩 + 发光边框）
-- ----------------------------
INSERT INTO `dynamic_datav_screen`
  (`id`, `tenant_id`, `revision`, `created_by`, `created_time`, `deleted_flag`, `department_id`, `organization_id`,
   `screen_code`, `screen_name`, `app_id`, `layout_mode`, `schema_json`, `status`)
SELECT REPLACE(UUID(), '-', ''), 'system-default', 1, 'admin', NOW(), 'N', @demo_dept, @demo_org,
  'datav_demo_showcase', '示例·部门展示大屏', NULL, 'absolute', '{
  "schemaVersion": 1,
  "canvas": { "width": 1920, "height": 1080, "fit": "scale", "theme": "dark", "palette": "neon-cyan",
    "background": { "type": "color", "value": "datav-canvas-bg" },
    "grid": { "show": false, "size": 10, "snap": true, "alignGuide": true } },
  "params": [],
  "widgets": [
    { "id": "demo_sc_title", "name": "标题", "type": "decorates.panel-title",
      "layout": { "x": 660, "y": 24, "w": 600, "h": 60, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "text": "部门数据一览", "subText": "", "fontSize": 26, "align": "center", "accent": "datav-accent", "textColor": "datav-text-primary", "showBar": true, "showDivider": true } },
    { "id": "demo_sc_icons", "name": "服务指标", "type": "icons.icon-list",
      "layout": { "x": 40, "y": 120, "w": 340, "h": 300, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "direction": "vertical", "iconSize": 26, "fontSize": 14, "colorToken": "datav-accent", "showValue": true },
      "data": { "mode": "static", "staticRows": [
        { "icon": "mdi:account-group-outline", "label": "在职员工", "value": "486" },
        { "icon": "mdi:domain", "label": "服务企业", "value": "1,209" },
        { "icon": "mdi:file-check-outline", "label": "本季交付", "value": "312" },
        { "icon": "mdi:handshake-outline", "label": "在建合作", "value": "27" } ] } },
    { "id": "demo_sc_water", "name": "存储水位", "type": "charts.water-level",
      "layout": { "x": 420, "y": 120, "w": 300, "h": 300, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "max": 100, "decimals": 0, "showPercent": true, "colorToken": "datav-series-1" },
      "data": { "mode": "static", "mapping": { "value": "value" }, "staticRows": [ { "value": 68 } ] } },
    { "id": "demo_sc_gauge", "name": "满意度仪表", "type": "charts.gauge",
      "layout": { "x": 760, "y": 120, "w": 340, "h": 300, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "max": 100, "unit": "%", "showTicks": true },
      "data": { "mode": "static", "mapping": { "value": "value" }, "staticRows": [ { "value": 96.4 } ] } },
    { "id": "demo_sc_countdown", "name": "季末倒计时", "type": "informations.countdown",
      "layout": { "x": 1140, "y": 120, "w": 740, "h": 300, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "target": "2026-12-31 23:59:59", "label": "距季度收官", "showDays": true, "showSeconds": true } },
    { "id": "demo_sc_glow", "name": "发光边框", "type": "decorates.border-glow",
      "layout": { "x": 20, "y": 100, "w": 1880, "h": 340, "rotate": 0, "z": 0, "locked": false, "hidden": false },
      "style": { "colorToken": "datav-accent", "radius": 12, "glowSpread": 8, "fillPanel": true } },
    { "id": "demo_sc_flow_v", "name": "装饰竖线", "type": "decorates.flow-line-v",
      "layout": { "x": 390, "y": 120, "w": 6, "h": 300, "rotate": 0, "z": 1, "locked": false, "hidden": false },
      "style": { "colorToken": "datav-accent", "thickness": 2, "speedSec": 4 } },
    { "id": "demo_sc_mask", "name": "四角遮罩", "type": "decorates.mask",
      "layout": { "x": 0, "y": 0, "w": 1920, "h": 1080, "rotate": 0, "z": 9, "locked": false, "hidden": false },
      "style": { "strength": 42, "spread": 62 } }
  ],
  "groups": []
}', 1
WHERE NOT EXISTS (SELECT 1 FROM `dynamic_datav_screen` WHERE `screen_code` = 'datav_demo_showcase' AND deleted_flag = 'N');

-- ----------------------------
-- 结果核对
-- ----------------------------
SELECT '示例大屏' AS `项`, COUNT(*) AS `行数` FROM `dynamic_datav_screen`
WHERE `screen_code` IN ('datav_demo_operations', 'datav_demo_trend', 'datav_demo_showcase')
  AND deleted_flag = 'N';

-- 期望：示例大屏 3。

-- ----------------------------
-- 回滚（按需手动执行）
-- ----------------------------
-- DELETE FROM dynamic_datav_screen WHERE screen_code IN
--   ('datav_demo_operations', 'datav_demo_trend', 'datav_demo_showcase');
-- DELETE FROM dynamic_datav_version WHERE screen_code IN
--   ('datav_demo_operations', 'datav_demo_trend', 'datav_demo_showcase');
