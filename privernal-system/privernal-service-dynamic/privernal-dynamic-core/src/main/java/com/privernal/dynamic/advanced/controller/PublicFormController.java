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

package com.privernal.dynamic.advanced.controller;

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.security.service.SecurityUtils;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.advanced.domain.DynamicFormShare;
import com.privernal.dynamic.advanced.domain.DynamicShareAccessLog;
import com.privernal.dynamic.advanced.engine.LinkageIdemUtils;
import com.privernal.dynamic.advanced.service.FormShareService;
import com.privernal.dynamic.dba.vo.PageResultInfo;
import com.privernal.dynamic.dbs.domain.DynamicForm;
import com.privernal.dynamic.dbs.domain.DynamicFormColumn;
import com.privernal.dynamic.dbs.mapper.DynamicFormColumnMapper;
import com.privernal.dynamic.dbs.service.DynamicSubTableService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 免登外链端点（§5.4，§5.3 全套防护）。路径 /pub/** 在 sa-token 白名单内（有意回退
 * "提交需登录态"的既有决策，理由与补偿控制见 §5.3 与 application.yaml 注释）。
 * <p>
 * 每个端点的固定骨架：IP 限流 → token 摘要解析（注入组织上下文，§5.5）→ 业务 → finally 恢复上下文。
 * 链接有效性任一不过 → 统一"链接不存在或已失效"（防探测，§5.3-4）。
 */
@Tag(name = "表单外链免登端点")
@Slf4j
@RestController
@RequestMapping("/pub")
public class PublicFormController {

    @Autowired
    private FormShareService shareService;

    @Autowired
    private DynamicFormColumnMapper formColumnMapper;

    @Autowired
    private DynamicSubTableService dynamicSubTableService;

    @Autowired
    private com.privernal.dynamic.dbs.service.DynamicTableService dynamicTableService;

    @Autowired
    private com.privernal.dynamic.advanced.service.IoConfigService ioConfigService;

    @Operation(summary = "外链表单元数据预检（字段级裁剪，§5.3-6）")
    @GetMapping("/form/meta/{token}")
    public CommonResult<Map<String, Object>> formMeta(@PathVariable("token") String token,
                                                      HttpServletRequest request) {
        String ip = clientIp(request);
        enableAnonDataScopeBypass();
        FormShareService.ShareResolution resolution;
        try {
            resolution = gateAndResolve(token, DynamicFormShare.TYPE_FILL, request);
        } catch (ServiceException e) {
            return CommonResult.fail(NOT_FOUND_MSG);
        }
        try {
            Map<String, Object> result = new HashMap<>();
            DynamicForm form = resolution.form();
            result.put("formName", StringUtils.isEmpty(form.getTitle()) ? form.getTableName() : form.getTitle());
            result.put("fields", com.privernal.dynamic.advanced.engine.FormSchemaFields.trimFillableFields(form));
            DynamicFormShare share = resolution.share();
            result.put("passwordRequired", StringUtils.isNotEmpty(share.getPassword()));
            result.put("captchaRequired", "Y".equals(share.getCaptcha()));
            result.put("editableAfterSubmit", "Y".equals(share.getEditableAfterSubmit()));
            result.put("expireTime", share.getExpireTime());
            result.put("prefill", prefillValues(share, request));
            shareService.logAccess(share, DynamicShareAccessLog.ACTION_META,
                    DynamicShareAccessLog.RESULT_SUCCESS, null, ip, request.getHeader("User-Agent"), null);
            return CommonResult.ok(result);
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    @Operation(summary = "外链免登提交")
    @PostMapping("/form/submit/{token}")
    public CommonResult<Map<String, Object>> formSubmit(@PathVariable("token") String token,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        String ip = clientIp(request);
        String userAgent = request.getHeader("User-Agent");
        enableAnonDataScopeBypass();
        FormShareService.ShareResolution resolution;
        try {
            resolution = gateAndResolve(token, DynamicFormShare.TYPE_FILL, request);
        } catch (ServiceException e) {
            shareService.logAccess(null, DynamicShareAccessLog.ACTION_SUBMIT,
                    DynamicShareAccessLog.RESULT_DENIED, "token失效", ip, request.getHeader("User-Agent"), null);
            return CommonResult.fail(NOT_FOUND_MSG);
        }
        DynamicFormShare share = resolution.share();
        try {
            shareService.verifyAccessPassword(share, (String) body.get("__password"), ip);
            // 原子消费名额：停用/过期/超限一并 404（§5.3-4）；指纹超限不占名额
            if (!shareService.consumeSubmitQuota(share.getId())) {
                shareService.logAccess(share, DynamicShareAccessLog.ACTION_SUBMIT,
                        DynamicShareAccessLog.RESULT_DENIED, "名额不可用", ip, userAgent, null);
                return CommonResult.fail(NOT_FOUND_MSG);
            }
            String fingerprint = LinkageIdemUtils.sha256Hex(ip + "|" + userAgent).substring(0, 32);
            if (shareService.isFingerprintOverLimit(share, fingerprint)) {
                shareService.releaseSubmitQuota(share.getId());
                shareService.logAccess(share, DynamicShareAccessLog.ACTION_SUBMIT,
                        DynamicShareAccessLog.RESULT_DENIED, "指纹超限", ip, userAgent, null);
                return CommonResult.fail("您已达到填写次数上限");
            }
            Map<String, Object> data = new LinkedHashMap<>(body);
            // 保存前查重与在线录入同一口径（§5.1）；脚本/子表为 P0 不开放
            if (resolution.form().getMetaData() != null) {
                dynamicSubTableService.assertUniqueFields(resolution.database(), resolution.form().getTableCode(),
                        resolution.form().getMetaData(), data);
            }
            String dataId = shareService.submitData(share, resolution.form(), resolution.database(), data, fingerprint);
            shareService.recordFingerprint(share, fingerprint);
            Map<String, Object> result = new HashMap<>();
            result.put("dataId", dataId);
            if ("Y".equals(share.getEditableAfterSubmit())) {
                result.put("receiptCode", shareService.issueReceipt(share.getId(), dataId));
            }
            shareService.logAccess(share, DynamicShareAccessLog.ACTION_SUBMIT,
                    DynamicShareAccessLog.RESULT_SUCCESS, null, ip, userAgent, dataId);
            return CommonResult.ok(result);
        } catch (ServiceException e) {
            shareService.releaseSubmitQuota(share.getId());
            if (NOT_FOUND_MSG.equals(e.getMessage())) {
                return CommonResult.fail(NOT_FOUND_MSG);
            }
            shareService.logAccess(share, DynamicShareAccessLog.ACTION_SUBMIT,
                    DynamicShareAccessLog.RESULT_REJECTED, e.getMessage(), ip, userAgent, null);
            return CommonResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("外链提交失败 token_tail={}", share.getTokenTail(), e);
            shareService.releaseSubmitQuota(share.getId());
            return CommonResult.fail("提交失败，请稍后重试");
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    @Operation(summary = "凭回执码修改自己提交的数据（§5.3-7）")
    @PostMapping("/form/update/{token}")
    public CommonResult<Map<String, Object>> formUpdate(@PathVariable("token") String token,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        String ip = clientIp(request);
        String userAgent = request.getHeader("User-Agent");
        enableAnonDataScopeBypass();
        FormShareService.ShareResolution resolution;
        try {
            resolution = gateAndResolve(token, DynamicFormShare.TYPE_FILL, request);
        } catch (ServiceException e) {
            return CommonResult.fail(NOT_FOUND_MSG);
        }
        DynamicFormShare share = resolution.share();
        try {
            if (!"Y".equals(share.getEditableAfterSubmit())) {
                return CommonResult.fail("该表单未开启提交后修改");
            }
            String dataId = shareService.findDataIdByReceipt(share.getId(), (String) body.get("__receiptCode"));
            if (dataId == null) {
                return CommonResult.fail("回执码无效");
            }
            String fingerprint = LinkageIdemUtils.sha256Hex(ip + "|" + userAgent).substring(0, 32);
            if (shareService.isFingerprintOverLimit(share, fingerprint, "upd")) {
                shareService.logAccess(share, DynamicShareAccessLog.ACTION_UPDATE,
                        DynamicShareAccessLog.RESULT_DENIED, "指纹超限", ip, userAgent, dataId);
                return CommonResult.fail("操作过于频繁");
            }
            Map<String, Object> data = new LinkedHashMap<>(body);
            data.put("id", dataId);
            String updatedId = shareService.submitData(share, resolution.form(), resolution.database(), data, fingerprint);
            shareService.recordFingerprint(share, fingerprint, "upd");
            shareService.logAccess(share, DynamicShareAccessLog.ACTION_UPDATE,
                    DynamicShareAccessLog.RESULT_SUCCESS, null, ip, userAgent, updatedId);
            return CommonResult.ok(Map.of("dataId", updatedId));
        } catch (ServiceException e) {
            return CommonResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("外链回执修改失败 token_tail={}", share.getTokenTail(), e);
            return CommonResult.fail("修改失败，请稍后重试");
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    @Operation(summary = "外链查看-数据列表（scope 鉴权 + show_list 列裁剪）")
    @PostMapping("/view/list/{token}")
    public CommonResult<Object> viewList(@PathVariable("token") String token,
                                         @RequestBody Map<String, Object> query,
                                         HttpServletRequest request) {
        String ip = clientIp(request);
        enableAnonDataScopeBypass();
        FormShareService.ShareResolution resolution;
        try {
            resolution = gateAndResolve(token, DynamicFormShare.TYPE_VIEW, request);
            resolutionFormId = resolution.form().getId();
            checkViewScope(resolution.share());
        } catch (ServiceException e) {
            shareService.logAccess(null, DynamicShareAccessLog.ACTION_VIEW_LIST,
                    DynamicShareAccessLog.RESULT_DENIED, e.getMessage(), ip, request.getHeader("User-Agent"), null);
            if (NOT_FOUND_MSG.equals(e.getMessage())) {
                return CommonResult.fail(NOT_FOUND_MSG);
            }
            return CommonResult.fail(e.getMessage());
        }
        try {
            PageResultInfo page = dynamicTableServicePage(resolution, query);
            shareService.logAccess(resolution.share(), DynamicShareAccessLog.ACTION_VIEW_LIST,
                    DynamicShareAccessLog.RESULT_SUCCESS, null, ip, request.getHeader("User-Agent"), null);
            return CommonResult.ok(projectColumns(resolution, page));
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    @Operation(summary = "外链查看-数据详情")
    @GetMapping("/view/detail/{token}/{dataId}")
    public CommonResult<Object> viewDetail(@PathVariable("token") String token,
                                           @PathVariable("dataId") String dataId,
                                           HttpServletRequest request) {
        String ip = clientIp(request);
        enableAnonDataScopeBypass();
        FormShareService.ShareResolution resolution;
        try {
            resolution = gateAndResolve(token, DynamicFormShare.TYPE_VIEW, request);
            resolutionFormId = resolution.form().getId();
            checkViewScope(resolution.share());
        } catch (ServiceException e) {
            if (NOT_FOUND_MSG.equals(e.getMessage())) {
                return CommonResult.fail(NOT_FOUND_MSG);
            }
            return CommonResult.fail(e.getMessage());
        }
        try {
            Map<String, Object> row = new HashMap<>(java.util.Objects.requireNonNullElse(
                    queryRow(resolution, dataId), Map.of()));
            if (row.isEmpty()) {
                return CommonResult.fail("数据不存在");
            }
            shareService.logAccess(resolution.share(), DynamicShareAccessLog.ACTION_VIEW_DETAIL,
                    DynamicShareAccessLog.RESULT_SUCCESS, null, ip, request.getHeader("User-Agent"), dataId);
            return CommonResult.ok(projectRow(resolution, row));
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    private void checkViewScope(DynamicFormShare share) {
        String scope = share.getScopeType();
        if (DynamicFormShare.SCOPE_ANON_PUBLIC.equals(scope)) {
            return;
        }
        String userId = SecurityUtils.getUserId();
        if (StringUtils.isEmpty(userId)) {
            // 内部共享链接需登录（§5.2）：未登录时前端跳登录后回跳
            throw new ServiceException("该链接仅限登录用户访问");
        }
        if (DynamicFormShare.SCOPE_LOGIN_DEPT.equals(scope)) {
            String deptId = SecurityUtils.getDepartmentId();
            List<String> allowed = parseDeptIds(share.getScopeDeptIds());
            if (StringUtils.isEmpty(deptId) || !allowed.contains(deptId)) {
                throw new ServiceException("您所在部门无权访问该链接");
            }
        }
    }

    private List<String> parseDeptIds(String scopeDeptIds) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isEmpty(scopeDeptIds)) {
            return result;
        }
        try {
            JSON.parseArray(scopeDeptIds).forEach(item -> result.add(String.valueOf(item)));
        } catch (Exception e) {
            log.warn("scope_dept_ids 解析失败：{}", scopeDeptIds);
        }
        return result;
    }

    private PageResultInfo dynamicTableServicePage(FormShareService.ShareResolution resolution,
                                                   Map<String, Object> query) {
        return dynamicTableService.findByPage(resolution.database(), resolution.form().getTableCode(), query);
    }

    private Map<String, Object> queryRow(FormShareService.ShareResolution resolution, String dataId) {
        return dynamicTableService.findById(resolution.database(), resolution.form().getTableCode(), dataId);
    }

    /** show_list 列裁剪（§5.2 只读列表按列表设计渲染）；敏感列脱敏随 §4.4 落地接入 */
    @SuppressWarnings("unchecked")
    private PageResultInfo projectColumns(FormShareService.ShareResolution resolution, PageResultInfo page) {
        List<String> visibleColumns = visibleColumns(resolution);
        if (page.getList() != null) {
            List<Map<String, Object>> projected = new ArrayList<>();
            for (Object item : page.getList()) {
                if (item instanceof Map) {
                    projected.add(projectRowByColumns((Map<String, Object>) item, visibleColumns));
                }
            }
            page.setList(new ArrayList<>(projected));
        }
        return page;
    }

    private Map<String, Object> projectRow(FormShareService.ShareResolution resolution, Map<String, Object> row) {
        return projectRowByColumns(row, visibleColumns(resolution));
    }

    private Map<String, Object> projectRowByColumns(Map<String, Object> row, List<String> visibleColumns) {
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("id", row.get("id"));
        if (visibleColumns.isEmpty()) {
            // 未配置列表显示列时回落 id+title，避免整行外泄
            projected.put("title", row.get("title"));
            projected.put("created_time", row.get("created_time"));
            return projected;
        }
        // 敏感列在服务端强制脱敏（§5.2：不信任前端隐藏），策略取 io_config.sensitive_policy
        java.util.Set<String> sensitiveColumns = sensitiveColumnsOf(resolutionFormId);
        for (String column : visibleColumns) {
            Object value = row.get(column);
            if (value != null && sensitiveColumns.contains(column)) {
                if ("HIDE".equals(sensitivePolicyOf(resolutionFormId))) {
                    continue;
                }
                value = com.privernal.dynamic.advanced.engine.SensitiveDataMask.mask(String.valueOf(value));
            }
            projected.put(column, value);
        }
        return projected;
    }

    private String resolutionFormId;

    private java.util.Set<String> sensitiveColumnsOf(String formId) {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (DynamicFormColumn column : formColumnMapper.selectList(
                Wrappers.<DynamicFormColumn>lambdaQuery().eq(DynamicFormColumn::getFormId, formId))) {
            if (com.privernal.dynamic.advanced.engine.SensitiveDataMask.needMask(column.getSensitive())
                    && StringUtils.isNotEmpty(column.getFiledCode())) {
                result.add(column.getFiledCode());
            }
        }
        return result;
    }

    private String sensitivePolicyOf(String formId) {
        com.privernal.dynamic.advanced.domain.DynamicFormIoConfig config = ioConfigService.getByFormId(formId);
        return StringUtils.isEmpty(config.getSensitivePolicy()) ? "MASK" : config.getSensitivePolicy();
    }

    private List<String> visibleColumns(FormShareService.ShareResolution resolution) {
        List<String> codes = new ArrayList<>();
        List<DynamicFormColumn> columns = formColumnMapper.selectList(
                Wrappers.<DynamicFormColumn>lambdaQuery()
                        .eq(DynamicFormColumn::getFormId, resolution.form().getId()));
        for (DynamicFormColumn column : columns) {
            if (column.getShowList() != null && column.getShowList() == 1
                    && StringUtils.isNotEmpty(column.getFiledCode())) {
                codes.add(column.getFiledCode());
            }
        }
        return codes;
    }

    /**
     * 免登线程无会话：数据范围切面对注解 mapper 的用户判定取不到用户会兜底拒绝，
     * 必须在第一个 mapper 查询（token 摘要定位）之前就开"组织+数据权限"旁路
     */
    private void enableAnonDataScopeBypass() {
        OrganizationContextHolder.setIgnoreOrgAndDepartment(true);
    }

    /** 外链 kill switch（T9/D7）：关停时免登端点统一 404 语义（§5.3-4 防探测，不区分原因） */
    private boolean shareDisabled() {
        return !shareService.shareEnabled();
    }

    /** 免登端点公共前置：kill switch → 匿名数据范围旁路 → 限流 → token 解析 */
    private FormShareService.ShareResolution gateAndResolve(String token, String expectType,
                                                            HttpServletRequest request) {
        if (shareDisabled()) {
            throw new ServiceException(NOT_FOUND_MSG);
        }
        String ip = clientIp(request);
        shareService.checkIpRateLimit(ip);
        return shareService.resolveByToken(token, expectType);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotEmpty(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 免登预填：免登 URL 的 ?p_{field}= 参数仅
     * {@code dynamic_form_share.prefill_fields} 白名单内的字段被接受，其余忽略（防篡改面）。
     * 巡检点二维码即用此通道：码内容 = {public-origin}/pub/form/{token}?p_point_code=xxx。
     */
    static Map<String, Object> prefillValues(DynamicFormShare share, HttpServletRequest request) {
        Map<String, Object> prefill = new HashMap<>();
        if (share == null || StringUtils.isEmpty(share.getPrefillFields())) {
            return prefill;
        }
        java.util.List<String> whitelist;
        try {
            whitelist = com.alibaba.fastjson2.JSON.parseArray(share.getPrefillFields(), String.class);
        } catch (Exception e) {
            return prefill;
        }
        if (whitelist == null) {
            return prefill;
        }
        for (String field : whitelist) {
            if (StringUtils.isEmpty(field) || !field.matches("[a-zA-Z0-9_]{1,64}")) {
                continue;
            }
            String value = request.getParameter("p_" + field);
            if (StringUtils.isNotEmpty(value)) {
                prefill.put(field, value);
            }
        }
        return prefill;
    }

    private static final String NOT_FOUND_MSG = "链接不存在或已失效";
}
