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

package com.privernal.dynamic.advanced.service;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.dynamic.advanced.domain.DynamicDocTemplate;
import com.privernal.dynamic.advanced.engine.LinkageSupport;
import com.privernal.dynamic.advanced.mapper.DynamicDocTemplateMapper;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 单据打印模板服务（plan-sheet-print §3.2/§3.4，M1：模板 CRUD + 保存期 binding 一致性校验）。
 * <p>
 * is_default 唯一性：单条条件 update（set is_default='N' where form_id=? and is_default='Y'
 * and id<>?）与置 Y 放同一事务——旧实现「先 selectList 后逐条 updateById」两步无锁，并发保存
 * 会出现两个 Y（plan-sheet-print §3.2 v2.1 加强，M5 验收「默认模板唯一」依赖这条）。
 * 保存期一致性校验：binding_json 的明细表以 data-detail-key 定位，缺失/模板行缺失 → 拦截保存
 * （§3.4〔v2 版补充〕：渲染期 warnings 只是兜底，不是校验）。
 */
@Slf4j
@Service
public class DocTemplateService {

    /** 版式白名单（§3.5）：print_config 的值会拼进前端 <style>，只允许枚举 + 有限数值，杜绝 CSS 注入 */
    private static final Set<String> ALLOWED_PAPERS = Set.of("A4", "A5", "Letter");
    private static final Set<String> ALLOWED_ORIENTATIONS = Set.of("PORTRAIT", "LANDSCAPE");
    private static final String[] MARGIN_KEYS = {"top", "right", "bottom", "left"};
    private static final double MARGIN_MAX_MM = 50;
    private static final double SCALE_MIN = 0.3;
    private static final double SCALE_MAX = 2;

    @Autowired
    private DynamicDocTemplateMapper templateMapper;

    @Autowired
    private LinkageSupport linkageSupport;

    public List<DynamicDocTemplate> listByFormId(String formId) {
        return templateMapper.selectList(Wrappers.<DynamicDocTemplate>lambdaQuery()
                .eq(DynamicDocTemplate::getFormId, formId)
                .orderByAsc(DynamicDocTemplate::getSort));
    }

    public DynamicDocTemplate getById(String id) {
        return templateMapper.selectById(id);
    }

    /** 按稳定编码取启用模板（运行态取数入口用） */
    public DynamicDocTemplate getByTemplateCode(String templateCode) {
        if (StringUtils.isEmpty(templateCode)) {
            return null;
        }
        return templateMapper.selectOne(Wrappers.<DynamicDocTemplate>lambdaQuery()
                .eq(DynamicDocTemplate::getTemplateCode, templateCode)
                .eq(DynamicDocTemplate::getStatus, "1")
                .last("limit 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicDocTemplate insertTemplate(DynamicDocTemplate template) {
        fillDefaults(template);
        validate(template);
        validateBindingConsistency(template);
        // 一表单只一个默认模板：置 Y 前先把该表单其余 Y 单条条件 update 清掉（竞态关在一条语句里）
        if ("Y".equals(template.getIsDefault())) {
            clearDefault(template.getFormId(), null);
        }
        templateMapper.insert(template);
        return template;
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicDocTemplate updateTemplate(DynamicDocTemplate template) {
        DynamicDocTemplate existing = templateMapper.selectById(template.getId());
        if (existing == null) {
            throw new ServiceException("单据模板不存在：" + template.getId());
        }
        // 稳定编码与归属锚点不随编辑改变（应用包判重键、表单级联锚点）
        template.setTemplateCode(existing.getTemplateCode());
        template.setFormId(existing.getFormId());
        template.setOrganizationId(existing.getOrganizationId());
        template.setDepartmentId(existing.getDepartmentId());
        // 局部更新兜底：内容/绑定字段不传时回退存量值，防"仅设默认"这类轻量更新
        // 把版式覆盖成空（DOC 时代 contentHtml:'<p></p>' 的老调用习惯正是这一缺陷的来源）
        if (StringUtils.isEmpty(template.getTemplateType())) {
            template.setTemplateType(existing.getTemplateType());
        }
        if (StringUtils.isEmpty(template.getContentHtml())) {
            template.setContentHtml(existing.getContentHtml());
        }
        if (StringUtils.isEmpty(template.getContentSheet())) {
            template.setContentSheet(existing.getContentSheet());
        }
        if (StringUtils.isEmpty(template.getBindingJson())) {
            template.setBindingJson(existing.getBindingJson());
        }
        if (StringUtils.isEmpty(template.getParamsJson())) {
            template.setParamsJson(existing.getParamsJson());
        }
        if (StringUtils.isEmpty(template.getPrintConfig())) {
            template.setPrintConfig(existing.getPrintConfig());
        }
        validate(template);
        validateBindingConsistency(template);
        if ("Y".equals(template.getIsDefault())) {
            clearDefault(template.getFormId(), template.getId());
        }
        templateMapper.updateById(template);
        return template;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(String id) {
        templateMapper.deleteById(id);
    }

    private void fillDefaults(DynamicDocTemplate template) {
        if (StringUtils.isEmpty(template.getTemplateCode())) {
            template.setTemplateCode("DT" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        if (StringUtils.isEmpty(template.getTemplateType())) {
            template.setTemplateType(DynamicDocTemplate.TYPE_DOC);
        }
        if (StringUtils.isEmpty(template.getSourceType())) {
            template.setSourceType("FORM");
        }
        if (StringUtils.isEmpty(template.getStatus())) {
            template.setStatus("1");
        }
        if (StringUtils.isEmpty(template.getIsDefault())) {
            template.setIsDefault("N");
        }
        if (StringUtils.isEmpty(template.getOrganizationId()) || StringUtils.isEmpty(template.getDepartmentId())) {
            DynamicForm form = linkageSupport.loadForm(template.getFormId());
            if (form != null) {
                if (StringUtils.isEmpty(template.getOrganizationId())) {
                    template.setOrganizationId(form.getOrganizationId());
                }
                if (StringUtils.isEmpty(template.getDepartmentId())) {
                    template.setDepartmentId(form.getDepartmentId());
                }
            }
        }
    }

    private void validate(DynamicDocTemplate template) {
        if (StringUtils.isEmpty(template.getName())) {
            throw new ServiceException("模板名称不能为空");
        }
        if (StringUtils.isEmpty(template.getFormId())) {
            throw new ServiceException("模板必须关联表单（formId）");
        }
        String type = template.getTemplateType();
        if (DynamicDocTemplate.TYPE_SHEET.equals(type)) {
            validateSheetContent(template.getContentSheet());
        } else if (DynamicDocTemplate.TYPE_DOC.equals(type)) {
            if (StringUtils.isEmpty(template.getContentHtml())) {
                throw new ServiceException("模板内容（contentHtml）不能为空");
            }
            if (template.getContentHtml().length() > 1_000_000) {
                throw new ServiceException("模板内容超过 1MB 上限，请精简图片或版式");
            }
        } else {
            throw new ServiceException("模板类型只支持 DOC（文档）与 SHEET（表格），当前：" + type + "（GRID 预留未实现）");
        }
        validatePrintConfig(template.getPrintConfig());
    }

    /** SHEET 模板内容校验：合法 JSON 且含 celldata 数组（渲染端最小依赖），上限 2MB（Luckysheet JSON 较 HTML 更冗长） */
    private void validateSheetContent(String contentSheet) {
        if (StringUtils.isEmpty(contentSheet)) {
            throw new ServiceException("表格模板内容（contentSheet）不能为空");
        }
        if (contentSheet.length() > 2_000_000) {
            throw new ServiceException("表格模板内容超过 2MB 上限，请精简画布范围（删多余空行空列）");
        }
        try {
            JSONObject sheet = JSON.parseObject(contentSheet);
            if (sheet == null || sheet.getJSONArray("celldata") == null) {
                throw new ServiceException("表格模板缺少 celldata，请在表格编辑器重新保存");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("表格模板内容不是合法 JSON，请在表格编辑器重新保存");
        }
    }

    /**
     * print_config 白名单校验（§3.5 纸张与边距）。这些值由前端 buildPrintCss 原样拼进
     * {@code <style>}，不过滤即成 CSS / {@code </style>} 注入面，且随应用包（M6）跨环境传播；
     * 因此落库前只接受枚举纸张、枚举方向、有限且合理范围内的数值。
     */
    private void validatePrintConfig(String printConfigJson) {
        if (StringUtils.isEmpty(printConfigJson)) {
            return;
        }
        JSONObject config;
        try {
            config = JSON.parseObject(printConfigJson);
        } catch (Exception e) {
            throw new ServiceException("printConfig 不是合法 JSON，请重新设置打印配置");
        }
        if (config == null) {
            return;
        }
        String paper = config.getString("paper");
        if (StringUtils.isNotEmpty(paper) && !ALLOWED_PAPERS.contains(paper)) {
            throw new ServiceException("打印纸张只支持 " + ALLOWED_PAPERS + "，当前：" + paper);
        }
        String orientation = config.getString("orientation");
        if (StringUtils.isNotEmpty(orientation) && !ALLOWED_ORIENTATIONS.contains(orientation)) {
            throw new ServiceException("打印方向只支持 " + ALLOWED_ORIENTATIONS + "，当前：" + orientation);
        }
        JSONObject margins = config.getJSONObject("margins");
        if (margins != null) {
            for (String key : MARGIN_KEYS) {
                if (margins.containsKey(key)) {
                    requireFiniteInRange(margins.get(key), 0, MARGIN_MAX_MM, "页边距 " + key);
                }
            }
        }
        if (config.containsKey("scale")) {
            requireFiniteInRange(config.get("scale"), SCALE_MIN, SCALE_MAX, "缩放比例");
        }
    }

    private double requireFiniteInRange(Object value, double min, double max, String label) {
        double parsed;
        if (value instanceof Number) {
            parsed = ((Number) value).doubleValue();
        } else if (value instanceof String && StringUtils.isNotEmpty((String) value)) {
            try {
                parsed = Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException e) {
                throw new ServiceException(label + " 必须是数值（单位 mm），当前：" + value);
            }
        } else {
            throw new ServiceException(label + " 必须是数值（单位 mm）");
        }
        if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
            throw new ServiceException(label + " 取值需在 " + min + "~" + max + " 之间，当前：" + parsed);
        }
        return parsed;
    }

    /**
     * 保存期 binding 一致性校验（§3.4〔v2 版补充〕＋〔v2.1 加强〕）：用 jsoup 重扫序列化 HTML，
     * 按 detailKey 定位明细表后检查其模板行，四类失配一律拦截——明细表缺失、同一 detailKey 标在
     * 多张表、模板行缺失、模板行内含合并单元格。
     * 不用字符串 contains：它会把「A 表缺模板行但 B 表有」判成通过，也无法定位到具体明细表；
     * 前端保存链（ProseMirror doc 模型）拦住的只是走编辑器的路径，后端这层才挡得住直调 API。
     */
    private void validateBindingConsistency(DynamicDocTemplate template) {
        if (StringUtils.isEmpty(template.getBindingJson())) {
            return;
        }
        JSONObject binding;
        try {
            binding = JSON.parseObject(template.getBindingJson());
        } catch (Exception e) {
            throw new ServiceException("bindingJson 不是合法 JSON，请重新保存模板");
        }
        JSONArray details = binding.getJSONArray("details");
        if (details == null || details.isEmpty()) {
            return;
        }
        // SHEET 模板的绑定是单元格占位符的观测快照（真实绑定自描述在 content_sheet 里，
        // 缺列由渲染端软引用兜底），不走 HTML 明细表扫描——空 contentHtml 会全部误判失配
        if (DynamicDocTemplate.TYPE_SHEET.equals(template.getTemplateType())) {
            return;
        }
        Document html = Jsoup.parse(template.getContentHtml() == null ? "" : template.getContentHtml());
        Elements tables = html.select("table[data-detail-key]");
        for (int i = 0; i < details.size(); i++) {
            JSONObject detail = details.getJSONObject(i);
            String detailKey = detail.getString("detailKey");
            if (StringUtils.isEmpty(detailKey)) {
                throw new ServiceException("明细区绑定缺少 detailKey（子表字段编码），请在编辑器重新设置明细区");
            }
            Element matched = null;
            int matchedCount = 0;
            for (Element table : tables) {
                if (detailKey.equals(table.attr("data-detail-key"))) {
                    matchedCount++;
                    if (matched == null) {
                        matched = table;
                    }
                }
            }
            if (matched == null) {
                throw new ServiceException("明细区绑定失配：文档中已找不到明细表 " + detailKey
                        + "（可能被删除或标记丢失），请重新设置明细区");
            }
            if (matchedCount > 1) {
                throw new ServiceException("明细区 " + detailKey + " 被标记在 " + matchedCount
                        + " 张表上，一张子表只允许一个明细表，请删除多余的标记");
            }
            Elements templateRows = matched.select("tr[data-template-row]");
            if (templateRows.isEmpty()) {
                throw new ServiceException("明细区绑定失配：明细表 " + detailKey
                        + " 缺少模板行（data-template-row），请在编辑器选中该行后点「设为明细模板行」");
            }
            for (Element row : templateRows) {
                for (Element cell : row.select("td, th")) {
                    if (attrSpan(cell, "colspan") > 1 || attrSpan(cell, "rowspan") > 1) {
                        throw new ServiceException("明细模板行内部不允许合并单元格（colspan/rowspan），"
                                + "循环复制时版式会错乱，请先拆分该单元格");
                    }
                }
            }
        }
    }

    private int attrSpan(Element cell, String attr) {
        String raw = cell.attr(attr);
        if (StringUtils.isEmpty(raw)) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** 单条条件 update 清默认（并发安全，竞态关在一条语句里；与置 Y 同事务） */
    private void clearDefault(String formId, String exceptId) {
        templateMapper.update(null, Wrappers.<DynamicDocTemplate>lambdaUpdate()
                .eq(DynamicDocTemplate::getFormId, formId)
                .eq(DynamicDocTemplate::getIsDefault, "Y")
                .ne(exceptId != null, DynamicDocTemplate::getId, exceptId)
                .set(DynamicDocTemplate::getIsDefault, "N"));
    }
}
