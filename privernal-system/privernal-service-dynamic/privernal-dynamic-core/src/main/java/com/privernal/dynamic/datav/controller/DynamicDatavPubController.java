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

import com.privernal.common.core.exception.ServiceException;
import com.privernal.common.core.utils.StringUtils;
import com.privernal.common.datascope.context.OrganizationContextHolder;
import com.privernal.common.web.controller.BaseController;
import com.privernal.common.web.domain.CommonResult;
import com.privernal.dynamic.datav.domain.DynamicDatavScreen;
import com.privernal.dynamic.datav.domain.DynamicDatavShare;
import com.privernal.dynamic.datav.service.DynamicDatavDataService;
import com.privernal.dynamic.datav.service.IDynamicDatavScreenService;
import com.privernal.dynamic.datav.service.IDynamicDatavShareService;
import com.privernal.dynamic.dto.DynamicDatabaseDTO;
import com.privernal.dynamic.dbs.service.IDynamicDatabaseService;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 大屏外链免登接口（M7.6）
 * <p>
 * 前缀 /pub 已在网关白名单（与表单外链同一个口子）。这里没有任何 @PreAuthorize——
 * 鉴权完全由 shareService.resolveShare 的校验链承担（不透明 shareCode + HMAC 签名 +
 * 有效期 + referer 白名单 + 启用态），失败统一一句文案防探测。
 * <p>
 * 免登取数以分享所属组织执行（setOrgAndIgnoreDepartment，OpenApi 机器调用同口径）：
 * 数据范围 = 分享所属组织的全部数据，部门行级范围不生效。只应分享脱敏/公开性质的大屏。
 *
 * @author saperliu
 */
@Tag(name = "大屏外链免登接口")
@RestController
@Slf4j
@RequestMapping("/pub/datav")
public class DynamicDatavPubController extends BaseController {

    private static final String DEFAULT_DATABASE = "master";

    @Autowired
    private IDynamicDatavShareService shareService;

    @Autowired
    private IDynamicDatavScreenService screenService;

    @Autowired
    private DynamicDatavDataService datavDataService;

    @Autowired
    private IDynamicDatabaseService dynamicDatabaseService;

    @Operation(summary = "外链大屏 schema", description = "返回发布快照（不是实时草稿），已下线/未发布/链接失效统一报错")
    @GetMapping("/{shareCode}/schema")
    public CommonResult<Object> schema(@PathVariable("shareCode") String shareCode,
                                       @RequestParam("sign") String sign,
                                       @RequestParam(value = "referer", required = false) String refererHeader,
                                       @RequestHeader(value = "Referer", required = false) String referer) {
        DynamicDatavShare share = resolveAsGuest(shareCode, sign, pick(referer, refererHeader));
        // 解析出分享后按分享组织执行后续查询：screen/version 表都带 @PrivernalDataScope，
        // 免登线程没有用户信息，不挂组织上下文会被切面判 1=4 拒绝
        OrganizationContextHolder.setOrgAndIgnoreDepartment(share.getOrganizationId(), true);
        try {
            DynamicDatavScreen screen = screenService.findByCode(share.getScreenCode());
            if (screen == null || !Integer.valueOf(1).equals(screen.getStatus())) {
                throw new ServiceException("分享链接不存在或已失效");
            }
            // 发布快照：编辑草稿不影响已分发的外链画面（与登录态投放同一个口径）。
            // 快照机制上线前发布的存量屏回落实时 schema（与登录态行为一致，不炸老数据）
            String schemaJson = screenService.findPublishedSchemaJson(share.getScreenCode());
            if (StringUtils.isBlank(schemaJson) && screen.getSchemaJson() != null) {
                schemaJson = screen.getSchemaJson().toJSONString();
            }
            if (StringUtils.isBlank(schemaJson)) {
                throw new ServiceException("分享链接不存在或已失效");
            }
            JSONObject result = new JSONObject();
            result.put("screenName", screen.getScreenName());
            result.put("schemaJson", schemaJson);
            return CommonResult.ok(result);
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    @Operation(summary = "外链大屏取数", description = "整屏批量；绑定配置只认库里已保存的画布")
    @PostMapping("/{shareCode}/data")
    public CommonResult<Object> data(@PathVariable("shareCode") String shareCode,
                                     @RequestParam("sign") String sign,
                                     @RequestParam(value = "referer", required = false) String refererHeader,
                                     @RequestHeader(value = "Referer", required = false) String referer,
                                     @RequestBody(required = false) JSONObject body) {
        DynamicDatavShare share = resolveAsGuest(shareCode, sign, pick(referer, refererHeader));
        // 组织上下文必须在任何查询之前设置，finally 清理（线程复用会带着脏上下文处理别人的请求）
        OrganizationContextHolder.setOrgAndIgnoreDepartment(share.getOrganizationId(), true);
        try {
            DynamicDatavScreen screen = screenService.findByCode(share.getScreenCode());
            if (screen == null || !Integer.valueOf(1).equals(screen.getStatus())) {
                throw new ServiceException("分享链接不存在或已失效");
            }
            // 快照机制与登录态一致：发布后草稿的改动不进外链
            String schemaJson = screenService.findPublishedSchemaJson(share.getScreenCode());
            if (StringUtils.isNotBlank(schemaJson)) {
                screen.setSchemaJson(com.alibaba.fastjson2.JSON.parseObject(schemaJson));
            }
            JSONObject params = body == null ? new JSONObject() : body.getJSONObject("params");
            JSONObject drillsJson = body == null ? null : body.getJSONObject("drills");
            @SuppressWarnings("unchecked")
            Map<String, Object> drills = drillsJson == null ? null : (Map<String, Object>) (Map<String, ?>) drillsJson;

            String database = resolveDatabase(share.getOrganizationId());
            return CommonResult.ok(datavDataService.queryScreenAsGuest(database, screen,
                    params == null ? new JSONObject() : params, drills));
        } finally {
            OrganizationContextHolder.clear();
        }
    }

    /** 业务库按组织解析（登录链路同一个入口），未登记业务库的组织回落主数据源 */
    private String resolveDatabase(String organizationId) {
        DynamicDatabaseDTO config = dynamicDatabaseService.selectDynamicDatabase(organizationId);
        String database = config == null ? null : config.getDatabaseName();
        return StringUtils.isEmpty(database) ? DEFAULT_DATABASE : database;
    }

    /**
     * 免登线程没有登录用户：数据权限切面对 @PrivernalDataScope mapper 在拿不到用户信息时直接判 1=4 拒绝，
     * 分享表自身按不透明 shareCode 定位、不需要组织谓词——解析前先放开 bypass，解析完立即复位，
     * 真正的组织上下文由调用方用 share.organizationId 重新挂上。
     */
    private DynamicDatavShare resolveAsGuest(String shareCode, String sign, String referer) {
        OrganizationContextHolder.setIgnore(true);
        try {
            return shareService.resolveShare(shareCode, sign, referer);
        } finally {
            OrganizationContextHolder.setIgnore(false);
        }
    }

    /** Referer 优先取请求头；参数兜底用于 iframe 场景部分代理会剥 Referer 的环境 */
    private static String pick(String header, String param) {
        return StringUtils.isNotBlank(header) ? header : param;
    }
}
